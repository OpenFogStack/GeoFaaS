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

class ClientGBClient(loc: Location, debug: Boolean, host: String = "localhost", port: Int = 5559, id: String = "ClientGeoFaaS1", private val ackTimeout: Int = 8000, private val resTimeout: Int = 3000): GeoBrokerClient(loc, ClientType.CLIENT, debug, host, port, id) {
    private val logger = LogManager.getLogger()
    private val getAckAttempts = 2
    fun callFunction(funcName: String, data: String, retries: Int = 0, radiusDegree: Double = 0.1): FunctionMessage? {
        if(retries < 0) return null
        logger.info("calling '$funcName' function with following param: '$data'")
        val pubFence = Geofence.circle(location, radiusDegree)
        val subFence = Geofence.circle(location, radiusDegree) // subFence is better to be as narrow as possible (if the client is not moving, zero)
        // Subscribe for the response
        val subTopics: MutableSet<ListeningTopic>? = subscribeFunction(funcName, subFence)
        if (subTopics == null) // if success, it is either empty or contains newly subscribed functions
            logger.warn("Failed to subscribe to /result and /ack. Assume calling to a wrong broker, continuing...")

        // Call the function
        val responseTopicFence = ResponseInfoPatched(id, Topic("functions/$funcName/result"), subFence.toJson())
        val message = gson.toJson(FunctionMessage(funcName, FunctionAction.CALL, data, TypeCode.NORMAL, "GeoFaaS", responseTopicFence))
        basicClient.send(Payload.PUBLISHPayload(Topic("functions/$funcName/call"), pubFence, message))
        val pubStatus = listenForPubAckAndProcess(FunctionAction.CALL, funcName, ackTimeout)
        if (pubStatus.first != StatusCode.Success && pubStatus.first != StatusCode.WrongBroker) return null

        var res: FunctionMessage? = null
        when (pubStatus.first) {
            StatusCode.WrongBroker -> { // published to a wrong broker
                val changeStatus = changeBroker(pubStatus.second!!) // processPublishAckSuccess() returns WrongBroker only if there is a brokerInfo
                if (changeStatus == StatusCode.Success) {
                    // TODO move all the subscriptions to the new broker? isn't needed now
                    val resultTopic = Topic("functions/$funcName/result")
                    val newSubscribeStatus: StatusCode = subscribe(resultTopic, subFence) // fast forward subscribeFunction() process
                    if(newSubscribeStatus == StatusCode.Failure)
                        throw RuntimeException("Failed to subscribe to '$resultTopic'. StatusCode: $newSubscribeStatus")
                    res = listenForResult(resTimeout)
                } else
                    throw RuntimeException("Failed to switch the broker. StatusCode: $changeStatus" )
            }
            else -> {
                // Wait for GeoFaaS's response (Ack)
                var getAckAttempts = getAckAttempts
                var ackSender :String?
                do {
                    ackSender = listenForAck(ackTimeout) // blocking, with timeout
                    getAckAttempts--
                    if (ackSender == null)
                        logger.info("attempts remained for getting the ack: {}", getAckAttempts)
                } while (ackSender == null && getAckAttempts > 0) // retry

                // wait for the result from the GeoFaaS
                if (ackSender != null){
                    res = listenForResult(resTimeout)
                }
            }
        }

        val unSubscribedTopics = unsubscribeFunction(funcName)
        if (unSubscribedTopics.size > 0)
            logger.info("cleaned subscriptions for '$funcName' call (${unSubscribedTopics.size} in total)")
        else
            logger.error("problem with cleaning subscriptions after calling '$funcName'")

        return if (res == null){
            logger.warn("Retrying '$funcName($data)' call")
            callFunction(funcName, data, retries -1, radiusDegree)
        } else
            res
    }

    // Returns the ack's sender id or null
    private fun listenForAck(timeout: Int): String? {
        // Wait for GeoFaaS's response
        var ack: FunctionMessage?
        do {
            ack = listenForFunction("ACK", timeout)
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
            logger.error("Expected an ACK in ${timeout}ms, but null response received from GeoBroker!")
            return null
        }
    }
    private fun listenForResult(timeout: Int): FunctionMessage? {
        var res: FunctionMessage?
        var resultCounter = 0
        do {
            res = listenForFunction("RESULT", timeout)
            resultCounter++
        } while (res != null && res.receiverId != id)
        logger.info("{} Message(s) processed when listening for the result", resultCounter)

        if(res?.funcAction == FunctionAction.RESULT) return res
        else logger.error("Expected a RESULT, but received: {}", res)
        return null
    }
}