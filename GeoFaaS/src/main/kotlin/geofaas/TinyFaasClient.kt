package geofaas

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
// https://ktor.io/docs/response.html

//Note: management API is on the 8080 port, Thus, CANNOT run two instances on the same ip
class TinyFaasClient (private val host: String, val port: Int) {
    //FIXME: on init: check host:port if tinyFaaS is online and 'connection refused' won't happen
    private val client: HttpClient = HttpClient(CIO) { // CIO is an asynchronous coroutine-based engine
        expectSuccess = true // expectSuccess is a resp validation
        install(Logging)
     }

    suspend fun call(funcName: String, data: String): HttpResponse {
        return client.get("http://$host:$port/$funcName") //TODO: add data in the call
    }

    suspend fun funcList(): HttpResponse {
        return client.get("http://$host:8080/list")
    } // TODO: handle multiple line output from tinyFaaS. what is the format?
}

//suspend fun main() {
//    val tf = TinyFaasClient("localhost", 8000)
//    val response = tf.funcList()
//    val b: String = response.body()
//    println(b)
//}