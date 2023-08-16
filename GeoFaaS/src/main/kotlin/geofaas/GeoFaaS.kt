package geofaas

import de.hasenburg.geobroker.commons.sleepNoLog
import io.ktor.client.call.*
import org.apache.logging.log4j.LogManager

class GeoFaaS {
    // Properties
        // a collection of "ServerlessFunction" (num of functions)
        // a Map of function to host FasSes (registry) or use tf instance to ask

}

suspend fun main() {
    val logger = LogManager.getLogger()
    val tf = TinyFaasClient("localhost", 8000)
    val geobroker = GeoBrokerClient()
    val sampleFunction = "sieve"

    geobroker.subscribeFunction(sampleFunction)
    val call = geobroker.listen(sampleFunction) //TODO: call in a coroutine
    if(call == "ok"){
        val response = tf.call(sampleFunction)
        logger.debug("FaaS Response: {}", response) // HttpResponse[http://localhost:8000/sieve, 200 OK]
        val responseBody: String = response.body()
        geobroker.sendResult(sampleFunction, responseBody)
        logger.info("sent the result '{}' to /$sampleFunction/result topic", responseBody) //Found 1229 primes under 10000
//        GlobalScope.launch {
//        }
    } else {
        logger.error("Expected a msg on the $sampleFunction channel!")
    }
    geobroker.terminate()
}