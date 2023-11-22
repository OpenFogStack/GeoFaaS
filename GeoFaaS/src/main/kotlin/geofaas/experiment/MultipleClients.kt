package geofaas.experiment

import de.hasenburg.geobroker.commons.model.spatial.Location
import de.hasenburg.geobroker.commons.model.spatial.toGeofence
import geofaas.Client
import geofaas.Model.FunctionMessage
import geofaas.Model.RequestID
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis

suspend fun main() {
    ////////////////////////////////// multiple Moving Clients //////////////////////
    val clientLocPairs = mutableListOf<Pair<Client, List<Pair<String, Location>>>>()
    clientLocPairs.add(Client(Commons.locFranceToPoland.first().second, Commons.debug, Commons.brokerAddresses["ParisW"]!!, 5560, "Client1") to Commons.locFranceToPoland)
    clientLocPairs.add(Client(Commons.locBerlinToFrance.first().second, Commons.debug, Commons.brokerAddresses["BerlinW"]!!, 5560, "Client2") to Commons.locBerlinToFrance)
    clientLocPairs.add(Client(Commons.locFrankParisBerlin.first().second, Commons.debug, Commons.brokerAddresses["FrankfurtW"]!!, 5560, "Client3") to Commons.locFrankParisBerlin)
    clientLocPairs.add(Client(Commons.clientLoc.first().second, Commons.debug, Commons.brokerAddresses["ParisW"]!!, 5560, "Client4") to Commons.clientLoc)
    coroutineScope {
        clientLocPairs.forEach { clientLocPair ->
            launch {
                val client = clientLocPair.first
                val locations = clientLocPair.second
                Measurement.log(client.id, -1, "Started at", locations.first().first, null)
                val elapsed = measureTimeMillis {
                    locations.forEachIndexed { i, loc ->
                        val reqId = RequestID(i, client.id, loc.first)
                        //                    sleepNoLog(3000, 0)
                        if (i > 0) {
                            client.moveTo(loc, reqId)
                            //                        sleepNoLog(6000, 0)
                        }
                        val res: Pair<FunctionMessage?, Long> = client.call("sieve",  "$i-${loc.first}|${client.id}", reqId)
                        // Note: call's run time also depends on number of the retries
                        if(res.first != null) Measurement.log(client.id, res.second, "Done", loc.second.distanceKmTo(res.first!!.responseTopicFence.fence.toGeofence().center).toString(), reqId) // misc shows distance in km in Double format
                        else client.throwSafeException("${client.id}-($i-${loc.first}): NOOOOOOOOOOOOOOO Response! (${res.second}ms)")
                    }
                }
                client.shutdown()
                Measurement.log(client.id, elapsed, "Finished", "${locations.size} total locations", null)
            }
        }
    }
    Measurement.close()
}