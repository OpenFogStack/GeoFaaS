package geofaas

import de.hasenburg.geobroker.commons.model.message.Payload
import de.hasenburg.geobroker.commons.model.message.ReasonCode
import de.hasenburg.geobroker.commons.model.message.Topic
import de.hasenburg.geobroker.commons.model.spatial.Geofence
import de.hasenburg.geobroker.commons.model.spatial.Location
import org.apache.logging.log4j.LogManager
import geofaas.Model.FunctionMessage
import geofaas.Model.FunctionAction
import geofaas.Model.TypeCode

class GBClientEdge(loc: Location, debug: Boolean, host: String = "localhost", port: Int = 5559, id: String = "GeoFaaSEdgeTest") :GeoBrokerClient(loc, Model.ClientType.EDGE, debug, host, port, id) {
    // publishes result for a function request
    fun sendResult(funcName: String, res: String) { //TODO: what location to send the result?
        val message = FunctionMessage(funcName, FunctionAction.RESULT, res, TypeCode.NORMAL)
        remoteGeoBroker.send(Payload.PUBLISHPayload(Topic("functions/$funcName/result"),Geofence.circle(location,2.0), gson.toJson(message))) // Json.encodeToString(FunctionMessage.serializer(), message)
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
    fun sendAck(funcName: String) {
        val message = FunctionMessage(funcName, FunctionAction.ACK, "", TypeCode.NORMAL)
        remoteGeoBroker.send(Payload.PUBLISHPayload(Topic("functions/$funcName/ack"),Geofence.circle(location,2.0), gson.toJson(message)))
        val msg = remoteGeoBroker.receive()
        logger.info("Received geoBroker's answer for publishing ack ACK: {}", msg)
        // TODO: check if reasonCode is okay (similar to sendResult). log error if not. reasonCode=NoMatchingSubscribers
    }

    // publishes a NotAck to offload to the cloud
    fun sendNack(funcName: String, data: String) { //TODO piggyback the data to the nack
        val message = FunctionMessage(funcName, FunctionAction.NACK, data, TypeCode.PIGGY)
        remoteGeoBroker.send(Payload.PUBLISHPayload(Topic("functions/$funcName/nack"),Geofence.circle(location,2.0), gson.toJson(message)))
        val msg = remoteGeoBroker.receive()
        logger.info("Received geoBroker's answer for publishing NACK: {}", msg)
        // TODO: check if reasonCode is okay. log error if not.
    }
}