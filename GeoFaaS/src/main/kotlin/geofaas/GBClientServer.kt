package geofaas

import de.hasenburg.geobroker.commons.model.message.Payload
import de.hasenburg.geobroker.commons.model.message.Topic
import de.hasenburg.geobroker.commons.model.spatial.Geofence
import de.hasenburg.geobroker.commons.model.spatial.Location
import de.hasenburg.geobroker.commons.model.spatial.toJson
import geofaas.Model.FunctionMessage
import geofaas.Model.FunctionAction
import geofaas.Model.TypeCode
import geofaas.Model.ListeningTopicPatched

class GBClientServer(loc: Location, debug: Boolean, host: String = "localhost", port: Int = 5559, id: String = "GeoFaaSServerTest", mode: Model.ClientType, val brokerAreaManager: BrokerAreaManager) :GeoBrokerClient(loc, mode, debug, host, port, id) {
    private val brokerArea: Geofence = brokerAreaManager.ownBrokerArea.coveredArea //Note: parsing geoFaaS's brokerAreaManager Geofence to geobroker Geofence
    // publishes result for a function request
    fun sendResult(funcName: String, res: String, clientFence: Geofence) {

        val responseTopicFence = ListeningTopicPatched(null, brokerArea.toJson()) // null topic = not listening for any response
        val message = FunctionMessage(funcName, FunctionAction.RESULT, res, TypeCode.NORMAL, responseTopicFence)
        remoteGeoBroker.send(Payload.PUBLISHPayload(Topic("functions/$funcName/result"), clientFence, gson.toJson(message))) // Json.encodeToString(FunctionMessage.serializer(), message)
        val pubAck = remoteGeoBroker.receive()
        val logMsg = "GeoBroker's 'Publish ACK' for the '$funcName' RESULT by $id: {}"
        if (pubAck is Payload.PUBACKPayload) {
            val noError = logPublishAck(pubAck, logMsg) // logs the reasonCode
            if (!noError) logger.debug("NOT HANDLED. Sending Result failed!")
        } else {
            logger.error("Expected PUBACK for the RESULT, but received: {}", pubAck)
        }
    }
    // publishes an Acknowledgement for receiving a client's request. the client listening for it
    fun sendAck(funcName: String, clientFence: Geofence) {
        val responseTopicFence = ListeningTopicPatched(null, brokerArea.toJson())
        val message = FunctionMessage(funcName, FunctionAction.ACK, "", TypeCode.NORMAL, responseTopicFence)
        remoteGeoBroker.send(Payload.PUBLISHPayload(Topic("functions/$funcName/ack"), clientFence, gson.toJson(message)))
        val pubAck = remoteGeoBroker.receive()
        val logMsg = "GeoBroker's 'Publish ACK' for the '$funcName' ACK by $id: {}"
        if (pubAck is Payload.PUBACKPayload) {
            val noError = logPublishAck(pubAck, logMsg) // logs the reasonCode
            if (!noError) logger.debug("NOT HANDLED. Sending Ack failed!")
        } else {
            logger.error("Expected PUBACK for the Ack, but received: {}", pubAck)
        }
    }

    // publishes a NotAck to offload to the cloud. the cloud listening for it
    fun sendNack(funcName: String, data: String, clientFence: Geofence, cloudFence: Geofence = Geofence.world()) {
        val responseTopicFence = ListeningTopicPatched(Topic("functions/$funcName/result"), clientFence.toJson())
        val message = FunctionMessage(funcName, FunctionAction.NACK, data, TypeCode.PIGGY, responseTopicFence)
        remoteGeoBroker.send(Payload.PUBLISHPayload(Topic("functions/$funcName/nack"), cloudFence, gson.toJson(message)))
        val pubAck = remoteGeoBroker.receive()
        val logMsg = "GeoBroker's 'Publish ACK' for the '$funcName' NACK by $id: {}"
        if (pubAck is Payload.PUBACKPayload) {
            val noError = logPublishAck(pubAck, logMsg) // logs the reasonCode
            if (!noError) logger.debug("NOT HANDLED. Sending NACK failed!")
        } else {
            logger.error("Expected PUBACK for the Nack, but received: {}", pubAck)
        }
    }
}