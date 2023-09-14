package geofaas

import de.hasenburg.geobroker.commons.model.message.Payload
import de.hasenburg.geobroker.commons.model.message.ReasonCode
import de.hasenburg.geobroker.commons.model.message.Topic
import de.hasenburg.geobroker.commons.model.spatial.Geofence
import de.hasenburg.geobroker.commons.model.spatial.Location
import de.hasenburg.geobroker.commons.model.spatial.toJson
import geofaas.Model.FunctionMessage
import geofaas.Model.FunctionAction
import geofaas.Model.TypeCode
import geofaas.Model.ListeningTopicPatched

class GBClientEdge(loc: Location, debug: Boolean, host: String = "localhost", port: Int = 5559, id: String = "GeoFaaSEdgeTest") :GeoBrokerClient(loc, Model.ClientType.EDGE, debug, host, port, id) {
    // publishes result for a function request
    private val brokerFence = Geofence.circle(location, 2.1)
    fun sendResult(funcName: String, res: String, clientFence: Geofence) {
        val responseTopicFence = ListeningTopicPatched(null, brokerFence.toJson()) // null topic = not listening for any response
        val message = FunctionMessage(funcName, FunctionAction.RESULT, res, TypeCode.NORMAL, responseTopicFence)
        remoteGeoBroker.send(Payload.PUBLISHPayload(Topic("functions/$funcName/result"), clientFence, gson.toJson(message))) // Json.encodeToString(FunctionMessage.serializer(), message)
        val msg = remoteGeoBroker.receive()
        logger.info("Received geoBroker's answer: {}", msg)
        if (msg is Payload.PUBACKPayload) {
            if (msg.reasonCode == ReasonCode.NoMatchingSubscribers) {
                logger.error("No matching subscriber found when sending the result for the function '$funcName'")
            } else {
                logger.info("result sent with reasonCode: {}", msg.reasonCode)
            }
        }
    }
    // publishes an Acknowledgement for receiving a client's request. the client listens to it
    fun sendAck(funcName: String, clientFence: Geofence) {
        val responseTopicFence = ListeningTopicPatched(null, brokerFence.toJson())
        val message = FunctionMessage(funcName, FunctionAction.ACK, "", TypeCode.NORMAL, responseTopicFence)
        remoteGeoBroker.send(Payload.PUBLISHPayload(Topic("functions/$funcName/ack"), clientFence, gson.toJson(message)))
        val msg = remoteGeoBroker.receive()
        logger.info("Received geoBroker's answer for publishing ack ACK: {}", msg)
        // TODO: check if reasonCode is okay (similar to sendResult). log error if not. reasonCode=NoMatchingSubscribers
    }

    // publishes a NotAck to offload to the cloud
    fun sendNack(funcName: String, data: String, clientFence: Geofence, cloudFence: Geofence) {
        val responseTopicFence = ListeningTopicPatched(Topic("functions/$funcName/result"), clientFence.toJson())
        val message = FunctionMessage(funcName, FunctionAction.NACK, data, TypeCode.PIGGY, responseTopicFence)
        remoteGeoBroker.send(Payload.PUBLISHPayload(Topic("functions/$funcName/nack"), cloudFence, gson.toJson(message)))
        val msg = remoteGeoBroker.receive()
        logger.info("Received geoBroker's answer for publishing NACK: {}", msg)
        // TODO: check if reasonCode is okay. log error if not.
    }
}