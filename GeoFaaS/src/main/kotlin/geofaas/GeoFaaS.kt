package geofaas

import geofaas.TinyFaasClient
import io.ktor.client.call.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class GeoFaaS {
    // Properties
        // a collection of "ServerlessFunction" (num of functions)
        // a Map of function to host FasSes (registry) or use tf instance to ask

}

@OptIn(DelicateCoroutinesApi::class)
fun main() {
    val tf = TinyFaasClient("localhost", 8000)
    GlobalScope.async {
        val response = tf.call("sieve")
        val responseBody: String = response.body()
        println(responseBody)
    }

}