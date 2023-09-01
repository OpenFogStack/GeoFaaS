package geofaas

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlin.Error
import geofaas.Model.GeoFaaSFunction
import org.apache.logging.log4j.LogManager

// https://ktor.io/docs/response.html

//Note: management API is on the 8080 port, Thus, CANNOT run two instances on the same ip
class TinyFaasClient (val host: String, val port: Int, var functions: MutableSet<GeoFaaSFunction> = mutableSetOf<GeoFaaSFunction>()) {
     // Note: this is local
    private val logger = LogManager.getLogger()
    //FIXME: on init: check host:port if tinyFaaS is online and 'connection refused' won't happen
    private val client: HttpClient = HttpClient(CIO) { // CIO is an asynchronous coroutine-based engine
        //expectSuccess = true // expectSuccess is a resp validation
        install(Logging)
     }

    suspend fun call(funcName: String, data: String): HttpResponse? {
        try {
            val resp = client.get("http://$host:$port/$funcName") //TODO: add data in the call
            return resp
        } catch (e: Throwable) {
            // TODO: print the error somewhere in log or in returning§
//            return Error("Error when calling", e)
        }
        return null
    }

    suspend fun funcList(): HttpResponse {
        return client.get("http://$host:8080/list")
    } // TODO: handle multiple line output from tinyFaaS. what is the format?

    fun isServingFunction(funcName: String): Boolean {
        return funcName in functions.map { it.name }
    }

    private fun respValidator(resp: HttpResponse): Error? {
        if (resp.status.value in 200..299) {
            return null
        }
        return Error("bad response code ${resp.status.value}")
    }
}

//suspend fun main() {
//    val tf = TinyFaasClient("localhost", 8000)
//    val response = tf.funcList()
//    val b: String = response.body()
//    println(b)
//}