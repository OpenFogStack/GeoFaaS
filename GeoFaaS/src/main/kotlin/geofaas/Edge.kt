package geofaas

import de.hasenburg.geobroker.commons.model.spatial.Geofence
import de.hasenburg.geobroker.commons.model.spatial.Location
import de.hasenburg.geobroker.commons.model.spatial.toGeofence
import io.ktor.client.call.*
import org.apache.logging.log4j.LogManager
import geofaas.Model.FunctionAction
import geofaas.Model.GeoFaaSFunction
import geofaas.Model.ClientType
import geofaas.Model.FunctionMessage
import geofaas.Model.StatusCode
import kotlin.system.measureTimeMillis

class Edge(loc: Location, debug: Boolean, host: String = "localhost", port: Int = 5559, id: String = "GeoFaaS-Edge1", brokerAreaManager: BrokerAreaManager) {
    private val logger = LogManager.getLogger()
    private val gbClient = ServerGBClient(loc, debug, host, port, id, ClientType.EDGE, brokerAreaManager)
    private var faasRegistry = mutableListOf<TinyFaasClient>()
    // returns ture if success to Subscribe to all the functions

    suspend fun registerFaaS(tf: TinyFaasClient): StatusCode {
        val funcs: Set<GeoFaaSFunction> = tf.remoteFunctions()!! //.functions()
        if (funcs.isNotEmpty()) {
            val registerSuccess = gbClient.registerFunctions(funcs, gbClient.brokerAreaManager.ownBrokerArea.coveredArea)
            return if (registerSuccess == StatusCode.Success) {
                logger.info("new FaaS's functions have been registered")
                faasRegistry += tf
                logger.info("registered a new FaaS with funcs: ${funcs.map { f -> f.name }}")
                StatusCode.Success
            } else {
                logger.fatal("Failed to register FaaS '${tf.host}:${tf.port}'. Reason: Failed to register its functions")
                StatusCode.Failure
            }
        } else {
            logger.warn("registered a new FaaS with no serving functions!")
            faasRegistry += tf
            return StatusCode.Success
        }
    }

    // returns run time
    suspend fun handleNextRequest(): Long {
        val newMsg :FunctionMessage? = gbClient.listenForFunction("CALL", 0) // blocking
        return measureTimeMillis {
            if (newMsg != null) {
                if (newMsg.typeCode == Model.TypeCode.RETRY) logger.warn("received a retry call from ${newMsg.responseTopicFence.senderId}")
                val clientFence = newMsg.responseTopicFence.fence.toGeofence() // JSON to Geofence
                if (newMsg.funcAction == FunctionAction.CALL) {
                    gbClient.sendAck(newMsg.funcName, clientFence, newMsg.responseTopicFence.senderId) // tell the client you received its request
                    val registeredFunctions: List<String> = faasRegistry.flatMap { tf -> tf.functions().map { func -> func.name } }.distinct()
                    if (newMsg.funcName in registeredFunctions){ // I will not check if the request is for a subscribed topic (function), because otherwise geobroker won't deliver it
                        val selectedFaaS: TinyFaasClient = bestAvailFaaS(newMsg.funcName, null)
                        val response = selectedFaaS.call(newMsg.funcName, newMsg.data)
                        logger.debug("FaaS's raw Response: {}", response) // HttpResponse[http://localhost:8000/sieve, 200 OK]

    //                    logger.warn("Offloading all requests! for test purposes!")
                        if (response != null) {
                            val responseBody: String = response.body<String>().trimEnd() //NOTE: tinyFaaS's response always has a trailing '\n'
    //                        GlobalScope.launch {
                            gbClient.sendResult(newMsg.funcName, responseBody, clientFence, newMsg.responseTopicFence.senderId)
                            logger.info("Sent the result '{}' to functions/${newMsg.funcName}/result for {}", responseBody, newMsg.responseTopicFence.senderId) // wiki: Found 1229 primes under 10000
    //                        }
                        } else { // connection refused?
    //                        val selectedFaaS2: TinyFaasClient = bestAvailFaaS(newMsg.funcName, listOf(selectedFaaS)) // 2nd best faas
    //                        val response2 = selectedFaaS.call(newMsg.funcName, newMsg.data)
    //                        logger.debug("FaaS's raw Response: {}", response2)
                            //TODO call another FaaS and if there is no more FaaS serving the func, offload
                            logger.error("No response from the FaaS with '${selectedFaaS.host}:${selectedFaaS.port}' address when calling '${newMsg.funcName}' Offloading to cloud...")
    //                        GlobalScope.launch {
                            gbClient.sendNack(newMsg.funcName, newMsg.data, clientFence, newMsg.responseTopicFence.senderId, "GeoFaaS-Cloud")
    //                        }
                        }
                    } else {
                        logger.fatal("No FaaS is serving the '${newMsg.funcName}' function! Offloading to cloud...")
    //                    GlobalScope.launch {
                        gbClient.sendNack(newMsg.funcName, newMsg.data, clientFence, newMsg.responseTopicFence.senderId, "GeoFaaS-Cloud")
    //                    }
                    }
                } else {
                    logger.error("The new request is not a CALL, but a ${newMsg.funcAction}!")
                }
            }
        }
    }
    fun collectNewMessages() { // blocking
        gbClient.asyncListen()
    }
    fun terminate() {
        gbClient.terminate()
        faasRegistry.clear()
    }
    private fun bestAvailFaaS(funcName: String, except: List<TinyFaasClient>?): TinyFaasClient {
        val availableServers: List<TinyFaasClient> = faasRegistry.filter { tf -> tf.isServingFunction(funcName) }
        if (except == null)
            return availableServers.first() // TODO: choose between FaaS servers
        else {
            return availableServers.filter { tf -> !except.contains(tf) }.first()
        }
    }
}

suspend fun main(args: Array<String>) { // supply the broker id (same as disgb-registry.json), epochs, and running mode
    println(args[0])
    val disgbRegistry = BrokerAreaManager(args[0]) // broker id
    when (args[2]) { // initialize
        "production" -> disgbRegistry.readFromFile("geobroker/config/disgb-registry.json")
        "intellij"   -> disgbRegistry.readFromFile("GeoBroker-Server/src/main/resources/jfsb/disgb_jfsb.json")
        "localjar"   -> disgbRegistry.readFromFile("../../GeoBroker-Server/src/main/resources/jfsb/disgb_jfsb.json")
        else -> throw RuntimeException("Wrong running mode. please specify any of 'production', 'localjar', or 'intellij'")
    }
    val brokerInfo = disgbRegistry.ownBrokerInfo
    val brokerArea: Geofence = disgbRegistry.ownBrokerArea.coveredArea // broker area: radius: 2.1
    println(brokerArea.center)
    val gf = Edge(brokerArea.center, true, brokerInfo.ip, brokerInfo.port, "GeoFaaS-${brokerInfo.brokerId}", brokerAreaManager =  disgbRegistry)
    val tf = TinyFaasClient("localhost", 8000)

    val registerSuccess = gf.registerFaaS(tf)
    val listeningThread = Thread { do { gf.collectNewMessages() } while (true) }
    if (registerSuccess == StatusCode.Success) {
        listeningThread.start()
        repeat(args[1].toInt()){
            val epocTime = gf.handleNextRequest() //TODO: call in a coroutine? or a separate thread
            println("${it+1} requests processed. last took ${epocTime}ms")
        }
        listeningThread.interrupt()
    }
    gf.terminate()
}