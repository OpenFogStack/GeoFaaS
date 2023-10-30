package geofaas

import de.hasenburg.geobroker.commons.model.disgb.BrokerInfo
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
        logger.info("calling '$funcName($data)'")
        val pubFence = Geofence.circle(location, radiusDegree)
        val subFence = Geofence.circle(location, radiusDegree) // subFence is better to be as narrow as possible (if the client is not moving, zero)
        // Subscribe for the response
        val subTopics: MutableSet<ListeningTopic>? = subscribeFunction(funcName, subFence)
        if (subTopics == null) // if success, it is either empty or contains newly subscribed functions
            logger.warn("Failed to subscribe to /result and /ack. Assume calling to a wrong broker, continuing...")

        // create Call message
        var result: FunctionMessage? = null
        val responseTopicFence = ResponseInfoPatched(id, Topic("functions/$funcName/result"), subFence.toJson())
        val message = gson.toJson(FunctionMessage(funcName, FunctionAction.CALL, data, TypeCode.NORMAL, "GeoFaaS", responseTopicFence))
        val messageRetryPayload = gson.toJson(FunctionMessage(funcName, FunctionAction.CALL, data, TypeCode.RETRY, "GeoFaaS", responseTopicFence))

        // call with retries
        val pubStatus = pubCallAndGetAck(message, funcName, pubFence, retries, messageRetryPayload, false)
        logger.debug("pubStatus dump: {}", pubStatus)

        // get the result
        when (pubStatus.first) { // Check the call Ack
            StatusCode.Success -> result = listenForResult(resTimeout)
            StatusCode.WrongBroker -> { // processPublishAckSuccess() returns WrongBroker only if there is a brokerInfo
                // get the result from the suggested broker, published to a wrong broker
                val changeStatus = changeBroker(pubStatus.second!!)
                if (changeStatus == StatusCode.Success) {
                    // TODO move all the subscriptions to the new broker? isn't needed now
                    val resultTopic = Topic("functions/$funcName/result")
                    val newSubscribeStatus: StatusCode = subscribe(resultTopic, subFence) // fast forward subscribeFunction() process
                    if(newSubscribeStatus == StatusCode.Failure)
                        throw RuntimeException("Failed to subscribe to '$resultTopic'. StatusCode: $newSubscribeStatus")

                    result = listenForResult(resTimeout)
                    if (result == null) { // only in the case of WrongBroker, do a retry if failed for result
                        logger.warn("retry Call with the new broker (${pubStatus.second!!.brokerId})")
                        val subAckTopic = subscribeFunction(funcName, subFence) // wasn't listening to ack. TODO: prettier wrongbroker handling?
                        if (subAckTopic != null) {
                            val retryPubStatus = pubCallAndGetAck(message, funcName, pubFence, retries, messageRetryPayload, false)
                            logger.debug("pubStatus dump: {}", retryPubStatus)
                            if (retryPubStatus.first == StatusCode.Success)
                                result = listenForResult(resTimeout)
                        }
                    }
                } else throw RuntimeException("Failed to switch the broker. StatusCode: $changeStatus" )
            }
            StatusCode.Failure -> {} // do nothing. will retry with the cloud directly
            else -> throw RuntimeException("Unexpected publish status '${pubStatus.first}'")
        }
        // Note: no retry for listening for Result

        // Cloud direct call
        if(result == null) {// anything but success
            logger.warn("No Result. Calling the cloud directly via 'functions/$funcName/call/retry'")
            when(val pubCloudStatus = retryCallByCloud(messageRetryPayload, funcName, pubFence)) {
                StatusCode.Success -> result = listenForResult(resTimeout)
                StatusCode.Failure -> {
                    logger.error("Can't call Cloud GeoFaaS for $funcName($data)")
                }
                else -> throw RuntimeException("Unexpected publish status from Cloud '${pubCloudStatus}'")
            }
        }

        // remove subscriptions
        val unSubscribedTopics = unsubscribeFunction(funcName)
        if (unSubscribedTopics.size > 0)
            logger.info("cleaned subscriptions for '$funcName' call (${unSubscribedTopics.size} in total)")
        else
            logger.error("problem with cleaning subscriptions after calling '$funcName'")

        return result
    }

    // Pub call, get PubAck, get CallAck, and retry
    private fun pubCallAndGetAck (message: String, funcName: String, pubFence: Geofence, retries: Int = 0, retryMessage: String, isRetry: Boolean = false): Pair<StatusCode, BrokerInfo?>{
        if(retries < 0)
            return Pair(StatusCode.Failure, null)
        val msg = if(isRetry) retryMessage else message //
        basicClient.send(Payload.PUBLISHPayload(Topic("functions/$funcName/call"), pubFence, msg))
        val pubStatus = listenForPubAckAndProcess(FunctionAction.CALL, funcName, ackTimeout)
        when (pubStatus.first){
            StatusCode.WrongBroker -> return pubStatus
            StatusCode.Success -> {
                // Wait for GeoFaaS's confirmation (Ack)
                var ackAttempts = getAckAttempts
                var ackSender :String?
                do {
                    ackSender = listenForAck(ackTimeout) // blocking, with timeout
                    ackAttempts--
                    if (ackSender == null)
                        logger.warn("attempts remained for getting an ACK: {}", ackAttempts)
                } while (ackSender == null && ackAttempts > 0) // retry

                if (ackSender != null){
                    return Pair(StatusCode.Success, null)
                } else { // retry
                    logger.warn("Retry Call. No Ack from GeoFaaS after calling '$funcName'. $retries retries remained...")
                    return pubCallAndGetAck(message, funcName, pubFence, retries - 1, retryMessage, true)
                }
            }
            else -> { // only 'Failure' is expected, but either way do retry
                logger.warn("Failed publishing '$funcName' call. $retries retries remained...")
                return pubCallAndGetAck(message, funcName, pubFence, retries - 1, retryMessage, true)
            }
        }
    }

    // For retry by cloud use cases. cloud is listening on 'f1/call/retry'
    private fun retryCallByCloud (messageRetryPayload: String, funcName: String, pubFence: Geofence): StatusCode {
        logger.warn("Calling the cloud directly for '$funcName' call...")
        basicClient.send(Payload.PUBLISHPayload(Topic("functions/$funcName/call/retry"), pubFence, messageRetryPayload))
        val pubStatus = listenForPubAckAndProcess(FunctionAction.CALL, funcName, ackTimeout)
        when (pubStatus.first) {
            StatusCode.WrongBroker -> return StatusCode.Failure // because it is unexpected for the cloud
            StatusCode.Success -> {
                // Wait for GeoFaaS Cloud's confirmation (Ack)
                var ackAttempts = getAckAttempts
                var ackSender :String?
                do {
                    ackSender = listenForAck(ackTimeout) // blocking, with timeout
                    ackAttempts--
                    if (ackSender == null)
                        logger.warn("attempts remained for getting the ack from Cloud GeoFaaS: {}", ackAttempts)
                } while (ackSender == null && ackAttempts > 0) // retry

                if (ackSender != null){
                    return StatusCode.Success
                } else {
                    logger.error("No Ack from Cloud GeoFaaS calling '$funcName'.")
                    return StatusCode.Failure
                }
            }
            StatusCode.Failure -> return StatusCode.Failure
            else -> { // unexpected
                logger.fatal("Unexpected StatusCode '${pubStatus.first}' for a PubAck when calling the cloud")
                return StatusCode.Failure
            }
        }
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
                if(ack.typeCode != TypeCode.NORMAL) logger.warn("'${ack.typeCode}' type for Ack is not handled! Ignoring it")
                logger.info("the Ack received from '{}'", ack.responseTopicFence.senderId)
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