package geofaas

import com.google.gson.Gson
import de.hasenburg.geobroker.commons.communication.ZMQProcessManager
import de.hasenburg.geobroker.commons.model.disgb.BrokerInfo
import de.hasenburg.geobroker.commons.model.message.Payload
import de.hasenburg.geobroker.commons.model.message.ReasonCode
import de.hasenburg.geobroker.commons.model.message.Topic
import de.hasenburg.geobroker.commons.model.spatial.Geofence
import de.hasenburg.geobroker.commons.model.spatial.Location
import de.hasenburg.geobroker.commons.setLogLevel
import geofaas.Model.FunctionMessage
import geofaas.Model.ListeningTopic
import geofaas.Model.ClientType
import geofaas.Model.FunctionAction
import geofaas.Model.StatusCode
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

// Basic Geobroker client for GeoFaaS system
abstract class GeoBrokerClient(var location: Location, val mode: ClientType, debug: Boolean, host: String = "localhost", port: Int = 5559, val id: String = "GeoFaaSAbstract") {
    private val logger = LogManager.getLogger()
    private val processManager = ZMQProcessManager()
    private var listeningTopics = mutableSetOf<ListeningTopic>()
    protected val ackQueue : BlockingQueue<Payload> = LinkedBlockingQueue<Payload>()
    val pubQueue : BlockingQueue<Payload> = LinkedBlockingQueue<Payload>()
    private val visitedBrokers = mutableSetOf<Pair<String, Int>>()
    var basicClient = GBBasicClient(host, port, identity = id) // NOTE: 'gbSimpleClient.identity' and 'GeoBrokerClient.id' are only same on the initialization and the former could change later
    val gson = Gson()
    init {
        if (debug) { setLogLevel(logger, Level.DEBUG) }
        basicClient.send(Payload.CONNECTPayload(location)) // connect
        var connAck = basicClient.receiveWithTimeout(8000)

        val connStatus = processConnAckSuccess(connAck, BrokerInfo("<Unknown>", host, port), true)
        if (connStatus == StatusCode.Failure) {
            if (connAck is Payload.DISCONNECTPayload) {
                if(connAck.brokerInfo == null) { // retry with suggested broker
                    if(connAck.reasonCode == ReasonCode.ProtocolError){
                        logger.warn("Duplicate id! $id can't connect to the remote geoBroker '$host:$port'. Retrying...")
                        basicClient.send(Payload.CONNECTPayload(location)) // connect
                        connAck = basicClient.receiveWithTimeout(8000)
                        //reconnect but ignore the connAck
                    } else {
                        if (connAck.reasonCode == ReasonCode.WrongBroker) // the connAck.Broker info is null
                            logger.fatal("No responsible broker found! while $id tried to connect to the remote geoBroker '$host:$port'.")
                        else
                            logger.fatal("Unexpected Error ${connAck.reasonCode}! $id can't connect to the remote geoBroker '$host:$port'.")
                        throwSafeException("Error while connecting to the geoBroker")
                    }
                }
            } else if (connAck == null) {
                throwSafeException("Timeout! can't connect to geobroker $host:$port. Check the Address and try again")
            } else {
                logger.fatal("Unexpected 'Conn ACK'! Received geoBroker's answer: {}", connAck)
                throwSafeException("Error while connecting to the geoBroker")
            }
        } else if (connStatus == StatusCode.WrongBroker && id.startsWith("GeoFaaS-")){
            if (connAck is Payload.DISCONNECTPayload) { // because smart cast doesn't happen here
                val newBrokerInfo = connAck.brokerInfo!! // TODO replace with 'changeBroker()' and do the retry
//            val changeIsSuccess = changeBroker(newBrokerInfo)
                logger.warn("Changed the remote broker to the suggested: $newBrokerInfo")
                basicClient = GBBasicClient(newBrokerInfo.ip, newBrokerInfo.port, identity = id)
                basicClient.send(Payload.CONNECTPayload(location)) // connect
                connAck = basicClient.receiveWithTimeout(8000)
                val connSuccess = processConnAckSuccess(connAck, newBrokerInfo, true)
                if (connSuccess != StatusCode.Success)
                    throwSafeException("Error connecting to the new geoBroker")
            }
        }
    }

    //Returns the new topics (w/ fence) listening to
    fun subscribeFunction(funcName: String, fence: Geofence): MutableSet<ListeningTopic>? {
        logger.debug("subscribeFunction() call. params:'{}', '{}'", funcName, fence)
        var newTopics: MutableSet<ListeningTopic> = mutableSetOf()
        var baseTopic = "functions/$funcName"
        baseTopic += when (mode) {
            ClientType.EDGE, ClientType.CLOUD   -> "/call"
            ClientType.CLIENT -> "/result"
        }
        val topic = Topic(baseTopic)
        val newSubscribe: StatusCode = subscribe(topic, fence)
        if (newSubscribe == StatusCode.Success) { newTopics.add(ListeningTopic(topic, fence)) }

        if (newSubscribe != StatusCode.Failure) {
            if (mode == ClientType.CLIENT) { // Client subscribes to two topics
                val ackTopic = Topic("functions/$funcName/ack")
                val ackSubscribe: StatusCode = subscribe(ackTopic, fence)
                if (ackSubscribe == StatusCode.Success) { newTopics.add(ListeningTopic(ackTopic, fence)) }
            }
            if (mode == ClientType.CLOUD) { // Cloud subscribes to three topics
                val nackTopic = Topic("functions/$funcName/nack")
                val nackSubscribe: StatusCode = subscribe(nackTopic, fence)
                if (nackSubscribe == StatusCode.Success) { newTopics.add(ListeningTopic(nackTopic, fence)) }
                val retryTopic = Topic("functions/$funcName/call/retry")
                val retrySubscribe: StatusCode = subscribe(retryTopic, fence)
                if (retrySubscribe == StatusCode.Success) { newTopics.add(ListeningTopic(retryTopic, fence)) }
            }
            return if (newTopics.isNotEmpty()) {
                logger.info("ListeningTopics appended by ${newTopics.size}: {}", newTopics.map { it.topic.topic })
                newTopics // for error handling purposes
            } else {
                logger.info("ListeningTopics didn't change. Nothing subscribed new!")
                mutableSetOf<ListeningTopic>()
            }
        } else
            return null // failure
    }

    // returns a map of function name to either call, result, or/and ack/nack
    fun subscribedFunctionsList(): Map<String, List<String>> {
        val functionCalls = listeningTopics.map { pair -> pair.topic.topic }//.filter { it.endsWith("/call") }
        logger.debug("functions that $id already listening to: {}", functionCalls)
        return functionCalls.map { val partialTopic = it.substringAfter("/").split("/");
        listOf(partialTopic.first(), partialTopic[1])}.groupBy { it.first() }.mapValues { it.value.map { pair -> pair[1] } }// take name of function and the action between '/', e.g. functions/"f1/call"
    }
    // returns three states: "success", null (failure), or "already exist"
    fun subscribe(topic: Topic, fence: Geofence): StatusCode {
        if (!isSubscribedTo(topic.topic)) {
            basicClient.send(Payload.SUBSCRIBEPayload(topic, fence))
            val subAck = if (ackQueue.peek() is Payload.SUBACKPayload) ackQueue.remove() else basicClient.receiveWithTimeout(8000)
            return if (subAck is Payload.SUBACKPayload){
                if (subAck.reasonCode == ReasonCode.GrantedQoS0){
                    logger.debug("$id successfully subscribed to '${topic.topic}' in $fence. details: {}", subAck)
                    listeningTopics.add(ListeningTopic(topic, fence)) // register locally
                    StatusCode.Success
                } else {
                    logger.error("Error Subscribing to '${topic.topic}' by $id. Reason: {}.", subAck.reasonCode)
                    StatusCode.Failure
                }
            } else if(subAck is Payload.PUBLISHPayload) {
                val message = gson.fromJson(subAck.content, FunctionMessage::class.java)
                if (message.receiverId == id) {
                    pubQueue.add(subAck)
                    logger.warn("Not a subAck ! adding it to the 'pubQueue'. dump: {}", subAck)
                }
                return subscribe(topic, fence) // it is okay to send SubscribePayload again...
            } else if(subAck == null) {
                logger.error("Error subscribing to '${topic.topic}' by $id. No response from the broker")
                return StatusCode.Failure
            } else {
                ackQueue.add(subAck)
                logger.warn("Not a SubAck! adding it to the 'ackQueue'. dump: {}", subAck)
                return subscribe(topic, fence)
            }
        } else {
            logger.warn("already subscribed to the '${topic.topic}'")
            return  StatusCode.AlreadyExist // not a failure
        }
    }

    fun unsubscribeFunction(funcName: String) :MutableSet<Topic> {
        logger.debug("unsubscribeFunction() call. func:'{}'", funcName)
        var deletedTopics: MutableSet<Topic> = mutableSetOf()
        var baseTopic = "functions/$funcName"
        baseTopic += when (mode) {
            ClientType.EDGE, ClientType.CLOUD   -> "/call"
            ClientType.CLIENT -> "/result"
        }
        val topic = Topic(baseTopic)
        val unSubscribe: StatusCode = unsubscribe(topic)
        if (unSubscribe == StatusCode.Success) { deletedTopics.add(topic) }

        // unlike subscribeFunction() we won't abort if the first unsubscribe fails or doesn't exist
        if (mode == ClientType.CLIENT) { // Client subscribes to two topics
            val ackTopic = Topic("functions/$funcName/ack")
            val ackUnsubscribe: StatusCode = unsubscribe(ackTopic)
            if (ackUnsubscribe == StatusCode.Success) { deletedTopics.add(ackTopic) }
        }
        if (mode == ClientType.CLOUD) { // Cloud subscribes to three topics
            val nackTopic = Topic("functions/$funcName/nack")
            val nackUnsubscribe: StatusCode = unsubscribe(nackTopic)
            if (nackUnsubscribe == StatusCode.Success) { deletedTopics.add(nackTopic) }
            val retryTopic = Topic("functions/$funcName/call/retry")
            val retryUnsubscribe: StatusCode = unsubscribe(nackTopic)
            if (retryUnsubscribe == StatusCode.Success) { deletedTopics.add(retryTopic) }
        }
        return if (deletedTopics.isNotEmpty()) {
            logger.debug("ListeningTopics decreased by ${deletedTopics.size}: {}", deletedTopics.map { it.topic })
            deletedTopics // for error handling purposes
        } else {
            logger.info("Nothing unsubscribed new for '$funcName' function!")
            mutableSetOf<Topic>()
       }
    }

    // returns three states: "success", null (failure), or "not exist"
    private fun unsubscribe(topic: Topic): StatusCode {
        if (isSubscribedTo(topic.topic)) {
            basicClient.send(Payload.UNSUBSCRIBEPayload(topic))

            val unsubAck = if (ackQueue.peek() is Payload.UNSUBACKPayload) ackQueue.remove() else basicClient.receiveWithTimeout(8000)
            if (unsubAck is Payload.UNSUBACKPayload) {
                if (unsubAck.reasonCode == ReasonCode.Success){
                    logger.debug("GeoBroker's unSub ACK for $id:  topic: '{}'", topic.topic)
                    logger.debug("listeningTopic size before unsubscribing: {}", listeningTopics.size)
                    val lsToDelete = listeningTopics.find { it.topic == topic }
                    listeningTopics.remove(lsToDelete) // update local registry
                    logger.debug("listeningTopic size after unsubscribing: {}", listeningTopics.size)
                    return StatusCode.Success
                } else if(unsubAck.reasonCode == ReasonCode.NoSubscriptionExisted) {
                    logger.warn("Expected '${topic.topic}' subscription to be existed in geoBroker when unsubscribing, but doesn't!")
                    logger.debug("listeningTopic size before unsubscribing: {}", listeningTopics.size)
                    val lsToDelete = listeningTopics.find { it.topic == topic }
                    listeningTopics.remove(lsToDelete) // update local registry
                    logger.debug("listeningTopic size after unsubscribing: {}", listeningTopics.size)
                    return StatusCode.NotExist
                } else {
                    logger.error("Error unSubscribing from '${topic.topic}' by $id. Reason: {}.", unsubAck.reasonCode)
                    return StatusCode.Failure
                }
            } else if(unsubAck is Payload.PUBLISHPayload) {
                val message = gson.fromJson(unsubAck.content, FunctionMessage::class.java)
                if (message.receiverId == id) {
                    pubQueue.add(unsubAck)
                    logger.warn("Not a unSubAck ! adding it to the 'pubQueue'. dump: {}", unsubAck)
                }
                return unsubscribe(topic) // it's okay to resend UNSub Payload again
            } else if (unsubAck == null) {
                logger.error("Error unSubscribing from '${topic.topic}' by $id. No response from the broker")
                return StatusCode.Failure
            } else {
                ackQueue.add(unsubAck)
                logger.warn("Not a unSubAck! adding it to the 'ackQueue'. dump: {}", unsubAck)
                return unsubscribe(topic)
            }
        } else {
            logger.warn("Subscription '${topic.topic}' doesn't exist.")
            return StatusCode.NotExist
        }
    }
    private fun isSubscribedTo(topic: String): Boolean { // NOTE: checks only the topic, not the fence
        return listeningTopics.map { pair -> pair.topic.topic }.any { it == topic }
    }

    fun listenForFunction(type: String, timeout: Int): FunctionMessage? {
        // wiki: function call/ack/nack/result
        val msgPayload: Payload?
        val expectedAction = if (mode == ClientType.CLOUD) null else FunctionAction.valueOf(type) // cloud has two expected action
        when(mode) { // GeoFaaS server uses a thread for listening
            ClientType.CLIENT -> {
                val dequeuedPub = pubQueue.poll()
                if (dequeuedPub == null) {
                    logger.info("Listening for a GeoFaaS '$type'...")
                    msgPayload = when (timeout){
                        0 -> basicClient.receive() // blocking
                        else -> basicClient.receiveWithTimeout(timeout) // returns null after expiry
                    }
                    if (timeout > 0 && msgPayload == null) {
                        logger.error("Listening timeout (${timeout}ms)! pubQueue size:{}", pubQueue.size)
                        return null
                    }
                    logger.debug("gB EVENT: {}", msgPayload)
                } else {
                    msgPayload = dequeuedPub
                    logger.debug("Pub queue's size is ${pubQueue.size}. dequeued: {}", dequeuedPub)
                }
            }
            else -> { // GeoFaaS Edge/Cloud
                logger.info("Waiting for a GeoFaaS '$type'...")
                msgPayload = pubQueue.take() // blocking
                logger.debug("Pub queue's size is ${pubQueue.size}. dequeued: {}", msgPayload)
            }
        }

        if (msgPayload is Payload.PUBLISHPayload) {
// wiki:    msg.topic    => Topic(topic=functions/f1/call)
// wiki:    msg.content  => message
// wiki:    msg.geofence => BUFFER (POINT (0 0), 2)
            val topic = msgPayload.topic.topic.split("/")
            if(topic.first() == "functions") {
                val message = gson.fromJson(msgPayload.content, FunctionMessage::class.java)
                when(mode) {
                    ClientType.CLOUD -> {
                        return if (message.funcAction == FunctionAction.NACK || message.funcAction == FunctionAction.CALL)
                            message
                        else {
                            logger.error("{} expected, but received a {}. Pushing it to the pubQueue again.pubQueue size: {}", type, message.funcAction, pubQueue.size)
                            pubQueue.add(msgPayload)
                            null
                        }
                    }
                    else -> {
                        return if (message.funcAction == expectedAction)
                            message
                        else {
                            logger.error("{} expected, but received a {}. Pushing it to the pubQueue again.pubQueue size: {}", type, message.funcAction, pubQueue.size)
                            pubQueue.add(msgPayload)
                            null
                        }
                    }
                }
            } else {
                logger.error("msg is not related to the functions! {}", msgPayload.topic.topic)
                return null
            }
        } else if(msgPayload == null) {
            logger.error("Expected a PUBLISHPayload, but null received from geoBroker!")
            return null
        } else {
            ackQueue.add(msgPayload)
            logger.warn("Not a PUBLISHPayload! adding it to the 'ackQueue'. dump: {}", msgPayload)
            return null
        }
    }
    fun listenForPubAckAndProcess(funcAct: FunctionAction, funcName: String, timeout: Int): Pair<StatusCode, BrokerInfo?> {
        val enqueuedAck: Payload? //Note: the ack could also be a SubAck or UnSubAck, and maybe others
        enqueuedAck = when(mode) {
            ClientType.CLIENT -> ackQueue.poll() // non-blocking
            else -> ackQueue.take() // blocking (for GeoFaaS server)
        }
        if (enqueuedAck == null) { // only for the client
            if (timeout > 0)
                logger.debug("PubAck queue is empty. Listening for a PubAck for ${timeout}ms...")
            else
                logger.debug("PubAck queue is empty. Listening for a PubAck...")
            val pubAck = basicClient.receiveWithTimeout(timeout)
            var pubStatus = processPublishAckSuccess(pubAck, funcName, funcAct, timeout > 0) // will push to the pubQueue
            while (pubStatus.first == StatusCode.Retry){
                logger.debug("Retrying listening for a PubAck")
                pubStatus = listenForPubAckAndProcess(funcAct, funcName, timeout)
            }
            return pubStatus
        } else {
            logger.debug("PubAck queue's size is ${ackQueue.size}. dequeued: {}", enqueuedAck)
            var pubStatus = processPublishAckSuccess(enqueuedAck, funcName, funcAct, timeout > 0) // will push to the pubQueue
            while (pubStatus.first == StatusCode.Retry){
                logger.debug("Retrying listening for a PubAck")
                pubStatus = listenForPubAckAndProcess(funcAct, funcName, timeout)
            }
            return pubStatus
        }
    }

    fun updateLocation(newLoc :Location) : Pair<StatusCode, BrokerInfo?> {
        basicClient.send(Payload.PINGREQPayload(newLoc))
        val pubAck = if (ackQueue.peek() is Payload.PINGRESPPayload) ackQueue.remove() else basicClient.receiveWithTimeout(8000)
        logger.debug("ping ack: {}", pubAck) //DISCONNECTPayload(reasonCode=WrongBroker, brokerInfo=BrokerInfo(brokerId=Frankfurt, ip=localhost, port=5559)
        if(pubAck is Payload.DISCONNECTPayload) {
            logger.info("moved outside of the current broker's area.")
            if (pubAck.reasonCode == ReasonCode.WrongBroker){ // you are now outside my area
                location = newLoc // update the local, as the current broker is no longer responsible for us
                if (pubAck.brokerInfo != null) {
                    val changeStatus = changeBroker(pubAck.brokerInfo!!)
                    if (changeStatus == StatusCode.Success) {
                        logger.info("$id's location updated to {}", location)
                        return Pair(StatusCode.Success, pubAck.brokerInfo)
                    } else {
                        logger.fatal("Failed to change the broker. And the previous broker is no longer responsible")
                        throwSafeException("Error updating the $id location to $newLoc"); throw RuntimeException() // dummy, to skip return check
                    }
                } else {
                    logger.fatal("No broker is responsible for the current location")
                    throwSafeException("Error updating the $id location to $newLoc"); throw RuntimeException() // dummy
                }
            } else {
                logger.fatal("unexpected reason code: {}", pubAck.reasonCode)
                throwSafeException("Error updating the $id location to $newLoc"); throw RuntimeException() // dummy
            }
        } else if (pubAck is Payload.PINGRESPPayload) {
            if(pubAck.reasonCode == ReasonCode.LocationUpdated) { // success
                location = newLoc
                logger.info("location updated to {}", location)
                return Pair(StatusCode.Success, null)
            } else if(pubAck.reasonCode == ReasonCode.NotConnectedOrNoLocation){
                location = newLoc
                logger.warn("Not Connected, but location updated to {}", location)
                return Pair(StatusCode.NotConnected, null)
            } else {
                logger.fatal("unexpected reason code: {}", pubAck.reasonCode)
                throwSafeException("Error updating the $id location to $newLoc"); throw RuntimeException() // dummy
            }
        } else if (pubAck == null) {
            logger.error("Updating location failed! No response from the '${basicClient.identity}' broker")
            return Pair(StatusCode.Failure, null)
        } else if (pubAck is Payload.PUBLISHPayload) {
            val message = gson.fromJson(pubAck.content, FunctionMessage::class.java)
            if (message.receiverId == id) {
                pubQueue.add(pubAck) // to be processed by listenForFunction()
                logger.warn("Not a PINGRESPayload! adding it to the 'pubQueue'. dump: {}", pubAck)
            }
            return updateLocation(newLoc)//Pair(StatusCode.Retry, null)
        }
        else {
            ackQueue.add(pubAck)
            logger.warn("Not a PINGRESPayload! adding it to the 'ackQueue'. dump: {}", pubAck)
            return updateLocation(newLoc)//Pair(StatusCode.Retry, null)
        }
    }

    protected fun changeBroker(newBroker: BrokerInfo): StatusCode {
        logger.warn("changing the remote broker to $newBroker...")
        val oldBroker = basicClient
        var clientId = basicClient.identity // Note: GeoBrokerClient.id never changes
        visitedBrokers.add(Pair(basicClient.ip, basicClient.port)) // already connected broker
        if ( visitedBrokers.contains(Pair(newBroker.ip, newBroker.port)) ){
            logger.debug("clearing visitedBrokers with size of {}", visitedBrokers.size)
            visitedBrokers.clear()
            clientId = id + "_${Date().time}" // to patch problem with reconnecting brokers
        }
        basicClient = GBBasicClient(newBroker.ip, newBroker.port, identity = clientId)
        basicClient.send(Payload.CONNECTPayload(location)) // connect

        val connAck = basicClient.receiveWithTimeout(8000)
        val connStatus = processConnAckSuccess(connAck, newBroker, true)

        if(connStatus == StatusCode.Success) {
            logger.info("connected to the {} broker", newBroker.brokerId)
            oldBroker.send(Payload.DISCONNECTPayload(ReasonCode.NormalDisconnection)) // disconnect
            oldBroker.tearDownClient()
            logger.info("disconnected from the previous broker")
            listeningTopics.clear()
            ackQueue.clear()
            pubQueue.clear()
            logger.debug("cleared queues and listeningTopics")
            return StatusCode.Success
        } else { // TODO handle 'StatusCode.WrongBroker', and reconnect to the correct broker
            logger.error("failed to change the remote broker to: ${newBroker.brokerId}. Thus, remote geobroker is not changed")
            basicClient = oldBroker
            return StatusCode.Failure
        }
    }

    // follow geoBroker instructions to Disconnect
    fun terminate() {
        basicClient.send(Payload.DISCONNECTPayload(ReasonCode.NormalDisconnection)) // disconnect
        basicClient.tearDownClient()
        if (processManager.tearDown(3000)) {
            logger.info("GBClient Channel shut down properly.")
        } else {
            logger.fatal("ProcessManager reported that processes are still running: {}",
                processManager.incompleteZMQProcesses)
        }
        val queueSizesMsg = "$id: (pubQueue: ${pubQueue.size}, ackQueue: ${ackQueue.size})"
        if (pubQueue.size + ackQueue.size == 0)
            logger.debug(queueSizesMsg)
        else
            logger.warn(queueSizesMsg)
//        exitProcess(0) // terminates current process
    }


    protected fun processConnAckSuccess(connAck: Payload?, broker: BrokerInfo, withTimeout: Boolean) :StatusCode {
        if (connAck is Payload.CONNACKPayload && connAck.reasonCode == ReasonCode.Success)
            return StatusCode.Success
        else if (connAck is Payload.DISCONNECTPayload) {
            if (connAck.reasonCode == ReasonCode.ProtocolError)
                logger.fatal("${connAck.reasonCode}! duplicate client id? can't connect to the geobroker ${broker.ip}:${broker.port}.")
            else if(connAck.reasonCode == ReasonCode.WrongBroker && connAck.brokerInfo != null) {
                logger.warn("Wrong Broker! Responsible broker is: {}", connAck.brokerInfo)
                return StatusCode.WrongBroker
            } else
                logger.fatal("${connAck.reasonCode}! can't connect to the geobroker ${broker.ip}:${broker.port}. other suggested server? ${connAck.brokerInfo}")

            return StatusCode.Failure
        } else if (connAck == null) {
            if (withTimeout) {
                throwSafeException("Timeout! $id can't connect to the geobroker ${broker.ip}:${broker.port}. Check the Address and try again")
                throw RuntimeException()} // dummy
            else {
                throwSafeException("Empty Response! $id can't connect to the geobroker ${broker.ip}:${broker.port}. Check the Address and try again")
                throw RuntimeException() } // dummy
        } else {
            logger.fatal("Unexpected 'Conn ACK'! Received geoBroker's answer: {}", connAck)
            return StatusCode.Failure
        }
    }
    // Returns Success, Failure, WrongBroker, and Retry
    protected fun processPublishAckSuccess(pubAck: Payload?, funcName: String, funcAct: FunctionAction, withTimeout: Boolean): Pair<StatusCode, BrokerInfo?> {
        val logMsg = "'$funcName' $funcAct Pub confirmation: {}"
        if (pubAck is Payload.PUBACKPayload) {
            val noError = logPublishAck(pubAck, logMsg) // logs the reasonCode
            if (noError) return Pair(StatusCode.Success, null)
            else logger.error("${pubAck.reasonCode}! $id's'Publish ACK' received for '$funcName' $funcAct")
        } else if(pubAck is Payload.DISCONNECTPayload && pubAck.brokerInfo != null)
            return Pair(StatusCode.WrongBroker, pubAck.brokerInfo) // disconnect payload when publishing to a wrong broker
          else if (pubAck == null && withTimeout) {
            logger.error("Timeout! no 'Publish ACK' received for '$funcName' $funcAct by '$id'. ackQueue size: {}", ackQueue.size)
        } else if (pubAck is Payload.PUBLISHPayload) {
            val message = gson.fromJson(pubAck.content, FunctionMessage::class.java)
            if (message.receiverId == id) {
                pubQueue.add(pubAck) // to be processed by listenForFunction()
                logger.warn("Not a PUBACKPayload! adding it to the 'pubQueue'. dump: {}", pubAck)
            }
            return Pair(StatusCode.Retry, null)
        } else {
            logger.error("Unexpected! $logMsg", pubAck)
            if(pubAck != null) {
                logger.warn("Not a PUBACKPayload! adding it to the 'ackQueue'. dump: {}", pubAck)
                ackQueue.add(pubAck)
                return Pair(StatusCode.Retry, null)
            }
        }
        return Pair(StatusCode.Failure, null)
    }
    protected fun logPublishAck(pubAck: Payload.PUBACKPayload, logMsg: String): Boolean {
        // logMsg: "GeoBroker's 'Publish ACK' for the '$funcName' ACK by $id: {}"
        when (pubAck.reasonCode) {
            // Successes:
            ReasonCode.GrantedQoS0 -> logger.debug(logMsg, pubAck)
            ReasonCode.Success -> logger.debug(logMsg, pubAck)
            ReasonCode.NoMatchingSubscribersButForwarded -> logger.warn(logMsg, pubAck.reasonCode)
            // Failures:
            ReasonCode.NoMatchingSubscribers -> {
                logger.error("$logMsg. Terminating..?", pubAck.reasonCode)
                return false
            }
            ReasonCode.NotConnectedOrNoLocation -> {
                logger.error(logMsg, pubAck)
                return false
            }
            else -> logger.warn(logMsg, pubAck)
        }
        return true
    }
    fun throwSafeException(msg: String) {
        this.terminate()
        throw RuntimeException(msg)
    }
}