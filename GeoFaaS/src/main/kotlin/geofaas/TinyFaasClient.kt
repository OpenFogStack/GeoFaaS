package geofaas

import de.hasenburg.geobroker.commons.sleepNoLog
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlin.Error
import geofaas.Model.GeoFaaSFunction
import org.apache.logging.log4j.LogManager
import kotlin.math.log

// https://ktor.io/docs/response.html

//Note: management API is on the 8080 port, Thus, CANNOT run two instances on the same ip
class TinyFaasClient (val host: String, val port: Int, private var functionsLocal: Set<GeoFaaSFunction> = mutableSetOf()) {
     // Note: this is local
    private val logger = LogManager.getLogger()
    //FIXME: on init: check host:port if tinyFaaS is online and handle 'connection refused'
    private val client: HttpClient = HttpClient(CIO) { // CIO is an asynchronous coroutine-based engine
        //expectSuccess = true // expectSuccess is a resp validation
        install(Logging)
     }

    suspend fun call(funcName: String, data: String): HttpResponse? {
        return try {
            val response = client.post("http://$host:$port/$funcName") {
                url {
                    parameters.append("data", data)
                }
            }
            respValidator(response)
            response
        } catch (e: Throwable) {
            //Fixme handle HttpResponse[http://localhost:8000/sieve, 500 Internal Server Error]
            logger.error("calling '$host:$port/$funcName' failed. {}", e.message)
            null
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

    suspend fun remoteFunctions(): Set<GeoFaaSFunction>? {
        // TODO: try catch connection refused
        try {
            val resp = client.get("http://$host:8080/list")
            val body = resp.body<String>()
            val funcs = body.split("\n").filter { it.isNotBlank() }
            val gfFunction = funcs.map { name -> GeoFaaSFunction(name) }.toSet()
            functionsLocal = gfFunction // update cache
            logger.debug("functionsLocal updated. dump: {}", functionsLocal)
            return gfFunction
        }
        catch (e: Throwable) {
            logger.fatal("Error when requesting the list of functions from '$host:8080'. $e")
            return null
        }
    }


    private fun respValidator(resp: HttpResponse): Boolean{ // TODO: to be used instead of builtin "expectSuccess = true"
        val httpCode = resp.status.value
        if (httpCode !in 200..299) {
            if (httpCode == 404)
                logger.error("function is not being served by tinyFaaS '$host:$port'")
            else
                logger.error("bad response code from tinyFaaS '$host:$port'! '$httpCode'")
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