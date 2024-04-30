package geofaas

import de.hasenburg.geobroker.commons.model.message.Payload
import de.hasenburg.geobroker.commons.model.message.Topic
import de.hasenburg.geobroker.commons.model.spatial.Geofence
import de.hasenburg.geobroker.commons.model.spatial.Location
import de.hasenburg.geobroker.commons.model.spatial.toJson
import geofaas.Model.FunctionMessage
import geofaas.Model.FunctionAction
import geofaas.Model.TypeCode
import geofaas.Model.ResponseInfoPatched
import geofaas.Model.GeoFaaSFunction
import geofaas.Model.ListeningTopic
import geofaas.Model.ClientType
import geofaas.Model.StatusCode
import geofaas.Model.RequestID
import org.apache.logging.log4j.LogManager
import java.util.concurrent.atomic.AtomicInteger

class ServerGBClient(loc: Location, debug: Boolean, host: String = "localhost", port: Int = 5559, id: String = "GeoFaaSServerTest", mode: ClientType, val brokerAreaManager: BrokerAreaManager) :GeoBrokerClient(loc, mode, debug, host, port, id) {
    private val logger = LogManager.getLogger()
    private val brokerArea: Geofence = brokerAreaManager.ownBrokerArea.coveredArea //Note: parsing geoFaaS's brokerAreaManager Geofence to geobroker Geofence
    var receivedPubs = AtomicInteger(); var receivedHandshakes = AtomicInteger()
    // to asynchronously fill the queues with new messages
    fun asyncListen() { // blocking
        when (val newMessage = basicClient.receive()) {
            is Payload.PUBLISHPayload -> {
                pubQueue.add(newMessage)
                logger.debug("incoming Pub added to pubQueue. dump: {}", newMessage)
                receivedPubs.incrementAndGet()
            }
            is Payload.PUBACKPayload -> {
                logger.debug("drop incoming PUBACKPayload on the server")
                receivedHandshakes.incrementAndGet()
            }
            else -> {
                ackQueue.add(newMessage)
                logger.debug("incoming msg added to ackQueue. dump: {}", newMessage)
                receivedHandshakes.incrementAndGet()
            }
        }
    }

    // publishes result for a function request
    fun sendResult(funcName: String, res: String, clientFence: Geofence, reqId: RequestID, isOffload: Boolean = false) {
        val type = if(isOffload) TypeCode.OFFLOAD else TypeCode.NORMAL
        val responseTopicFence = ResponseInfoPatched(id, null, brokerArea.toJson()) // null topic = not listening for any response
        val message = FunctionMessage(reqId, funcName, FunctionAction.RESULT, res, type, reqId.clientId, responseTopicFence)
        basicClient.send(Payload.PUBLISHPayload(Topic("functions/$funcName/result"), clientFence, gson.toJson(message)))
//        val pubStatus = listenForPubAckAndProcess(FunctionAction.RESULT, funcName, 8000)
//        if (pubStatus.first != StatusCode.Success)
//            logger.error("Unsuccessful sending Result to ${reqId.clientId}. StatusCode: {}", pubStatus.first)
    }
    // publishes an Acknowledgement for receiving a client's request. the client listening for it
    fun sendAck(funcName: String, clientFence: Geofence, reqId: RequestID) {
        val responseTopicFence = ResponseInfoPatched(id,null, brokerArea.toJson())
        val message = FunctionMessage(reqId, funcName, FunctionAction.ACK, "", TypeCode.NORMAL, reqId.clientId, responseTopicFence)
        basicClient.send(Payload.PUBLISHPayload(Topic("functions/$funcName/ack"), clientFence, gson.toJson(message)))
//        val pubStatus = listenForPubAckAndProcess(FunctionAction.ACK, funcName, 8000)
//        if (pubStatus.first != StatusCode.Success)
//            logger.error("Unsuccessful sending Ack to ${reqId.clientId}. StatusCode: {}", pubStatus.first)
    }

    // publishes a NotAck to offload to the cloud. the cloud listening for it
    fun sendNack(funcName: String, data: String, clientFence: Geofence, reqId: RequestID, cloudId: String, cloudFence: Geofence = Geofence.world()) {
        val responseTopicFence = ResponseInfoPatched(reqId.clientId, Topic("functions/$funcName/result"), clientFence.toJson())
        val message = FunctionMessage(reqId, funcName, FunctionAction.NACK, data, TypeCode.NORMAL, cloudId, responseTopicFence)
        basicClient.send(Payload.PUBLISHPayload(Topic("functions/$funcName/nack"), cloudFence, gson.toJson(message)))
//        val pubStatus = listenForPubAckAndProcess(FunctionAction.NACK, funcName, 8000)
//        if (pubStatus.first == StatusCode.Failure) logger.error("failed to offload $funcName call by ${reqId.clientId}. Is $cloudId online?")
//        else if(pubStatus.first != StatusCode.Success)
//            logger.error("Unsuccessful sending Nack to ${reqId.clientId}. StatusCode: {}", pubStatus)
    }

    fun registerFunctions(functions: Set<GeoFaaSFunction>, fence: Geofence): StatusCode { //FIXME: should update CALL subscriptions in geoBroker when remote FaaS added/removed serving function
        val subscriptions: Map<String, List<String>> = subscribedFunctionsList()
        val callSubs = subscriptions.filter { it.value.contains("call") } // assume Cloud either subscribed to both Nack & Call or none
//        val nackSubs = subscriptions.filter { it.value.contains("nack") }

        when (mode){
            ClientType.EDGE, ClientType.CLOUD -> {
                functions.forEach { func ->
                    if (callSubs[func.name]?.contains("call") == true) {
                        logger.debug("$id already subscribed to '${func.name}' function calls")
                    } else {
                        val sub: MutableSet<ListeningTopic>? = subscribeFunction(func.name, fence)
                        if (sub != null) {
                            logger.info("new function '${func.name}' has registered to the $id, and will be served for the new requests")
                        } else {
                            logger.fatal("failed to register the '${func.name}' function to the $id; mode:$mode!")
                            return StatusCode.Failure
                        }
                    }
                }
                return StatusCode.Success
            }
            else -> {
                logger.fatal("Unexpected! '$id' of type '$mode' can't access registerFunction()")
                return StatusCode.Failure
            }
        }
    }
}