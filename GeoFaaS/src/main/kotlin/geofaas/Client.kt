package geofaas

import de.hasenburg.geobroker.commons.model.disgb.BrokerInfo
import de.hasenburg.geobroker.commons.model.spatial.Location
import de.hasenburg.geobroker.commons.model.spatial.toGeofence
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.apache.logging.log4j.LogManager
import kotlin.system.measureTimeMillis
import geofaas.Model.FunctionMessage
import geofaas.Model.StatusCode
import geofaas.Model.RequestID
import geofaas.experiment.Commons.brokerAddressesWifi
import geofaas.experiment.Commons.clientLoc
import geofaas.experiment.Commons.locBerlinToFrance
import geofaas.experiment.Commons.locFranceToPoland
import geofaas.experiment.Commons.locFrankParisBerlin
import geofaas.experiment.Measurement

class Client(loc: Location, debug: Boolean, host: String, port: Int, id: String = "ClientGeoFaaS1") {
    private val logger = LogManager.getLogger()
    private val gbClient = ClientGBClient(loc, debug, host, port, id, 8000, 3000)
    val id
        get() = gbClient.id

    fun moveTo(dest: Pair<String, Location>, reqId: RequestID): Pair<StatusCode, BrokerInfo?> {
        val status: Pair<StatusCode, BrokerInfo?>
        val elapsed = measureTimeMillis { status = gbClient.updateLocation(dest.second) }
        val newBroker = if (status.second == null) "sameBroker" else status.second!!.brokerId
        Measurement.log(id, elapsed,"Moved", newBroker, reqId)
        return status
    }

    // returns a pair of result and run time
    fun call(funcName: String, param: String, reqId: RequestID): Pair<FunctionMessage?, Long> {
        val retries = 2 //2
        val result: FunctionMessage?
        val elapsed = measureTimeMillis {
            result = gbClient.callFunction(funcName, param, retries, 0.1, reqId)
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
    clientLocPairs.add(Client(locFranceToPoland.first().second, debug, brokerAddressesWifi["Paris"]!!, 5560, "Client1") to locFranceToPoland)
    clientLocPairs.add(Client(locBerlinToFrance.first().second, debug, brokerAddressesWifi["Berlin"]!!, 5560, "Client2") to locBerlinToFrance)
    clientLocPairs.add(Client(locFrankParisBerlin.first().second, debug, brokerAddressesWifi["Frankfurt"]!!, 5560, "Client3") to locFrankParisBerlin)
    clientLocPairs.add(Client(clientLoc.first().second, debug, brokerAddressesWifi["Paris"]!!, 5560, "Client4") to clientLoc)
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
    /////////////////2 local 2 nodes//////
//    val client1 = Client(clientLoc[3].second, debug, brokerAddresses["Local"]!!, 5560, "client1")
//    sleepNoLog(2000, 0)
//    val reqId1 = RequestID(1, "client1", clientLoc[3].first)
//    val res: Pair<FunctionMessage?, Long> = client1.call("sieve", "", reqId1)
//    if(res.first != null) Measurement.log("client1", res.second, "Done", clientLoc[3].second.distanceKmTo(res.first!!.responseTopicFence.fence.toGeofence().center).toString(), reqId1) // misc shows distance in km in Double format
//    else client1.throwSafeException("client1-(1-${clientLoc[3].first}): NOOOOOOOOOOOOOOO Response! (${res.second}ms)")
//    sleepNoLog(2000, 0)

//    val reqId2 = RequestID(2, "client1", clientLoc[8].first)
//    client1.moveTo(clientLoc[8], reqId2)
//    sleepNoLog(2000, 0)
//    val res2: Pair<FunctionMessage?, Long> = client1.call("sieve", "", reqId2)
//    if(res2.first != null)  Measurement.log("client1", res2.second, "Done", clientLoc[3].second.distanceKmTo(res2.first!!.responseTopicFence.fence.toGeofence().center).toString(), reqId2) // misc shows distance in km in Double format
//    else client1.throwSafeException("client1-(2-${clientLoc[8].first}): NOOOOOOOOOOOOOOO Response! (${res2.second}ms)")
//    sleepNoLog(2000, 0)
//    client1.shutdown()
//    Measurement.close()

}