package geofaas

import io.ktor.client.call.*
import org.apache.logging.log4j.LogManager
import geofaas.Model.FunctionAction
import geofaas.Model.FunctionMessage
import org.apache.logging.log4j.Logger

class GeoFaaS {
    // Properties
        // a collection of registered "functions" with corresponding faas instances
        // a Map of function to host FasSes (registry) or use tf instance to ask

}

suspend fun main() {
    val logger = LogManager.getLogger()
    val tf = TinyFaasClient("localhost", 8000)
    val geobroker = GeoBrokerClient()
    val sampleFunction = "sieve"

    geobroker.subscribeFunction(sampleFunction)
    repeat(3){
        handleIncomingRequest(geobroker, tf, logger) //TODO: call in a coroutine
    }


    geobroker.terminate()
}

suspend fun handleIncomingRequest(geobroker: GeoBrokerClient, tf: TinyFaasClient, logger: Logger) {
    val newMsg = geobroker.listen()
    if (newMsg != null) {
        if (newMsg.funcAction == FunctionAction.CALL) {
            val response = tf.call(newMsg.funcName, newMsg.data)
            logger.debug("FaaS Response: {}", response) // HttpResponse[http://localhost:8000/sieve, 200 OK]
            // TODO: send ack
            val responseBody: String = response.body()
            geobroker.sendResult(newMsg.funcName, responseBody)
            logger.info("sent the result '{}' to functions/${newMsg.funcName}/result topic", responseBody) //Found 1229 primes under 10000

        }
    }
}