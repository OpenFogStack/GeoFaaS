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
import geofaas.Model.RequestID
import geofaas.experiment.Measurement
import org.apache.logging.log4j.LogManager

class ClientGBClient(loc: Location, debug: Boolean, host: String = "localhost", port: Int = 5559, id: String = "ClientGeoFaaS1", private val ackTimeout: Int = 8000, private val resTimeout: Int = 3000): GeoBrokerClient(loc, ClientType.CLIENT, debug, host, port, id) {
    private val logger = LogManager.getLogger()
//    private val getAckAttempts = 2

    fun callFunction(funcName: String, data: String, retries: Int = 0, ackAttempts: Int = 0, radiusDegree: Double = 0.1, reqId: RequestID, isWithCloudRetry: Boolean = true, isContinousCall: Boolean = false): FunctionMessage? {
        logger.info("calling '$funcName($data)'")
        val pubFence = Geofence.circle(location, radiusDegree)
        val subFence = Geofence.circle(location, radiusDegree) // subFence is better to be as narrow as possible (if the client is not moving, zero)

        // Subscribe for the response
        if (isSubscribedTo("functions/$funcName/result")){ // skip if already subscribed
            logger.debug("already subscribed to $funcName results, skipping subscription")
        } else {
            val subTopics: MutableSet<ListeningTopic>? = Measurement.logRuntime(id, "SubFunction", "${location.lat}:${location.lon}", reqId){
                subscribeFunction(funcName, subFence)
            }
            if (subTopics == null) // if success, it is either empty or contains newly subscribed functions
                logger.warn("Failed to subscribe to /result and /ack. Assume calling to a wrong broker, continuing...")
        }
        // create Call message
        var result: FunctionMessage? = null
        val responseTopicFence = ResponseInfoPatched(id, Topic("functions/$funcName/result"), subFence.toJson())
        val message = gson.toJson(FunctionMessage(reqId, funcName, FunctionAction.CALL, data, TypeCode.NORMAL, "GeoFaaS", responseTopicFence))
        val messageRetryPayload = gson.toJson(FunctionMessage(reqId, funcName, FunctionAction.CALL, data, TypeCode.RETRY, "GeoFaaS", responseTopicFence))

        // call with retries (includes receiving GF Ack)
        val pubStatus = pubCallAndGetAck(message, funcName, pubFence, retries, ackAttempts, messageRetryPayload, reqId, false)
        logger.debug("pubStatus dump: {}", pubStatus)

        // get the result
        when (pubStatus.first) { // Check the call Ack
            StatusCode.Success -> result = listenForResult(resTimeout, reqId)
            StatusCode.WrongBroker -> { // processPublishAckSuccess() returns WrongBroker only if there is a brokerInfo
                // get the result from the suggested broker, published to a wrong broker
                val changeStatus = Measurement.logRuntime(id, "WrongBroker", "SwitchedTo;${pubStatus.second!!.brokerId}", reqId){
                    changeBroker(pubStatus.second!!)
                }
                if (changeStatus == StatusCode.Success) { // TODO move all the subscriptions to the new broker? isn't needed now
                    val resultTopic = Topic("functions/$funcName/result")
                    val newSubscribeStatus: StatusCode = subscribe(resultTopic, subFence) // fast forward subscribeFunction() process
                    if(newSubscribeStatus == StatusCode.Failure)
                        throwSafeException("Failed to subscribe to '$resultTopic'. StatusCode: $newSubscribeStatus")

                    result = listenForResult(resTimeout, reqId)
                    if (result == null) { // Note: only in the case of WrongBroker, do a retry if failed for the result
                        logger.warn("retry Call with the new broker (${pubStatus.second!!.brokerId})")
                        Measurement.log(id, -1, "RESULT;NoResult", "Retry;NewBroker", reqId)
                        val subAckTopic = subscribeFunction(funcName, subFence) // wasn't listening to ack. TODO: prettier wrongbroker handling?
                        if (subAckTopic != null) {
                            val retryPubStatus = pubCallAndGetAck(messageRetryPayload, funcName, pubFence, retries, ackAttempts, messageRetryPayload, reqId, true)
//                            Measurement.logRuntime(id, "PubCall;WrongBroker", "retry;retry=$retries;${location.lat}:${location.lon}"){
//                            }
                            logger.debug("pubStatus dump: {}", retryPubStatus)
                            if (retryPubStatus.first == StatusCode.Success)
                                result = listenForResult(resTimeout, reqId)
                        }
                    }
                } else throwSafeException("$id failed to switch the broker. StatusCode: $changeStatus" )
            }
            StatusCode.Failure -> {} // do nothing. will retry with the cloud directly
            else -> throwSafeException("Unexpected publish status '${pubStatus.first}'")
        }
        // Note: no retry for listening for Result (after receiving an ack)

        // Cloud direct call
        if(result == null) {// anything but success
            if(isWithCloudRetry){
                logger.warn("No Result. Calling the cloud directly via 'functions/$funcName/call/retry'")
                when(val pubCloudStatus = retryCallByCloud(messageRetryPayload, funcName, pubFence, ackAttempts, reqId)) {
                    StatusCode.Success -> result = listenForResult(resTimeout, reqId)
                    StatusCode.Failure -> {
                        logger.error("Can't call Cloud GeoFaaS for $funcName($data)")
                    }
                    else -> throwSafeException("Unexpected publish status from Cloud '${pubCloudStatus}'")
                }
            } else {
                logger.warn("No Result. NO cloud direct retry")
            }
        }

        // remove subscriptions
        if(!isContinousCall) {
            val unSubscribedTopics = unsubscribeFunction(funcName)
            if (unSubscribedTopics.size > 0)
                logger.info("cleaned subscriptions for '$funcName' call (${unSubscribedTopics.size} in total)")
            else
                logger.error("problem with cleaning subscriptions after calling '$funcName'")
        }

        logger.debug("clearing pubQueue ({} messages) and ackQueue ({} messages)", pubQueue.size, ackQueue.size)
        pubQueue.clear(); ackQueue.clear()
        return result
    }

    // Pub call, get PubAck, get CallAck, and retry
    private fun pubCallAndGetAck (message: String, funcName: String, pubFence: Geofence, retries: Int, ackAttempts: Int, retryMessage: String, reqId: RequestID, isRetry: Boolean = false): Pair<StatusCode, BrokerInfo?>{
        if(retries < 0)
            return Pair(StatusCode.Failure, null)
        val msg = if(isRetry) retryMessage else message //
        val pubStatus = Measurement.logRuntime(id, "Published" + if(isRetry) ";Retry" else "", "CALL", reqId){
            basicClient.send(Payload.PUBLISHPayload(Topic("functions/$funcName/call"), pubFence, msg))
            listenForPubAckAndProcess(FunctionAction.CALL, funcName, ackTimeout)
        }
        when (pubStatus.first){
            StatusCode.WrongBroker -> return pubStatus
            StatusCode.Success -> {
                // Wait for GeoFaaS's confirmation (Ack)
                var ackAttmptCountDown: Int = ackAttempts
                var ackSender :String? = null
                Measurement.logRuntime(id, "ACK;received", "timeout=$ackTimeout", reqId){
                    do {
                        ackSender = listenForAck(ackTimeout, reqId) // blocking, with timeout
                        ackAttmptCountDown--
                        if (ackSender == null)
                            logger.warn("attempts remained for getting an ACK: {}", ackAttmptCountDown)
                    } while (ackSender == null && ackAttmptCountDown > 0) // retry
                }

                if (ackSender != null){
                    return Pair(StatusCode.Success, null)
                } else { // retry
                    if (retries > 0)
                        logger.warn("Retry Call. No Ack from GeoFaaS after calling '$funcName'. $retries retries remained...")
                    else
                        logger.error("No Ack from GeoFaaS after calling '$funcName'. NO retries remained...")
                    Measurement.log(id, -1, "ACK;NoACK", "Retry;Pub;$retries retries remained", reqId)
//                    val pubQSize = pubQueue.size; pubQueue.clear()
//                    if (pubQSize > 0) logger.warn("cleared pubQueue with {} pubs", pubQSize)
                    return pubCallAndGetAck(message, funcName, pubFence, retries - 1, ackAttempts, retryMessage, reqId, true)
                }
            }
            else -> { // only 'Failure' is expected, but either way do retry
                logger.warn("Failed publishing '$funcName' call. $retries retries remained...")
                Measurement.log(id, -1, "Retry;NotPublished", "Retry;Pub;$retries retries remained", reqId)
                return pubCallAndGetAck(message, funcName, pubFence, retries - 1, ackAttempts, retryMessage, reqId, true)
            }
        }
    }

    // For retry by cloud use cases. cloud is listening on 'f1/call/retry'
    private fun retryCallByCloud (messageRetryPayload: String, funcName: String, pubFence: Geofence, ackAttempts: Int, reqId: RequestID): StatusCode {
        logger.warn("Calling the cloud directly for '$funcName' call...")
        val pubStatus = Measurement.logRuntime(id, "Published", "Cloud;Retry", reqId){
            basicClient.send(Payload.PUBLISHPayload(Topic("functions/$funcName/call/retry"), pubFence, messageRetryPayload))
            listenForPubAckAndProcess(FunctionAction.CALL, funcName, ackTimeout)
        }
        when (pubStatus.first) {
            StatusCode.WrongBroker -> return StatusCode.Failure // because it is unexpected for the cloud
            StatusCode.Success -> {
                // Wait for GeoFaaS Cloud's confirmation (Ack)
                var ackAttmptCountDown = ackAttempts
                var ackSender :String? = null
                Measurement.logRuntime(id, "Retry;Cloud;Ack", "GeoFaaS Ack", reqId){
                    do {
                        ackSender = listenForAck(ackTimeout, reqId) // blocking, with timeout
                        ackAttmptCountDown--
                        if (ackSender == null)
                            logger.warn("attempts remained for getting the ack from Cloud GeoFaaS: {}", ackAttmptCountDown)
                    } while (ackSender == null && ackAttmptCountDown > 0) // retry
                }
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
    private fun listenForAck(timeout: Int, reqId: RequestID): String? {
        // Wait for GeoFaaS's response
        var ack: FunctionMessage?
        do {
            ack = listenForFunction("ACK", timeout, reqId)
        } while (ack != null && ack.reqId != reqId) // post-process receiver-id. as in pub/sub you may also need messages with other receiver ids

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
    private fun listenForResult(timeout: Int, reqId: RequestID): FunctionMessage? {
        var res: FunctionMessage? = null
        var resultCounter = 0
        Measurement.logRuntime(id, "RESULT;Received", "timeout=$timeout", reqId){
            do {
                res = listenForFunction("RESULT", timeout, reqId)
                resultCounter++
            } while (res != null && res!!.reqId != reqId)
        }
        logger.info("{} Message(s) processed when listening for the result", resultCounter)

        if(res?.funcAction == FunctionAction.RESULT) return res
        else logger.error("Expected a RESULT, but received: {}", res)
        return null
    }
}