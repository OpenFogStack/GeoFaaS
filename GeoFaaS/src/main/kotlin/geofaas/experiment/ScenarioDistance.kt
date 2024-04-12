package geofaas.experiment

import de.hasenburg.geobroker.commons.model.spatial.Location
import de.hasenburg.geobroker.commons.model.spatial.toGeofence
import de.hasenburg.geobroker.commons.sleepNoLog
import geofaas.Client
import geofaas.Model.FunctionMessage
import geofaas.Model.RequestID
import kotlin.system.measureTimeMillis

fun main(args: Array<String>) {
    val debug = args[0].toBoolean()
    val locations = Commons.locScenario1
    Measurement.log("", -1, "SenarioDistance (locSize)", locations.size.toString(), null)
    val client = Client(locations.first().second, debug,
        Commons.brokerAddresses["Berlin"]!!, 60001, "DistanceClient",
        4000, 4000)
    var cloudCounter = 0; var edgeCounter = 0
    Measurement.log(client.id, -1, "Started at", locations.first().first, null)

    val elapsed = measureTimeMillis {
        locations.forEachIndexed { i, loc ->
            val reqId = RequestID(i+1, client.id, loc.first)

            if (i > 0)
                client.moveTo(loc, reqId)
            val res: Pair<FunctionMessage?, Long> = client.call("sieve",  "", reqId, 1, 2)
            // Note: call's run time also depends on number of the retries
            if(res.first != null){
                val serverInfo = res.first!!.responseTopicFence
                val serverLocation = if(serverInfo.senderId.contains("Cloud")){
                    cloudCounter++
                    Location(51.498593,-0.176959) // Imperial College London
                } else {
                    edgeCounter++
                    serverInfo.fence.toGeofence().center
                }
                Measurement.log(client.id, res.second, "Done", loc.second.distanceKmTo(serverLocation).toString(), reqId)
            } // misc shows distance in km in Double format
            else client.throwSafeException("${client.id}-(${i+1}-${loc.first}): NOOOOOOOOOOOOOOO Response! (${res.second}ms)")
        }
    }

    client.shutdown()
    Measurement.log(client.id, elapsed, "Finished",
        "${locations.size} total locations", null)
    Measurement.log(client.id, elapsed, "byCloud/Edge", "$cloudCounter;$edgeCounter", null)
    Measurement.close()
}