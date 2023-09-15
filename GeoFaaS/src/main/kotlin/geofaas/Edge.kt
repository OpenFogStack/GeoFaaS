package geofaas

import de.hasenburg.geobroker.commons.model.spatial.Geofence
import de.hasenburg.geobroker.commons.model.spatial.Location
import de.hasenburg.geobroker.commons.model.spatial.toGeofence
import io.ktor.client.call.*
import org.apache.logging.log4j.LogManager
import geofaas.Model.FunctionAction
import geofaas.Model.GeoFaaSFunction
import geofaas.Model.ListeningTopic

val cloudFence = Geofence.world()
class Edge(loc: Location, debug: Boolean, host: String = "localhost", port: Int = 5559, id: String = "GeoFaaSEdge1") {
    private val logger = LogManager.getLogger()
    private val gbClient = GBClientEdge(loc, debug, host, port, id)
    private var faasRegistry = mutableListOf<TinyFaasClient>()
    // returns ture if success to Subscribe to all the functions
    fun registerFunctions(functions: Set<GeoFaaSFunction>, fence: Geofence): Boolean { //FIXME: should update CALL subscriptions in geoBroker when remote FaaS added/removed serving function
        val subscribedFunctionsName = gbClient.subscribedFunctionsList().distinct() // distinct removes duplicates (/result & /ack)
        functions.forEach { func ->
            if (func.name in subscribedFunctionsName) {
                logger.debug("GeoFaaS already subscribed to '${func.name}' function calls")
            } else {
                val sub: MutableSet<ListeningTopic>? = gbClient.subscribeFunction(func.name, fence)
                if (sub != null) {
                    logger.info("new function '${func.name}' has registered to the GeoFaaS, and will be served for the new requests")
                } else {
                    logger.fatal("failed to register the '${func.name}' function to the GeoFaaS ${gbClient.mode}!")
                    return false
                }
            }
        }
        return true
    }

    suspend fun registerFaaS(tf: TinyFaasClient, fence: Geofence): Boolean {
        val funcs: Set<GeoFaaSFunction> = tf.functions()
        if (funcs.isNotEmpty()) {
            val registerSuccess = registerFunctions(funcs, fence)
            return if (registerSuccess) {
                logger.info("new FaaS's functions have been registered")
                faasRegistry += tf
                logger.info("registered a new FaaS with funcs: ${funcs.map { f -> f.name }}")
                true
            } else {
                logger.fatal("Failed to register FaaS '${tf.host}:${tf.port}'. Reason: Failed to register its functions")
                false
            }
        } else {
            logger.warn("registered a new FaaS with no serving functions!")
            faasRegistry += tf
            return true
        }
    }

    suspend fun handleNextRequest() {
        val newMsg :Model.FunctionMessage? = gbClient.listen() // blocking
        if (newMsg != null) {
            val clientFence = newMsg.responseTopicFence.fence.toGeofence()
            if (newMsg.funcAction == FunctionAction.CALL) {
                gbClient.sendAck(newMsg.funcName, clientFence) // tell the client you received its request
                val registeredFunctionsName: List<String> = faasRegistry.flatMap { tf -> tf.functions().map { func -> func.name } }.distinct()
                if (newMsg.funcName in registeredFunctionsName){ // I will not check if the request is for a subscribed topic (function), because otherwise geobroker won't deliver it
                    val selectedFaaS: TinyFaasClient = bestAvailFaaS(newMsg.funcName)
                    val response = selectedFaaS.call(newMsg.funcName, newMsg.data)
                    logger.debug("FaaS's raw Response: {}", response) // HttpResponse[http://localhost:8000/sieve, 200 OK]

                    if (response != null) {
                        val responseBody: String = response.body<String>().trimEnd() //NOTE: tinyFaaS's response always has a trailing '\n'
                        gbClient.sendResult(newMsg.funcName, responseBody, clientFence)
                        logger.info("${gbClient.id}: sent the result '{}' to functions/${newMsg.funcName}/result", responseBody) // wiki: Found 1229 primes under 10000
                    } else { // connection refused?
                        logger.error("No response from the FaaS with '${selectedFaaS.host}' address for the function call '${newMsg.funcName}'")
                        gbClient.sendNack(newMsg.funcName, newMsg.data, clientFence, cloudFence)
                    }
                } else {
                    logger.fatal("No FaaS is serving the '${newMsg.funcName}' function!")
                    gbClient.sendNack(newMsg.funcName, newMsg.data, clientFence, cloudFence)
                }
            } else {
                logger.error("The new request is not a CALL, but a ${newMsg.funcAction}!")
                // TODO ADD, NACK
            }
        }
    }
    fun terminate() {
        gbClient.terminate()
        faasRegistry.clear()
    }
    private fun bestAvailFaaS(funcName: String): TinyFaasClient {
        val availableServers: List<TinyFaasClient> = faasRegistry.filter { tf -> tf.isServingFunction(funcName) }
        return availableServers.first() // TODO: choose between FaaS servers
    }
}

suspend fun main() {
    val frankfurtLoc = Location(50.106732,8.663124) // same as frankfurt (broker are: radius: 2.1)
    val gf = Edge(frankfurtLoc, true, "localhost", 5559)
    val tf = TinyFaasClient("localhost", 8000)
//    val tf = TinyFaasClient("192.168.100.144", 8000) // home raspberry

    // NOTE: check if Geofence is 'world'
    val registerSuccess = gf.registerFaaS(tf, Geofence.circle(frankfurtLoc, 10.1))
    if (registerSuccess) {
        repeat(1){
            gf.handleNextRequest() //TODO: call in a coroutine? or  a separate thread
        }
    }
    gf.terminate()
}