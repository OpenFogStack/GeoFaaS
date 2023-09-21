package geofaas

import de.hasenburg.geobroker.commons.model.message.Payload
import de.hasenburg.geobroker.commons.model.message.Topic
import de.hasenburg.geobroker.commons.model.spatial.Geofence
import de.hasenburg.geobroker.commons.model.spatial.Location
import de.hasenburg.geobroker.commons.model.spatial.toJson
import de.hasenburg.geobroker.commons.sleepNoLog
import geofaas.Model.ClientType
import geofaas.Model.FunctionAction
import geofaas.Model.FunctionMessage
import geofaas.Model.TypeCode
import geofaas.Model.ListeningTopic
import geofaas.Model.ListeningTopicPatched

class GBClientClient(loc: Location, debug: Boolean, host: String = "localhost", port: Int = 5559, id: String = "GeoFaaSClient1"): GeoBrokerClient(loc, ClientType.CLIENT, debug, host, port, id) {
    fun callFunction(funcName: String, data: String, pubFence: Geofence, subFence: Geofence): String? {
        // Subscribe for the response
        val subTopics: MutableSet<ListeningTopic>? = subscribeFunction(funcName, subFence)
        if (subTopics != null) {
            // Call the function
            val responseTopicFence = ListeningTopicPatched(Topic("functions/$funcName/result"), subFence.toJson())
            val message = gson.toJson(FunctionMessage(funcName, FunctionAction.CALL, data, TypeCode.NORMAL, responseTopicFence))
            remoteGeoBroker.send(Payload.PUBLISHPayload(Topic("functions/$funcName/call"), pubFence, message))
            val pubAck = remoteGeoBroker.receive()
            val logMsg = "GeoBroker's 'Publish ACK' for the '$funcName' CALL by $id: {}"
            if (pubAck is Payload.PUBACKPayload) {
                val noError = logPublishAck(pubAck, logMsg) // logs the reasonCode
                if (!noError) return null
            } else { logger.error("Unexpected! $logMsg", pubAck); return null }

            // Wait for GeoFaaS's response
            logger.info("Listening for an ack from the server...")
            val ack: FunctionMessage? = listen()
            var res: FunctionMessage? = null
            if (ack != null) {
                if (ack.funcAction == FunctionAction.ACK) {
                    logger.info("new Ack received")
                    when (ack.typeCode) {
                        TypeCode.NORMAL -> {
                            res = listen()
                        }
                        TypeCode.PIGGY -> {
                            return null //TODO: Implement if Ack is piggybacked
                        }
                    }
                } else { logger.error("expected Ack but received '{}'", ack.funcAction)}
                logger.debug("(any?) response received: {}", res)
                return res?.data
            } else { logger.error("null response received from GeoBroker! (Client.Listen())") }
            return null
            // Unsubscribe after receiving the response
            //TODO: Unsubscribe
        }
        logger.error("Call function failed! Failed to subscribe to /result and /ack")
        return null
    }
}

fun main() {
//    val closerToParisClientLoc = Location(50.289339,3.801270)
//    val belgiumClientLoc = Location(50.597186,4.822998)
//    val farClientLocNonOverlap = Location(47.323931,5.174561)
    val parisClientLocNonOverlap = Location(48.719961,1.153564)
//    val parisClientLoc = Location(48.858391, 2.327385)// overlaps with broker area but the broker is not inside the fence//Location(48.835797,2.244301) // Boulogne Bilancourt area in Paris
//    val parisEdgeLoc = Location(48.877366, 2.359708)
//    val frankfurtEdgeLoc = Location(50.106732,8.663124); val parisLoc = frankfurtLocOnly
    val client1 = GBClientClient(parisClientLocNonOverlap, true, "141.23.28.207", 5560)
    val res: String? = client1.callFunction("sieve", "", pubFence = Geofence.circle(parisClientLocNonOverlap, 2.1), subFence = Geofence.circle(parisClientLocNonOverlap, 2.1))
    if(res != null) println("Result: $res")
    sleepNoLog(2000, 0)
    client1.terminate()
}