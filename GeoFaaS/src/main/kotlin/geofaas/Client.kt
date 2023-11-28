package geofaas

import de.hasenburg.geobroker.commons.model.disgb.BrokerInfo
import de.hasenburg.geobroker.commons.model.spatial.Location
import de.hasenburg.geobroker.commons.model.spatial.toGeofence
import de.hasenburg.geobroker.commons.sleepNoLog
import org.apache.logging.log4j.LogManager
import kotlin.system.measureTimeMillis
import geofaas.Model.FunctionMessage
import geofaas.Model.StatusCode
import geofaas.Model.RequestID
import geofaas.experiment.Commons.brokerAddresses
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
        val retries = 1 //2
        val result: FunctionMessage?
        val elapsed = measureTimeMillis {
            result = gbClient.callFunction(funcName, param, retries, 0.01, reqId)
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
//    val client = Client(locFranceToPoland.first().second, debug, brokerAddresses["Frankfurt"]!!, 5560, "client1")
//    val res: String? = client.call("sieve", client.id)
//    if(res != null) println("${client.id} Result: $res")
//    else println("${client.id}: NOOOOOOOOOOOOOOO Response!")
//    client.shutdown()
//    println("${client.id} finished!")

    /////////////////2 local 2 nodes//////
    val client1 = Client(clientLoc[3].second, debug, brokerAddresses["Local"]!!, 5560, "client1")
    Measurement.log(client1.id, -1, "Started at", clientLoc[3].first, null)
    sleepNoLog(2000, 0)
    val reqId1 = RequestID(1, "client1", clientLoc[3].first)
    val res: Pair<FunctionMessage?, Long> = client1.call("sieve", "", reqId1)
    if(res.first != null) Measurement.log("client1", res.second, "Done", clientLoc[3].second.distanceKmTo(res.first!!.responseTopicFence.fence.toGeofence().center).toString(), reqId1) // misc shows distance in km in Double format
    else client1.throwSafeException("client1-(1-${clientLoc[3].first}): NOOOOOOOOOOOOOOO Response! (${res.second}ms)")
    sleepNoLog(2000, 0)

    val reqId2 = RequestID(2, "client1", clientLoc[8].first)
    client1.moveTo(clientLoc[8], reqId2)
    sleepNoLog(2000, 0)
    val res2: Pair<FunctionMessage?, Long> = client1.call("sieve", "", reqId2)
    if(res2.first != null)  Measurement.log("client1", res2.second, "Done", clientLoc[3].second.distanceKmTo(res2.first!!.responseTopicFence.fence.toGeofence().center).toString(), reqId2) // misc shows distance in km in Double format
    else client1.throwSafeException("client1-(2-${clientLoc[8].first}): NOOOOOOOOOOOOOOO Response! (${res2.second}ms)")
    sleepNoLog(2000, 0)
    client1.shutdown()
    Measurement.close()

}