package geofaas

import de.hasenburg.geobroker.commons.model.disgb.BrokerInfo
import de.hasenburg.geobroker.commons.model.spatial.Location
import de.hasenburg.geobroker.commons.model.spatial.toGeofence
import de.hasenburg.geobroker.commons.sleepNoLog
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.apache.logging.log4j.LogManager
import kotlin.system.measureTimeMillis
import geofaas.Model.FunctionMessage
import geofaas.Model.StatusCode

class Client(loc: Location, debug: Boolean, host: String, port: Int, id: String = "ClientGeoFaaS1") {
    private val logger = LogManager.getLogger()
    private val gbClient = ClientGBClient(loc, debug, host, port, id, 8000, 3000)
    val id
        get() = gbClient.id

    fun moveTo(dest: Pair<String, Location>): Pair<StatusCode, BrokerInfo?> {
        val status: Pair<StatusCode, BrokerInfo?>
        val elapsed = measureTimeMillis { status = gbClient.updateLocation(dest.second) }
        val newBroker = if (status.second == null) "sameBroker" else status.second!!.brokerId
        Measurement.log(id, elapsed,"MovedTo;${dest.first}", newBroker)
        return status
    }

    // returns a pair of result and run time
    fun call(funcName: String, param: String): Pair<FunctionMessage?, Long> {
        val retries = 2 //2
        val result: FunctionMessage?
        val elapsed = measureTimeMillis {
            result = gbClient.callFunction(funcName, param, retries, 0.1)
        }
        if (result == null)
            logger.error("No result received after {} retries! {}ms", retries, elapsed)

        logger.debug("my geoBroker client id: {}", gbClient.basicClient.identity)
        return Pair(result, elapsed)
    }

    fun shutdown() {
        gbClient.terminate()
    }

    fun throwSafeException(msg: String) {
        gbClient.throwSafeException(msg) // also runs terminate
    }

}

val clientLoc = listOf("middleButCloserToParis" to Location(50.289339,3.801270),
    "parisNonOverlap" to Location(48.719961,1.153564),
    "parisOverlap" to Location(48.858391, 2.327385), // overlaps with broker area but the broker is not inside the fence
    "paris1" to Location(48.835797,2.244301), // Boulogne Bilancourt area in Paris
    "reims" to Location(49.246293,4.031982), // East France. inside parisEdge
    "parisEdge" to Location(48.877366, 2.359708),
    "saarland" to Location(49.368066,6.976318),
    "frankfurtEdge" to Location(50.106732,8.663124),
    "darmstadt" to Location(49.883132,8.646240),
    "potsdam" to Location(52.400953,13.060169),
    "franceEast" to Location(47.323931,5.174561),
    "amsterdam" to Location(52.315195,4.894409),
    "hamburg" to Location(53.527248,9.986572),
    "belgium" to Location(50.597186,4.822998)
)
val locFranceToPoland = listOf("Paris" to Location(48.936935,2.702637), // paris
    "Reims" to Location(49.282140,4.042969), // Reims
    "Saarland" to Location(49.375220,6.998291), // Saarland
    "Bonn" to Location(50.736455,7.119141), // Bonn
    "Leipzig" to Location(51.426614,12.194824), // Leipzig
    "Potsdam" to Location(52.348763,13.051758), // Potsdam
    "Warsaw" to Location(52.254709,20.939941) // warsaw
)
val locBerlinToFrance = listOf("Potsdam" to Location(52.348763,13.051758), // Potsdam
    "Leipzig" to Location(51.426614,12.194824), // Leipzig
    "Bonn" to Location(50.736455,7.119141), // Bonn
    "Saarland" to Location(49.375220,6.998291), // Saarland
    "Reims" to Location(49.282140,4.042969), // Reims
    "Paris" to Location(48.936935,2.702637), // paris
    "Rennes" to Location(48.107431,-1.691895) // rennes
)
val locFrankParisBerlin = listOf("Nurnberg" to Location(49.468124,11.074219),
    "Mannheim" to Location(49.482401,8.459473),
    "Dusseldorf" to Location(51.165567,6.811523),
    "Letzebuerg" to Location(49.951220,6.086426),
    "Hessen" to Location(50.597186,9.382324),
    "North Leipzig" to Location(51.631657,12.260742), // berlin is responsible
    "Reims" to Location(49.081062,4.262695),
    "South Paris" to Location(48.502048,2.812500),
    "North Paris" to Location(49.453843,1.735840),
    )
val brokerAddresses = mapOf("Frankfurt" to "141.23.28.205",
    "Paris" to "141.23.28.208",
    "Berlin" to "141.23.28.207",
    "Cloud" to "141.23.28.209",
    "Local" to "localhost")

suspend fun main() {

    val debug = false
//    val client = Client(locFranceToPoland.first().second, debug, brokerAddresses["Frankfurt"]!!, 5560, "ClientGeoFaaS1")
//    val res: String? = client.call("sieve", client.id)
//    if(res != null) println("${client.id} Result: $res")
//    else println("${client.id}: NOOOOOOOOOOOOOOO Response!")
//    client.shutdown()
//    println("${client.id} finished!")
    ////////////////////////////////// multiple Moving Clients //////////////////////
    val clientLocPairs = mutableListOf<Pair<Client, List<Pair<String,Location>>>>()
    clientLocPairs.add(Client(locFranceToPoland.first().second, debug, brokerAddresses["Paris"]!!, 5560, "Client1") to locFranceToPoland)
    clientLocPairs.add(Client(locBerlinToFrance.first().second, debug, brokerAddresses["Berlin"]!!, 5560, "Client2") to locBerlinToFrance)
    clientLocPairs.add(Client(locFrankParisBerlin.first().second, debug, brokerAddresses["Frankfurt"]!!, 5560, "Client3") to locFrankParisBerlin)
    clientLocPairs.add(Client(clientLoc.first().second, debug, brokerAddresses["Paris"]!!, 5560, "Client4") to clientLoc)
    coroutineScope {
        clientLocPairs.forEach { clientLocPair ->
            launch {
                val client = clientLocPair.first
                val locations = clientLocPair.second
                Measurement.log(client.id, -1, "Started at", locations.first().first)
                val elapsed = measureTimeMillis {
                    locations.forEachIndexed { i, loc ->
    //                    sleepNoLog(3000, 0)
                        if (i > 0) {
                            client.moveTo(loc)
    //                        sleepNoLog(6000, 0)
                        }
                        val res: Pair<FunctionMessage?, Long> = client.call("sieve",  "$i-${loc.first}|${client.id}")
                        // Note: call's run time also depends on number of the retries
                        if(res.first != null) Measurement.log(client.id, res.second, "Result-$i", loc.second.distanceKmTo(res.first!!.responseTopicFence.fence.toGeofence().center).toString()) // misc shows distance in km in Double format
                        else client.throwSafeException("${client.id}-($i-${loc.first}): NOOOOOOOOOOOOOOO Response! (${res.second}ms)")
                    }
                }
                client.shutdown()
                Measurement.log(client.id, elapsed, "Finished", "${locations.size} total locations")
            }
        }
    }
    Measurement.close()
    /////////////////2 local 2 nodes//////
//    val client1 = Client(clientLoc["paris1"]!!, debug, brokerAddresses["Local"]!!, 5559)
//    sleepNoLog(2000, 0)
////    val res: String? = client1.call("sieve", "")
////    if(res != null) println("Result: $res")
//    sleepNoLog(5000, 0)
//
//    client1.moveTo(clientLoc["darmstadt"]!!)
//    sleepNoLog(2000, 0)
////    val res2: String? = client1.call("sieve", "")
////    if(res2 != null) println("Result: $res2")
////    sleepNoLog(2000, 0)
//    client1.shutdown()

}