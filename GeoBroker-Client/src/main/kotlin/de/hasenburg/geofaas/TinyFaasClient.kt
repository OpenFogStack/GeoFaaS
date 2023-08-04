package de.hasenburg.geofaas

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
// https://ktor.io/docs/response.html#body

class TinyFaasClient (private val host: String, val port: Int) {
    val client: HttpClient = HttpClient(CIO) { // CIO is an asynchronous coroutine-based engine
        expectSuccess = true // expectSuccess is a resp validation
        install(Logging)
     }

    suspend fun call(funcname: String): HttpResponse {
        return client.get("http://$host:$port/$funcname")
    }
}