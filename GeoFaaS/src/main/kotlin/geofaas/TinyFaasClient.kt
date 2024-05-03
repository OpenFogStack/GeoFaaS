package geofaas

import geofaas.Model.GeoFaaSFunction
import org.apache.logging.log4j.LogManager
import okhttp3.*

//Note: management API is on the 8080 port, Thus, CANNOT run two instances on the same ip
class TinyFaasClient (val host: String, val port: Int, private var functionsLocal: Set<GeoFaaSFunction> = mutableSetOf()) {
     // Note: this is local
    private val logger = LogManager.getLogger()
    //Todo: on init: check host:port if tinyFaaS is online and handle 'connection refused'
    private val client = OkHttpClient()

    fun call(funcName: String, data: String): Pair<okhttp3.Response?, Boolean> {
        return try {
            val urlBuilder = HttpUrl.Builder()
                .scheme("http")
                .host(host)
                .port(port)
                .addPathSegment(funcName)
                .build()

            val requestBody = FormBody.Builder()
                .addEncoded("data", data)
                .build()

            val request = Request.Builder()
                .url(urlBuilder)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val isValid = respValidator(response)
            Pair(response, isValid)
        } catch (e: Exception) { // Use Exception for broader error handling
            logger.error("calling '$host:$port/$funcName' failed. {}", e.message)
            Pair(null, false)
        }
    }

    fun functions() : Set<GeoFaaSFunction> { // NOTE: could cause performance issue later
        //TODO: should update (append/remove) CALL subscriptions in geoBrokerClient
//        functionsLocal = remoteFunctions() ?: throw RuntimeException("can't get functions list! is the remote tinyFaaS running?")
        return functionsLocal
    }
    fun isServingFunction(funcName: String): Boolean {
        return funcName in functionsLocal.map { it.name }
    }

    fun remoteFunctions(): Set<GeoFaaSFunction>? {
        try {
            val request = Request.Builder()
                .url("http://$host:8080/list")
                .build()
            val resp: Response = client.newCall(request).execute()
            val body: String = resp.body?.string() ?: ""
            val funcs: List<String> = body.split("\n").filter { it.isNotBlank() }
            val gfFunction: Set<GeoFaaSFunction> = funcs.map { name -> GeoFaaSFunction(name) }.toSet()
            functionsLocal = gfFunction // update cache
            logger.debug("functionsLocal updated. dump: {}", functionsLocal)
            return gfFunction
        }
        catch (e: Throwable) {
            logger.fatal("Error when requesting the list of functions from '$host:8080'. $e")
            return null
        }
    }


    private fun respValidator(resp: Response): Boolean{ // TODO: to be used instead of builtin "expectSuccess = true"
        val httpCode = resp.code
        if (httpCode !in 200..299) {
            if (httpCode == 404)
                logger.error("function is not being served by tinyFaaS '$host:$port'")
            else
                logger.error("bad response code '$httpCode' from tinyFaaS '$host:$port'!")
            return false
        }
        return true
    }
}

//suspend fun main() {
//    val tf = TinyFaasClient("localhost", 8000)
//    println(tf.remoteFunctions()?: "null!!!")
//    val response = tf.call("sieve", "888")
//    val b: String = response?.body() ?: "null!!!"
//    println(b)
//}