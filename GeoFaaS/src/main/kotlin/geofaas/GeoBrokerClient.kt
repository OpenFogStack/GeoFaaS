package geofaas

import com.google.gson.Gson
import de.hasenburg.geobroker.client.main.SimpleClient
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
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager

// Basic Geobroker client for GeoFaaS system
abstract class GeoBrokerClient(var location: Location, val mode: ClientType, debug: Boolean, host: String = "localhost", port: Int = 5559, val id: String = "GeoFaaSAbstract") {
    val logger = LogManager.getLogger()
    private var listeningTopics = mutableSetOf<ListeningTopic>()
    private val processManager = ZMQProcessManager()
    var remoteGeoBroker = SimpleClient(host, port, identity = id)
    val gson = Gson()
    init {
        if (debug) { setLogLevel(logger, Level.DEBUG) }
        remoteGeoBroker.send(Payload.CONNECTPayload(location)) // connect
        var connAck = remoteGeoBroker.receiveWithTimeout(3000)

        val connSuccess = processConnAckSuccess(connAck, BrokerInfo(remoteGeoBroker.identity, host, port), true)
        if (!connSuccess) {
            if (connAck is Payload.DISCONNECTPayload) {
                if(connAck.brokerInfo == null) { // retry with suggested broker
                    logger.fatal("${connAck.reasonCode}!! No responsible broker found or duplicate client id. $id can't connect to the remote geoBroker '$host:$port'.")
                    throw RuntimeException("Error while connecting to the geoBroker")
                } else {
                    val newBrokerInfo = connAck.brokerInfo!! // TODO replace with 'changeBroker()' and do the retry
//                    val changeIsSuccess = changeBroker(newBrokerInfo)
                    logger.warn("Changed the remote broker to the suggested: $newBrokerInfo")
                    remoteGeoBroker = SimpleClient(newBrokerInfo.ip, newBrokerInfo.port, identity = id)
                    remoteGeoBroker.send(Payload.CONNECTPayload(location)) // connect
                    connAck = remoteGeoBroker.receiveWithTimeout(3000)
                    val connSuccess = processConnAckSuccess(connAck, newBrokerInfo, true)
                    if (!connSuccess)
                        throw RuntimeException("Error connecting to the new geoBroker")
                }
            } else if (connAck == null) {
                throw RuntimeException("Timeout! can't connect to geobroker $host:$port. Check the Address and try again")
            } else {
                logger.fatal("Unexpected 'Conn ACK'! Received geoBroker's answer: {}", connAck)
                throw RuntimeException("Error while connecting to the geoBroker")
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
        val newSubscribe: String? = subscribe(topic, fence) //subscribe(baseTopic, fence, functionAction)
        if (newSubscribe == "success") { newTopics.add(ListeningTopic(topic, fence)) }

        if (newSubscribe != null) {
            if (mode == ClientType.CLIENT) { // Client subscribes to two topics
                val ackTopic = Topic("functions/$funcName/ack")
                val ackSubscribe = subscribe(ackTopic, fence)
                if (ackSubscribe == "success") { newTopics.add(ListeningTopic(ackTopic, fence)) }
            }
            if (mode == ClientType.CLOUD) { // Cloud subscribes to two topics
                val nackTopic = Topic("functions/$funcName/nack")
                val nackSubscribe = subscribe(nackTopic, fence)
                if (nackSubscribe == "success") { newTopics.add(ListeningTopic(nackTopic, fence)) }
            }
            return if (newTopics.isNotEmpty()) {
                newTopics.forEach {  listeningTopics.add(it) } // add to local registry
                logger.debug("ListeningTopics appended by: {}", listeningTopics)
                newTopics // for error handling purposes
            } else {
                logger.debug("ListeningTopics didn't change. Nothing subscribed new!")
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
    private fun subscribe(topic: Topic, fence: Geofence): String? {
        if (!isSubscribedTo(topic.topic)) {
            remoteGeoBroker.send(Payload.SUBSCRIBEPayload(topic, fence))
            val subAck = remoteGeoBroker.receiveWithTimeout(3000)
            return if (subAck is Payload.SUBACKPayload){
                if (subAck.reasonCode == ReasonCode.GrantedQoS0){
                    logger.info("GeoBroker's Sub ACK for id:  for '${topic.topic}' in $fence: {}", subAck)
                    "success"
                } else {
                    logger.error("Error Subscribing to '${topic.topic}' by $id. Reason: {}.", subAck.reasonCode)
                    null // failure
                }
            } else {
                logger.fatal("Expected a SubAck Payload! {}", subAck)
                null // failure
            }
        } else {
            logger.warn("already subscribed to the '${topic.topic}'")
            return  "already exist"
        }
    }
    private fun isSubscribedTo(topic: String): Boolean { // NOTE: checks only the topic, not the fence
        return listeningTopics.map { pair -> pair.topic.topic }.any { it == topic }
    }

    fun listenFor(type: String, timeout: Int): FunctionMessage? {
        // function call
        logger.info("Listening to the geoBroker server for a '$type'...")
        val msg: Payload? = when (timeout){
            0 -> remoteGeoBroker.receive() // blocking
            else -> remoteGeoBroker.receiveWithTimeout(timeout)
        }
        if (timeout > 0 && msg == null) {
            logger.error("Listening timeout (${timeout}ms)!")
            return null
        }
        logger.info("EVENT from geoBroker: {}", msg)
        if (msg is Payload.PUBLISHPayload) {
// wiki:    msg.topic    => Topic(topic=functions/f1/call)
// wiki:    msg.content  => message
// wiki:    msg.geofence => BUFFER (POINT (0 0), 2)
            val topic = msg.topic.topic.split("/")
            if(topic.first() == "functions") {
                val message = gson.fromJson(msg.content, FunctionMessage::class.java)
                return message
//                return FunctionMessage(funcName, FunctionAction.valueOf(funcAction), msg.content, Model.TypeCode.Piggy)
            } else {
                logger.error("msg is not related to the functions! {}", msg.topic.topic)
                return null
            }
        } else {
            logger.error("Unexpected geoBroker message (not a PUBLISHPayload): $msg")
            return null
        }
    }

    fun updateLocation(newLoc :Location) :Boolean{
        remoteGeoBroker.send(Payload.PINGREQPayload(newLoc))
        val pubAck = remoteGeoBroker.receiveWithTimeout(3000)
        logger.debug("ping ack: {}", pubAck) //DISCONNECTPayload(reasonCode=WrongBroker, brokerInfo=BrokerInfo(brokerId=Frankfurt, ip=localhost, port=5559)
        if(pubAck is Payload.DISCONNECTPayload) {
            logger.warn("moved outside of the current broker's area ('${remoteGeoBroker.identity}').")
            if (pubAck.reasonCode == ReasonCode.WrongBroker){ // you are now outside my area
                location = newLoc // update the local, as the current broker is no longer responsible for us
                if (pubAck.brokerInfo != null) {
                    val changeIsSuccess = changeBroker(pubAck.brokerInfo!!)
                    if (changeIsSuccess) {
                        logger.debug("location updated to {}", location)
                        return true
                    } else {
                        logger.fatal("Failed to change the broker. And the previous broker is no longer responsible")
                        throw RuntimeException("Error updating the location to $newLoc")
                    }
                } else {
                    logger.fatal("No broker is responsible for current location")
                    throw RuntimeException("Error updating the location to $newLoc")
                }
            } else {
                logger.fatal("unexpected reason code: {}", pubAck.reasonCode)
                throw RuntimeException("Error updating the location to $newLoc")
            }
        } else if (pubAck is Payload.PINGRESPPayload) {
            if(pubAck.reasonCode == ReasonCode.LocationUpdated) { // success
                location = newLoc
                logger.debug("location updated to {}", location)
                return true
            } else {
                logger.fatal("unexpected reason code: {}", pubAck.reasonCode)
                throw RuntimeException("Error updating the location to $newLoc")
            }
        } else if (pubAck == null) {
            logger.error("Updating location failed! No response from the '${remoteGeoBroker.identity}' broker")
            return false
        } else {
            logger.fatal("Unexpected ack when updating the location! Received geoBroker's answer: {}", pubAck)
            throw RuntimeException("Error updating the location to $newLoc")
        }
    }

    protected fun changeBroker(broker: BrokerInfo): Boolean {
        logger.warn("changing the remote broker to $broker...")
        val oldBroker = remoteGeoBroker
        remoteGeoBroker = SimpleClient(broker.ip, broker.port, identity = id)
        remoteGeoBroker.send(Payload.CONNECTPayload(location)) // connect

        val connAck = remoteGeoBroker.receiveWithTimeout(3000)
        val connSuccess = processConnAckSuccess(connAck, broker, true)

        if(connSuccess) {
            logger.info("switched the remote broker to: ${broker.brokerId}")
            oldBroker.tearDownClient()
            return true
        } else {
            logger.error("failed to change the remote broker to: $broker")
            remoteGeoBroker = oldBroker
            return false
        }
    }

    // follow geoBroker instructions to Disconnect
    fun terminate() {
        remoteGeoBroker.send(Payload.DISCONNECTPayload(ReasonCode.NormalDisconnection)) // disconnect
        remoteGeoBroker.tearDownClient()
        if (processManager.tearDown(3000)) {
            logger.info("GBClient Channel shut down properly.")
        } else {
            logger.fatal("ProcessManager reported that processes are still running: {}",
                processManager.incompleteZMQProcesses)
        }
//        exitProcess(0) // terminates current process
    }


    protected fun processConnAckSuccess(connAck: Payload?, broker: BrokerInfo, withTimeout: Boolean) :Boolean{
        if (connAck is Payload.CONNACKPayload && connAck.reasonCode == ReasonCode.Success)
            return true
        else if (connAck is Payload.DISCONNECTPayload) {
            logger.fatal("${connAck.reasonCode}! can't connect to the geobroker ${broker.ip}:${broker.port}. another suggested server? ${connAck.brokerInfo}")
            return false
        } else if (connAck == null) {
            if (withTimeout)
                throw RuntimeException("Timeout! can't connect to the geobroker ${broker.ip}:${broker.port}. Check the Address and try again")
            else
                throw RuntimeException("No Response! can't connect to the geobroker ${broker.ip}:${broker.port}. Check the Address and try again")
        } else {
            logger.fatal("Unexpected 'Conn ACK'! Received geoBroker's answer: {}", connAck)
            return false
        }
    }
    protected fun processPublishAckSuccess(pubAck: Payload?, funcName: String, funcAct: FunctionAction, withTimeout: Boolean): Boolean {
        val logMsg = "GeoBroker's 'Publish ACK' for the '$funcName' $funcAct by $id: {}"
        if (pubAck is Payload.PUBACKPayload) {
            val noError = logPublishAck(pubAck, logMsg) // logs the reasonCode
            if (!noError) return false
        } else if (pubAck == null && withTimeout) {
            logger.error("Timeout! no 'Publish ACK' received for '$funcName' $funcAct by $id")
            return false
        } else {
            logger.error("Unexpected! $logMsg", pubAck)
        }
        return true
    }
    protected fun logPublishAck(pubAck: Payload.PUBACKPayload, logMsg: String): Boolean {
        // logMsg: "GeoBroker's 'Publish ACK' for the '$funcName' ACK by $id: {}"
        when (pubAck.reasonCode) {
            ReasonCode.GrantedQoS0 -> logger.info(logMsg, pubAck)
            ReasonCode.Success -> logger.info(logMsg, pubAck)
            ReasonCode.NoMatchingSubscribersButForwarded -> logger.warn(logMsg, pubAck.reasonCode)
            ReasonCode.NoMatchingSubscribers -> {
                logger.error("$logMsg. Terminating...", pubAck.reasonCode)
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
}