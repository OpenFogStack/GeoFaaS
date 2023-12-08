package geofaas.experiment

import de.hasenburg.geobroker.commons.model.spatial.Location
import geofaas.Client
import geofaas.Model.FunctionMessage
import geofaas.Model.RequestID
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis

suspend fun main(args: Array<String>) {
    val locations = Commons.locScenario3
    val clientsLocPair = mutableListOf<Pair<Client, Pair<String, Location>>>()
    clientsLocPair.addAll(
        locations.mapIndexed { i, p ->
            Client(p.second, Commons.debug, Commons.brokerAddresses["Potsdam"]!!, 5560, "Client${i+1}", 8000, 3000) to p
        }
    )
    coroutineScope {
        clientsLocPair.forEach { clientLocPair ->
            launch {
                val client = clientLocPair.first
                val locPair = clientLocPair.second
                val numRequests = args[0].toInt()
                var cloudCounter = 0; var edgeCounter = 0
                Measurement.log(client.id, -1, "Started at", locPair.first, null)
                val elapsed = measureTimeMillis {
                    for (i in 1..numRequests)  {
                        val reqId = RequestID(i, client.id, locPair.first)

                        val res: Pair<FunctionMessage?, Long> = client.call("sieve",  "", reqId)
                        // Note: call's run time also depends on number of the retries
                        if(res.first != null){
                            val serverInfo = res.first!!.responseTopicFence
                            if(serverInfo.senderId.contains("Cloud"))
                                cloudCounter++
                            else edgeCounter++
                            Measurement.log(client.id, res.second, "Done", serverInfo.senderId+";${res.first!!.typeCode}", reqId)
                        } // details shows responder server name and type of response (offload, or normal)
                        else client.throwSafeException("${client.id}-($i-${locPair.first}): NOOOOOOOOOOOOOOO Response! (${res.second}ms)")
                    }
                }
                client.shutdown()
                Measurement.log(client.id, elapsed, "Finished", "$numRequests requests", null)
                Measurement.log(client.id, elapsed, "byCloud/Edge", "$cloudCounter;$edgeCounter", null)
            }
        }
    }
    Measurement.close()
}