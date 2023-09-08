package geofaas

import de.hasenburg.geobroker.commons.model.spatial.Geofence
import de.hasenburg.geobroker.commons.model.spatial.Location
import io.ktor.client.call.*
import org.apache.logging.log4j.LogManager
import geofaas.Model.FunctionAction
import geofaas.Model.GeoFaaSFunction

object GeoFaaSEdge {
    private val logger = LogManager.getLogger()
    private val gbClient = GBClientEdge(Location(0.0,0.0), true)
    private var faasRegistry = mutableListOf<TinyFaasClient>()
    fun registerFunctions(functions: Set<GeoFaaSFunction>) { //FIXME: should update CALL subscriptions in geoBroker when remote FaaS added/removed serving function
        val subscribedFunctionsName = gbClient.subscribedFunctionsList().distinct() // distinct removes duplicates
        functions.forEach { func ->
            if (func.name in subscribedFunctionsName) {
                logger.debug("GeoFaaS already subscribed to '${func.name}' function calls")
            } else {
                gbClient.subscribeFunction(func.name, Geofence.circle(Location(0.0, 0.0), 2.0)) //FIXME: check if subscribe was successful
                logger.info("function '${func.name}' registered to the GeoFaaS, and will be served for the new requests")
            }
        }
    }

    suspend fun registerFaaS(tf: TinyFaasClient) {
        faasRegistry += tf
        val funcs: Set<GeoFaaSFunction> = tf.functions()
        if (funcs.isNotEmpty()) {
            logger.info("registered a new FaaS. Now registering its serving funcs: $funcs")
            registerFunctions(funcs)
            logger.info("new FaaS's functions have been registered")
        } else {
            logger.warn("registered a new FaaS with no serving functions!")
        }
    }

    suspend fun handleNextRequest() {
        val newMsg = gbClient.listen() // blocking
        if (newMsg != null) {
            if (newMsg.funcAction == FunctionAction.CALL) {
                gbClient.sendAck(newMsg.funcName) // tell the client you received its request
                val registeredFunctionsName: List<String> = faasRegistry.flatMap { tf -> tf.functions().map { func -> func.name } }.distinct()
                if (newMsg.funcName in registeredFunctionsName){ // I will not check if the request is for a subscribed topic (function), because otherwise geobroker won't deliver it
                    val selectedFaaS: TinyFaasClient = bestAvailFaaS(newMsg.funcName)
                    val response = selectedFaaS.call(newMsg.funcName, newMsg.data)
                    logger.debug("FaaS Response: {}", response) // HttpResponse[http://localhost:8000/sieve, 200 OK]

                    if (response != null) {
                        val responseBody: String = response.body()
                        gbClient.sendResult(newMsg.funcName, responseBody)
                        logger.info("sent the result '{}' to functions/${newMsg.funcName}/result topic", responseBody) // wiki: Found 1229 primes under 10000
                    } else { // connection refused?
                        logger.error("No response from the FaaS with '${selectedFaaS.host}' address for the function call '${newMsg.funcName}'")
                        gbClient.sendNack(newMsg.funcName, newMsg.data)
                    }
                } else {
                    logger.fatal("No FaaS is serving the '${newMsg.funcName}' function!")
                    gbClient.sendNack(newMsg.funcName, newMsg.data)
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
    val gf = GeoFaaSEdge // singleton
//    val sampleFuncNames = mutableSetOf(GeoFaaSFunction("sieve")) // could be removed
    val tf = TinyFaasClient("localhost", 8000)//, sampleFuncNames)

    gf.registerFaaS(tf)
    repeat(1){
        gf.handleNextRequest() //TODO: call in a coroutine? or a separate thread
    }
    gf.terminate()
}