package geofaas

import io.ktor.client.call.*
import org.apache.logging.log4j.LogManager
import geofaas.Model.FunctionAction
import geofaas.Model.GeoFaaSFunction

object GeoFaaS {
    private val logger = LogManager.getLogger()
    private val geobroker = GeoBrokerClient(debug = true)
    private var faasRegistry = mutableListOf<TinyFaasClient>()
    fun registerFunctions(functions: Set<GeoFaaSFunction>) { //FIXME: should update CALL subscriptions in geoBroker when remote FaaS changed
        val subscribedFunctionsName = geobroker.subscribedFunctionsList().distinct() // distinct removes duplicates
        functions.forEach { func ->
            if ( func.name in subscribedFunctionsName) {
                logger.debug("GeoFaaS already listens to '${func.name}' function calls")
            } else {
                geobroker.subscribeFunction(func.name) //FIXME: check if subscribe was successful
                logger.info("function '${func.name}' registered to the GeoFaaS, and will be served for the new requests")
            }
        }
    }

    suspend fun registerFaaS(tf: TinyFaasClient) {
        faasRegistry += tf
        val funcs: Set<GeoFaaSFunction> = tf.functions()
        if (funcs.isNotEmpty()) {
            logger.info("registered a new FaaS with serving funcs: $funcs")
            registerFunctions(funcs)
            logger.info("new FaaS's functions have been registered")
        } else {
            logger.warn("registered a new FaaS with no serving functions!")
        }


    }

    suspend fun handleNextRequest() {
        val newMsg = geobroker.listen() // blocking
        if (newMsg != null) {
            if (newMsg.funcAction == FunctionAction.CALL) {
                geobroker.sendAck(newMsg.funcName) // tell the client you received its request
                val registeredFunctionsName: List<String> = faasRegistry.flatMap { tf -> tf.functions().map { func -> func.name } }.distinct()
                if (newMsg.funcName in registeredFunctionsName){ // I will not check if the request is for a subscribed topic (function), because otherwise geobroker won't deliver it
                    val selectedFaaS: TinyFaasClient = bestAvailFaaS(newMsg.funcName)
                    val response = selectedFaaS.call(newMsg.funcName, newMsg.data)
                    logger.debug("FaaS Response: {}", response) // HttpResponse[http://localhost:8000/sieve, 200 OK]

                    if (response != null) {
                        val responseBody: String = response.body()
                        geobroker.sendResult(newMsg.funcName, responseBody)
                        logger.info("sent the result '{}' to functions/${newMsg.funcName}/result topic", responseBody) // wiki: Found 1229 primes under 10000
                    } else { // connection refused?
                        logger.error("No response from the FaaS with '${selectedFaaS.host}' address for the function call '${newMsg.funcName}'")
                        geobroker.sendNack(newMsg.funcName, newMsg.data)
                    }
                } else {
                    logger.fatal("No FaaS is serving the '${newMsg.funcName}' function!")
                    geobroker.sendNack(newMsg.funcName, newMsg.data)
                }
            } else {
                logger.error("The new request is not a CALL, but a ${newMsg.funcAction}!")
                // TODO ADD, NACK

            }
        }
    }
    fun terminate() {
        geobroker.terminate()
        faasRegistry.clear()
    }
    private fun bestAvailFaaS(funcName: String): TinyFaasClient {
        val availableServers: List<TinyFaasClient> = faasRegistry.filter { tf -> tf.isServingFunction(funcName) }
        return availableServers.first() // TODO: choose between FaaS servers
    }
}

suspend fun main() {
    val gf = GeoFaaS // singleton
    val sampleFuncNames = mutableSetOf(GeoFaaSFunction("sieve"))
    val tf = TinyFaasClient("localhost", 8000, sampleFuncNames)

    gf.registerFaaS(tf)
    repeat(1){
        gf.handleNextRequest() //TODO: call in a coroutine? or a separate thread
    }
    gf.terminate()
}