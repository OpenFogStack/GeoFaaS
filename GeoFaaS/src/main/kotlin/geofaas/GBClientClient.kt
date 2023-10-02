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
import geofaas.Model.ResponseInfoPatched

class GBClientClient(loc: Location, debug: Boolean, host: String = "localhost", port: Int = 5559, id: String = "ClientGeoFaaS1"): GeoBrokerClient(loc, ClientType.CLIENT, debug, host, port, id) {
    fun callFunction(funcName: String, data: String, radiusDegree: Double): FunctionMessage? {
        val pubFence = Geofence.circle(location, radiusDegree)
        val subFence = Geofence.circle(location, radiusDegree)
        // Subscribe for the response
        val subTopics: MutableSet<ListeningTopic>? = subscribeFunction(funcName, subFence)
        if (subTopics != null) {
            // Call the function
            val responseTopicFence = ResponseInfoPatched(id, Topic("functions/$funcName/result"), subFence.toJson())
            val message = gson.toJson(FunctionMessage(funcName, FunctionAction.CALL, data, TypeCode.NORMAL, "GeoFaaS", responseTopicFence))
            remoteGeoBroker.send(Payload.PUBLISHPayload(Topic("functions/$funcName/call"), pubFence, message))
            val pubAck = remoteGeoBroker.receiveWithTimeout(3000)
            val pubSuccess = processPublishAckSuccess(pubAck, funcName, FunctionAction.CALL, true)
            if (!pubSuccess) return null

            // Wait for GeoFaaS's response (Ack)
            var retryCount = 5
            var ackSender :String?
            do {
                ackSender = listenForAck(3500) // blocking
                if (ackSender == null)
                    logger.info("attempts remained for getting the ack: {}", retryCount - 1)
                retryCount--
            } while (ackSender == null && retryCount > 0) // retry

            var res: FunctionMessage? = null
            // wait for the result from the GeoFaaS
            if (ackSender != null){
                var resultsCounter = 0
                while (res == null){
                    res = listenForResult()
                    resultsCounter++
                    if(res == null)
                        logger.debug("not a Result yet. {} Message(s) processed", resultsCounter)
                }
                logger.info("{} Message(s) processed when listening for the result", resultsCounter)
            }
            return res
            // Unsubscribe after receiving the response
            //TODO: Unsubscribe
        }
        logger.error("Call function failed! Failed to subscribe to /result and /ack")
        return null
    }

    private fun listenForAck(ackTimeout: Int): String? {
        // Wait for GeoFaaS's response
        val ack: FunctionMessage? = listenFor("ACK", ackTimeout)
        if (ack != null) {
            if (ack.funcAction == FunctionAction.ACK) {
                if(ack.typeCode == TypeCode.PIGGY) logger.warn("Piggybacked Ack not handled! Ignoring it")
                return if (ack.receiverId == id){
                    logger.info("the Ack received from '{}': {}", ack.responseTopicFence.senderId, ack)
                    ack.responseTopicFence.senderId
                } else {
                    logger.debug("the received Ack is not for me. Retrying... {}", ack)
                    null
                }
            } else {
                logger.error("expected an Ack but received '{}'", ack.funcAction)
                return null
            }
        } else {
            logger.error("Expected an ACK in ${ackTimeout}ms, but null response received from GeoBroker!")
            return null
        }
    }
    private fun listenForResult(): FunctionMessage? {
        val res: FunctionMessage? = listenFor("RESULT", 0)
        if(res?.funcAction == FunctionAction.RESULT) {
            if (res.receiverId == id)
                return res
            else
                logger.warn("the received Result is not for me. Retrying... {}", res)
        } else
            logger.error("Expected an RESULT, but received: {}", res)
        return null
    }
}


fun main() {
    val clientLoc = mapOf("middleButCloserToParis" to Location(50.289339,3.801270),
        "parisNonOverlap" to Location(48.719961,1.153564),
        "parisOverlap" to Location(48.858391, 2.327385), // overlaps with broker area but the broker is not inside the fence
        "paris2" to Location(48.835797,2.244301), // Boulogne Bilancourt area in Paris
        "parisEdge" to Location(48.877366, 2.359708),
        "frankfurtEdge" to Location(50.106732,8.663124),
        "potsdam" to Location(52.400953,13.060169),
        "franceEast" to Location(47.323931,5.174561),
        "amsterdam" to Location(52.315195,4.894409),
        "hamburg" to Location(53.527248,9.986572),
        "belgium" to Location(50.597186,4.822998))

    val client1 = GBClientClient(clientLoc["franceEast"]!!, true, "141.23.28.207", 5560)
    val res: FunctionMessage? = client1.callFunction("sieve", "", 2.1)
    if(res != null) println("Result: ${res.data}")
    sleepNoLog(2000, 0)
    client1.terminate()
}