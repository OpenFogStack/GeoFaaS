package geofaas

import de.hasenburg.geobroker.commons.model.message.Payload
import de.hasenburg.geobroker.commons.model.message.Topic
import de.hasenburg.geobroker.commons.model.spatial.Geofence
import de.hasenburg.geobroker.commons.model.spatial.Location
import de.hasenburg.geobroker.commons.model.spatial.toJson
import geofaas.Model.ClientType
import geofaas.Model.FunctionAction
import geofaas.Model.FunctionMessage
import geofaas.Model.TypeCode
import geofaas.Model.ListeningTopic
import geofaas.Model.ResponseInfoPatched
import geofaas.Model.StatusCode
import org.apache.logging.log4j.LogManager

class ClientGBClient(loc: Location, debug: Boolean, host: String = "localhost", port: Int = 5559, id: String = "ClientGeoFaaS1"): GeoBrokerClient(loc, ClientType.CLIENT, debug, host, port, id) {
    private val logger = LogManager.getLogger()
    fun callFunction(funcName: String, data: String, radiusDegree: Double = 0.1): FunctionMessage? {
        logger.info("calling '$funcName' function with following param: '$data'")
        val pubFence = Geofence.circle(location, radiusDegree)
        val subFence = Geofence.circle(location, radiusDegree) // subFence is better to be as narrow as possible (if the client is not moving, zero)
        // Subscribe for the response
        val subTopics: MutableSet<ListeningTopic>? = subscribeFunction(funcName, subFence)
        if (subTopics == null) // if success, it is either empty or contains newly subscribed functions
            logger.warn("Failed to subscribe to /result and /ack. Could be a wrong broker, continuing...")

        // Call the function
        val responseTopicFence = ResponseInfoPatched(id, Topic("functions/$funcName/result"), subFence.toJson())
        val message = gson.toJson(FunctionMessage(funcName, FunctionAction.CALL, data, TypeCode.NORMAL, "GeoFaaS", responseTopicFence))
        gbSimpleClient.send(Payload.PUBLISHPayload(Topic("functions/$funcName/call"), pubFence, message))
        val pubAck = gbSimpleClient.receiveWithTimeout(8000) // TODO: replace with 'listenForPubAckAndProcess()'
        val pubStatus = processPublishAckSuccess(pubAck, funcName, FunctionAction.CALL, true)
        if (pubStatus != StatusCode.Success && pubStatus != StatusCode.WrongBroker) return null

        var res: FunctionMessage? = null
        when (pubStatus) {
            StatusCode.WrongBroker -> { // published to a wrong broker
                if(pubAck is Payload.DISCONNECTPayload) { // smart cast not available
                    val changeStatus = changeBroker(pubAck.brokerInfo!!) // processPublishAckSuccess() returns WrongBroker only if there is a brokerInfo
                    if (changeStatus == StatusCode.Success) {
                        // TODO move all the subscriptions to the new broker? isn't needed now
                        val newSubscribeStatus = subscribe(Topic("functions/$funcName/result"), subFence)
                        if(newSubscribeStatus == StatusCode.Failure)
                            throw RuntimeException("Failed to subscribe to the functions/$funcName/result" )
                        var functionMessagesCounter = 0
                        while (res == null){
                            res = listenForResult()
                            functionMessagesCounter++
                            if(res == null)
                                logger.debug("not a Result yet. {} Message(s) processed", functionMessagesCounter)
                        }
                        logger.info("{} 'Function Message(s)' processed when listening for the result", functionMessagesCounter)
                    } else
                        throw RuntimeException("Failed to switch the broker. StatusCode: $changeStatus" )
                }
            }
            else -> {
                // Wait for GeoFaaS's response (Ack)
                var retryCount = 3
                var ackSender :String?
                do {
                    ackSender = listenForAck(8500) // blocking
                    if (ackSender == null)
                        logger.info("attempts remained for getting the ack: {}", retryCount - 1)
                    retryCount--
                } while (ackSender == null && retryCount > 0) // retry

                // wait for the result from the GeoFaaS
                if (ackSender != null){
                    var functionMessagesCounter = 0
                    while (res == null){
                        res = listenForResult()
                        functionMessagesCounter++
                        if(res == null)
                            logger.debug("not a Result yet. {} Message(s) processed", functionMessagesCounter)
                    }
                    logger.info("{} 'Function Message(s)' processed when listening for the result", functionMessagesCounter)
                }
            }
        }

        val unSubscribedTopics = unsubscribeFunction(funcName)
        if (unSubscribedTopics.size > 0)
            logger.info("cleaned subscriptions for '$funcName' call (${unSubscribedTopics.size} in total)")
        else
            logger.error("problem with cleaning subscriptions after calling '$funcName'")
        return res
//        logger.error("Call function failed! Failed to subscribe to /result and /ack")
//        return null
    }

    private fun listenForAck(ackTimeout: Int): String? {
        // Wait for GeoFaaS's response
        var ack: FunctionMessage?
        do {
            ack = listenForFunction("ACK", ackTimeout)
        } while (ack != null && ack.receiverId != id) // post-process receiver-id. as in pub/sub you may also need messages with other receiver ids

        if (ack != null) {
            if (ack.funcAction == FunctionAction.ACK) {
                if(ack.typeCode == TypeCode.PIGGY) logger.warn("Piggybacked Ack not handled! Ignoring it")
                logger.info("the Ack received from '{}': {}", ack.responseTopicFence.senderId, ack)
                return ack.responseTopicFence.senderId
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
        var res: FunctionMessage?
        do {
            res = listenForFunction("RESULT", 0)
        } while (res != null && res.receiverId != id)

        if(res?.funcAction == FunctionAction.RESULT) return res
        else logger.error("Expected an RESULT, but received: {}", res)
        return null
    }
}