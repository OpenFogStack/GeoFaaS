package geofaas

import io.ktor.client.call.*
import org.apache.logging.log4j.LogManager
import geofaas.Model.FunctionAction
import geofaas.Model.GeoFaaSFunction
import org.apache.logging.log4j.Logger

object GeoFaaS {
    private val logger = LogManager.getLogger()
    private val geobroker = GeoBrokerClient(debug = true)
    private var registeredFunctions = mutableSetOf<GeoFaaSFunction>()
    private var faasRegistery = mutableMapOf<TinyFaasClient, List<String>>()
    fun registerFunction(name: String) {
        if (registeredFunctions.any { it.name == name }) {
            logger.error("function $name is already registered in the GeoFaaS")
        } else {
            geobroker.subscribeFunction(name) //FIXME: check if subscribe was successful
            registeredFunctions.add(GeoFaaSFunction(name))
            logger.info("function '$name' registered to the GeoFaaS, and will be served for the new requests")
        }
    }

    fun registerFaaS(tf: TinyFaasClient, funcs: List<String>) {
        faasRegistery += mapOf(tf to funcs)
        logger.info("registered a new FaaS with serving funcs: $funcs")
    }

    suspend fun handleIncomingRequest() {
        val newMsg = geobroker.listen()
        if (newMsg != null) {
            if (newMsg.funcAction == FunctionAction.CALL) {
                if (registeredFunctions.filter { it.name == newMsg.funcName }.any()){
                    val response = bestAvailFaaS(newMsg.funcName).call(newMsg.funcName, newMsg.data)
                    logger.debug("FaaS Response: {}", response) // HttpResponse[http://localhost:8000/sieve, 200 OK]
                    // TODO: send ack
                    val responseBody: String = response.body()
                    geobroker.sendResult(newMsg.funcName, responseBody)
                    logger.info("sent the result '{}' to functions/${newMsg.funcName}/result topic", responseBody) //Found 1229 primes under 10000
                } else {
                    logger.fatal("No FaaS is serving the function ${newMsg.funcName}!")
                }
            } // FunctionAction is not a CALL
        }
    }
    fun terminate() {
        geobroker.terminate()
        registeredFunctions.clear()
    }
    private fun bestAvailFaaS(funcName: String): TinyFaasClient {
        val availableServers = faasRegistery.filter { it.value.contains(funcName) }
        return availableServers.entries.first().key
    }
    // Methods
        // runningFunctions: list the functions on tinyFaaS
        // registeredFunctions: list of registered f (could be running or not)
        // update and remove registeredFunctions if the no faas is serving: mutableMap.remove("Key1") // delete existing value
}

suspend fun main() {
    val tf = TinyFaasClient("localhost", 8000)
    val gf = GeoFaaS // singleton
    val sampleFuncName: String = "sieve"

    gf.registerFaaS(tf, listOf(sampleFuncName))
    gf.registerFunction(sampleFuncName)
    repeat(1){
        gf.handleIncomingRequest() //TODO: call in a coroutine? or a main thread
    }
    gf.terminate()
}