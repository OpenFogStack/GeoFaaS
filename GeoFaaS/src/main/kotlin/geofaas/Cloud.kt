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

// Cloud Subs to Nack and Call with fence.world()
class Cloud(loc: Location, debug: Boolean, host: String = "localhost", port: Int = 5559, id: String = "GeoFaaS-Cloud", brokerAreaManager: BrokerAreaManager) {
    private val logger = LogManager.getLogger()
    private val gbClient = ServerGBClient(loc, debug, host, port, id, ClientType.CLOUD, brokerAreaManager)
    private var faasRegistry = mutableListOf<TinyFaasClient>()

    suspend fun registerFaaS(tf: TinyFaasClient): StatusCode {
        val funcs: Set<GeoFaaSFunction> = tf.remoteFunctions()!! //.functions()
        if (funcs.isNotEmpty()) {
            val registerSuccess = gbClient.registerFunctions(funcs, gbClient.brokerAreaManager.ownBrokerArea.coveredArea) // == Geofence.world()
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

    suspend fun handleNextRequest() {
        val newMsg :FunctionMessage? = gbClient.listenForFunction("CALL or NACK", 0) // blocking
        if (newMsg != null) {
            val clientFence = newMsg.responseTopicFence.fence.toGeofence() // JSON to Geofence
            if (newMsg.funcAction == FunctionAction.NACK) {
//                gbClient.sendAck(newMsg.funcName, clientFence) // tell the client you received its request
                val registeredFunctionsName: List<String> = faasRegistry.flatMap { tf -> tf.functions().map { func -> func.name } }.distinct()
                if (newMsg.funcName in registeredFunctionsName){ // I will not check if the request is for a subscribed topic (function), because otherwise geobroker won't deliver it
                    val selectedFaaS: TinyFaasClient = bestAvailFaaS(newMsg.funcName, null)
                    val response = selectedFaaS.call(newMsg.funcName, newMsg.data)
                    logger.debug("FaaS's raw Response: {}", response) // HttpResponse[http://localhost:8000/sieve, 200 OK]

                    if (response != null) {
                        val responseBody: String = response.body<String>().trimEnd() //NOTE: tinyFaaS's response always has a trailing '\n'
                        gbClient.sendResult(newMsg.funcName, responseBody, clientFence, newMsg.responseTopicFence.senderId)
                        logger.info("${gbClient.id}: sent the result '{}' to functions/${newMsg.funcName}/result", responseBody) // wiki: Found 1229 primes under 10000
                    } else { // connection refused?
                        logger.error("No response from the FaaS with '${selectedFaaS.host}:${selectedFaaS.port}' address when calling '${newMsg.funcName}'")
//                        gbClient.sendNack(newMsg.funcName, newMsg.data, clientFence)
                    }
                } else {
                    logger.fatal("No FaaS is serving the '${newMsg.funcName}' function!")
//                    gbClient.sendNack(newMsg.funcName, newMsg.data, clientFence)
                }
            } else if(newMsg.funcAction == FunctionAction.CALL) { // behave same as Edge
                gbClient.sendAck(newMsg.funcName, clientFence, newMsg.responseTopicFence.senderId) // tell the client you received its request
                val registeredFunctionsName: List<String> = faasRegistry.flatMap { tf -> tf.functions().map { func -> func.name } }.distinct()
                if (newMsg.funcName in registeredFunctionsName){ // I will not check if the request is for a subscribed topic (function), because otherwise geobroker won't deliver it
                    val selectedFaaS: TinyFaasClient = bestAvailFaaS(newMsg.funcName, null)
                    val response = selectedFaaS.call(newMsg.funcName, newMsg.data)
                    logger.debug("FaaS's raw Response: {}", response) // HttpResponse[http://localhost:8000/sieve, 200 OK]

                    if (response != null) {
                        val responseBody: String = response.body<String>().trimEnd() //NOTE: tinyFaaS's response always has a trailing '\n'
                        gbClient.sendResult(newMsg.funcName, responseBody, clientFence, newMsg.responseTopicFence.senderId)
                        logger.info("${gbClient.id}: sent the result '{}' to functions/${newMsg.funcName}/result", responseBody) // wiki: Found 1229 primes under 10000
                    } else { // connection refused?
                        logger.error("No response from the FaaS with '${selectedFaaS.host}:${selectedFaaS.port}' address when calling '${newMsg.funcName}'")
                        logger.error("The Client will NOT receive any response! This is end of the line of offloading")
//                        gbClient.sendNack(newMsg.funcName, newMsg.data, clientFence)
                    }
                } else {
                    logger.fatal("No FaaS is serving the '${newMsg.funcName}' function!")
                    logger.error("The Client will NOT receive any response! This is end of the line of offloading")
//                    gbClient.sendNack(newMsg.funcName, newMsg.data, clientFence)
                }
            } else {
                logger.error("The new request is not a NACK nor a CALL, but a ${newMsg.funcAction}!")
            }
        }
    }

    fun terminate() { //NOTE: Same as Edge terminate()
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

suspend fun main(args: Array<String>) {
    val GFServerMode = "Cloud"
    println(GFServerMode)
    val disgbRegistry = BrokerAreaManager(GFServerMode)
    when (args[2]) { // initialize
        "production" -> disgbRegistry.readFromFile("geobroker/config/disgb-registry.json")
        "intellij"   -> disgbRegistry.readFromFile("GeoBroker-Server/src/main/resources/jfsb/disgb_jfsb.json")
        "localjar"   -> disgbRegistry.readFromFile("../../GeoBroker-Server/src/main/resources/jfsb/disgb_jfsb.json")
        else -> throw RuntimeException("Wrong running mode. please specify any of 'production', 'localjar', or 'intellij'")
    }
    val brokerInfo = disgbRegistry.ownBrokerInfo
    val brokerArea: Geofence = disgbRegistry.ownBrokerArea.coveredArea // broker area: radius: 2.1
    println(brokerArea.center)
    val gf = Cloud(brokerArea.center, true, brokerInfo.ip, brokerInfo.port, "GeoFaaS-Cloud1", brokerAreaManager =  disgbRegistry)
    val tf = TinyFaasClient("localhost", 8000)

    val registerSuccess = gf.registerFaaS(tf)
    if (registerSuccess == StatusCode.Success) {
        repeat(args[1].toInt()){
            gf.handleNextRequest() //TODO: call in a coroutine? or a separate thread
            println("${it+1} requests processed")
        }
    }
    gf.terminate()
}