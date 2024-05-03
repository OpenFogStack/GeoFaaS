package geofaas

import de.hasenburg.geobroker.commons.model.spatial.Geofence
import de.hasenburg.geobroker.commons.model.spatial.Location
import de.hasenburg.geobroker.commons.model.spatial.toGeofence
import de.hasenburg.geobroker.commons.sleepNoLog
import org.apache.logging.log4j.LogManager
import geofaas.experiment.Measurement.logRuntime
import geofaas.Model.FunctionAction
import geofaas.Model.GeoFaaSFunction
import geofaas.Model.ClientType
import geofaas.Model.FunctionMessage
import geofaas.Model.StatusCode
import geofaas.Model.TypeCode
import geofaas.experiment.Measurement
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import sun.misc.Signal
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

private const val logToCsv = false

class Server(loc: Location, debug: Boolean, host: String = "localhost", port: Int = 5559, id: String = "GeoFaaS-Edge1", brokerAreaManager: BrokerAreaManager) {
    private val logger = LogManager.getLogger()
    private val mode = if(id == "GeoFaaS-Cloud") ClientType.CLOUD else ClientType.EDGE
    private val gbClient = ServerGBClient(loc, debug, host, port, id, mode, brokerAreaManager)
    private var faasRegistry = mutableListOf<TinyFaasClient>()
    private val listeningThread = Thread ({ collectNewMessages() }, "ingestThread" )
    private var state :Boolean = false
    private var receivedCalls = AtomicInteger(); private var receivedNacks = AtomicInteger(); private var receivedRetries = AtomicInteger(); private var inProgressRequests = AtomicInteger()

    // returns success if success to Subscribe to all the functions
    fun registerFaaS(tf: TinyFaasClient): StatusCode {
        val funcs: Set<GeoFaaSFunction>
        val funcListTime = measureTimeMillis { funcs = tf.remoteFunctions()!! }
        Measurement.log(gbClient.id, funcListTime, "FaaS;getFuncs", funcs.joinToString(separator = ";") { it.name }, null, logToCsv)
        if (funcs.isNotEmpty()) {
            val registerSuccess = logRuntime(gbClient.id, "Subscribe Functions", funcs.joinToString(separator = ";") { it.name }, null, logToCsv){
                gbClient.registerFunctions(funcs, gbClient.brokerAreaManager.ownBrokerArea.coveredArea)
            }
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

    suspend fun run(serverName: String, epocs: Int){
        state = true
        listeningThread.start()
        logger.info("Receiving incoming messages from geoBroker")
        val listeningMsg = if (mode == ClientType.CLOUD) "CALL(/retry) or NACK" else "CALL"
        coroutineScope {
            repeat(epocs) {
                val newMsg :FunctionMessage? = gbClient.listenForFunction(listeningMsg, 0) // blocking
                launch {
                    val epocTime = handleNextRequest(newMsg)
                    Measurement.log(serverName, epocTime, "processed-${it+1}", "$receivedCalls;$receivedNacks", null, logToCsv)
                }
            }
        }
        shutdown(false)
    }
    fun shutdown(withCountdown: Boolean) {
        val countdown = 2000L
        state = false
        if (withCountdown) {
            logger.info(".:Shutting down GeoFaaS in ${countdown}ms:.")
            sleepNoLog(countdown, 0)
        } else logger.info(".:Shutting down GeoFaaS:.")
        gbClient.terminate()
        faasRegistry.clear()
        listeningThread.interrupt()
        Measurement.log(gbClient.id, -1, "totalReceivedMsg(GF/gB)", "${gbClient.receivedPubs.get()};${gbClient.receivedHandshakes.get()}", null, logToFile)
        Measurement.log(gbClient.id, -1, "received calls/nacks", "$receivedCalls;$receivedNacks", null, logToFile)
        Measurement.log(gbClient.id, -1, "received retries", receivedRetries.get().toString(), null, logToFile)
        Measurement.log(gbClient.id, -1, "totalReceivedMsg(GF/gB)", "${gbClient.receivedPubs.get()};${gbClient.receivedHandshakes.get()}", null, logToCsv)
        Measurement.log(gbClient.id, -1, "received calls/nacks", "$receivedCalls;$receivedNacks", null, logToCsv)
        Measurement.log(gbClient.id, -1, "received retries", receivedRetries.get().toString(), null, logToCsv)
        Measurement.close()
        logger.info("GeoFaaS properly shutdown")
    }

    // returns run time
    private fun handleNextRequest(newMsg :FunctionMessage?): Long {
        return measureTimeMillis {
            inProgressRequests.incrementAndGet()
            if (newMsg != null) {
                val distanceToClient = gbClient.location.distanceKmTo(newMsg.responseTopicFence.fence.toGeofence().center)
                var eventDesc = "newMsg;${newMsg.funcAction}"
                if (newMsg.typeCode == TypeCode.RETRY) {
                    receivedRetries.incrementAndGet()
                    eventDesc += ";Retry"
                }
                Measurement.log(newMsg.responseTopicFence.senderId,-1,  eventDesc,"${newMsg.funcName}(${newMsg.data});$distanceToClient", newMsg.reqId, logToCsv)
                val clientFence = newMsg.responseTopicFence.fence.toGeofence() // JSON to Geofence

                if (newMsg.funcAction == FunctionAction.CALL) { // cloud behave same as Edge, also listening to '/retry'
                    receivedCalls.incrementAndGet()
                    logRuntime(newMsg.responseTopicFence.senderId, "ACK;sent", newMsg.funcName, newMsg.reqId, logToCsv){ // todo: send the ack after checking if there is any FaaS serving the function and tell the client about it
                        gbClient.sendAck(newMsg.funcName, clientFence, newMsg.reqId) // tell the client you received its request
                    }
                    // FaaS
                    val registeredFunctions: List<String> = faasRegistry.flatMap { tf -> tf.functions().map { func -> func.name } }.distinct()
                    if (newMsg.funcName in registeredFunctions){ // I will not check if the request is for a subscribed topic (function), because otherwise geobroker won't deliver it
                        val selectedFaaS: TinyFaasClient = logRuntime(gbClient.id, "Select;FaaS", "between: ${registeredFunctions.joinToString(separator = ";")}", newMsg.reqId, logToCsv) {
                            bestAvailFaaS(newMsg.funcName, null)
                        }
                        val beforeCallInProgress = inProgressRequests.get()
                        val response: Pair<okhttp3.Response?, Boolean>
                        val faasTime = measureTimeMillis { response = selectedFaaS.call(newMsg.funcName, newMsg.data) }
                        Measurement.log(gbClient.id, faasTime, "FaaS;Response", "${newMsg.funcName}(${newMsg.data})", newMsg.reqId, logToCsv)
                        logger.debug("FaaS's raw Response: {}", response) // HttpResponse[http://localhost:8000/sieve, 200 OK]

                        // handle the Result
                        if (gbClient.id == "GeoFaaS-Berlin")
                            logger.warn("Offloading all requests! for test purposes!")
                        if (response.first != null && response.second && gbClient.id != "GeoFaaS-Berlin") {
                            val responseBody: String =
                                response.first!!.body?.string()?.trimEnd() ?: ""//NOTE: tinyFaaS's response always has a trailing '\n'
                            logRuntime(newMsg.responseTopicFence.senderId, "Result;sent", newMsg.funcName, newMsg.reqId, logToCsv){
                                gbClient.sendResult(newMsg.funcName, responseBody, clientFence, newMsg.reqId)
                            }
                            logger.info("Sent the result '{}' to functions/${newMsg.funcName}/result for {}", responseBody, newMsg.responseTopicFence.senderId) // wiki: Found 1229 primes under 10000
                        } else { // connection refused?
                            //TODO call another FaaS or offload if there is no more FaaS serving the func
                            logger.error("No/Bad response from '${selectedFaaS.host}:${selectedFaaS.port}' FaaS when calling '${newMsg.funcName}'. beforeCall:current #in-progress: $beforeCallInProgress:${inProgressRequests.get()}")
                            if(mode == ClientType.CLOUD)
                                logger.error("The Client will NOT receive any response! This is end of the line of offloading")
                            else {
                                logger.warn("Offloading to cloud...")
                                logRuntime(newMsg.responseTopicFence.senderId, "NACK;sent", "No response from the FaaS '${selectedFaaS.host}:${selectedFaaS.port}'. Offloaded to the cloud!", newMsg.reqId, logToCsv){
                                    gbClient.sendNack(newMsg.funcName, newMsg.data, clientFence, newMsg.reqId, "GeoFaaS-Cloud")
                                }
                            }
                        }
                    } else {
                        logger.fatal("No FaaS is serving the '${newMsg.funcName}' function!")
                        if(mode == ClientType.CLOUD)
                            logger.error("The Client will NOT receive any response! This is end of the line of offloading")
                        else {
                            logger.warn("Offloading to cloud...")
                            logRuntime(newMsg.responseTopicFence.senderId, "NACK;sent", "No FaaS is serving the '${newMsg.funcName}' function! Offloading to cloud...", newMsg.reqId, logToCsv){
                                gbClient.sendNack(newMsg.funcName, newMsg.data, clientFence, newMsg.reqId, "GeoFaaS-Cloud")
                            }
                        }
                    }
                } else if (newMsg.funcAction == FunctionAction.NACK) { // only for Cloud
                    receivedNacks.incrementAndGet()
//                gbClient.sendAck(newMsg.funcName, clientFence) // tell the client you received its request
                    val registeredFunctions: List<String> = faasRegistry.flatMap { tf -> tf.functions().map { func -> func.name } }.distinct()
                    if (newMsg.funcName in registeredFunctions){ // I will not check if the request is for a subscribed topic (function), because otherwise geobroker won't deliver it
                        val selectedFaaS: TinyFaasClient = logRuntime(gbClient.id, "Select;FaaS", "between: ${registeredFunctions.joinToString(separator = ";")}", newMsg.reqId, logToCsv) {
                            bestAvailFaaS(newMsg.funcName, null)
                        }
                        val response: Pair<okhttp3.Response?, Boolean>
                        val faasTime = measureTimeMillis { response = selectedFaaS.call(newMsg.funcName, newMsg.data) }
                        Measurement.log(gbClient.id, faasTime, "FaaS;Response", "${newMsg.funcName}(${newMsg.data})", newMsg.reqId, logToCsv)
                        logger.debug("FaaS's raw Response: {}", response) // HttpResponse[http://localhost:8000/sieve, 200 OK]

                        if (response.first != null && response.second) {
                            val responseBody: String = response.first!!.body.toString().trimEnd() //NOTE: tinyFaaS's response always has a trailing '\n'
//                        GlobalScope.launch{
                            logRuntime(newMsg.responseTopicFence.senderId, "Result;sent", newMsg.funcName, newMsg.reqId, logToCsv){
                                gbClient.sendResult(newMsg.funcName, responseBody, clientFence, newMsg.reqId, true)
                            }
                            logger.info("Sent the result '{}' to functions/${newMsg.funcName}/result for {}", responseBody, newMsg.responseTopicFence.senderId) // wiki: Found 1229 primes under 10000
//                        }
                        } else { // connection refused?
                            logger.error("No/Bad response from the FaaS with '${selectedFaaS.host}:${selectedFaaS.port}' address when calling '${newMsg.funcName}'")
//                        gbClient.sendNack(newMsg.funcName, newMsg.data, clientFence)
                        }
                    } else {
                        logger.fatal("No FaaS is serving the '${newMsg.funcName}' function!")
//                    gbClient.sendNack(newMsg.funcName, newMsg.data, clientFence)
                    }
                } else {
                    logger.error("The new request is not a CALL, but a ${newMsg.funcAction}!")
                }
            }
            inProgressRequests.decrementAndGet()
        }
    }
    private fun collectNewMessages() { // blocking
        do{ gbClient.asyncListen() } while (state)
    }

    private fun bestAvailFaaS(funcName: String, except: List<TinyFaasClient>?): TinyFaasClient {
        val availableServers: List<TinyFaasClient> = faasRegistry.filter { tf -> tf.isServingFunction(funcName) }
        if (except == null)
            return availableServers.first() // TODO: choose between FaaS servers
        else { // add exception in selection
            return availableServers.filter { tf -> !except.contains(tf) }.first()
        }
    }
}

suspend fun main(args: Array<String>) { // supply the broker id (same as disgb-registry.json), epochs, and running mode
    println(args[0])
    Measurement.log(args[0], -1, "Started", args[1], null, logToCsv)
    val disgbRegistry = logRuntime(args[0], "init", "Broker registry", null, logToCsv){
        BrokerAreaManager(args[0]) // broker id
    }
    logRuntime(args[0], "fetch", args[2], null, logToCsv) {
        when (args[2]) { // initialize
            "production" -> disgbRegistry.readFromFile("geobroker/config/disgb-registry.json")
            "intellij"   -> disgbRegistry.readFromFile("GeoFaaS/src/main/resources/DisGB-Config/DistanceScenarioLocal/disgb-registry.json")
            "localjar"   -> disgbRegistry.readFromFile("../../GeoFaaS/src/main/resources/DisGB-Config/DistanceScenarioLocal/disgb-registry.json")
            else -> throw RuntimeException("Wrong running mode. please specify any of 'production', 'localjar', or 'intellij'")
        }
    }
    val brokerInfo = disgbRegistry.ownBrokerInfo
    val brokerArea: Geofence = disgbRegistry.ownBrokerArea.coveredArea
    val location = if(args[0] == "Cloud") Location(51.498593,-0.176959) else brokerArea.center
    println(location)
    Measurement.log(args[0], -1, "BrokerArea", brokerArea.toString().replace(',', ';'), null, logToCsv)
    val geofaas = Server(location, args[3].toBoolean(), brokerInfo.ip, brokerInfo.port, "GeoFaaS-${brokerInfo.brokerId}", brokerAreaManager =  disgbRegistry)

    val tf = TinyFaasClient("localhost", 8000)
    val registerSuccess = geofaas.registerFaaS(tf)

    Signal.handle(Signal("INT")){// handle terminate signal

        Measurement.log(args[0], -1, "Intrupt received","", null, logToCsv)
        geofaas.shutdown(true)
        exitProcess(0)
    }

    if (registerSuccess == StatusCode.Success) {
        geofaas.run(args[0], args[1].toInt())
    }
//    geofaas.shutdown()
}