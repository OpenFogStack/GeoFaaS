package geofaas

import com.google.gson.Gson
import de.hasenburg.geobroker.client.main.SimpleClient
import de.hasenburg.geobroker.commons.communication.ZMQProcessManager
import de.hasenburg.geobroker.commons.model.message.Payload
import de.hasenburg.geobroker.commons.model.message.ReasonCode
import de.hasenburg.geobroker.commons.model.message.Topic
import de.hasenburg.geobroker.commons.model.spatial.Geofence
import de.hasenburg.geobroker.commons.model.spatial.Location
import de.hasenburg.geobroker.commons.setLogLevel
import geofaas.Model.FunctionAction
import geofaas.Model.FunctionMessage
import geofaas.Model.ListeningTopic
import geofaas.Model.ClientType
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import java.lang.Exception

val logger = LogManager.getLogger()

// Basic Geobroker client for GeoFaaS system
abstract class GeoBrokerClient(val location: Location, val mode: ClientType, debug: Boolean, host: String = "localhost", port: Int = 5559, val id: String = "GeoFaaSAbstract") {

    private var listeningTopics = mutableSetOf<ListeningTopic>()
    private val processManager = ZMQProcessManager()
    var remoteGeoBroker = SimpleClient(host, port, identity = id)
    val gson = Gson()
    init {
        if (debug) { setLogLevel(logger, Level.DEBUG) }
        remoteGeoBroker.send(Payload.CONNECTPayload(location)) // connect
        var connAck = remoteGeoBroker.receive()
        if (connAck is Payload.DISCONNECTPayload) {
            if(connAck.brokerInfo == null) { // retry with suggested broker
                logger.fatal("${connAck.reasonCode}! $id can't connect to the remote geoBroker '$host:$port'! Responsible broker: ${connAck.brokerInfo}")
                throw RuntimeException("Error while connecting to the geoBroker")
            } else {
                logger.warn("Changed the remote broker to the suggested: ${connAck.brokerInfo}")
                remoteGeoBroker = SimpleClient(connAck.brokerInfo!!.ip, connAck.brokerInfo!!.port, identity = id)
                remoteGeoBroker.send(Payload.CONNECTPayload(location)) // connect
                connAck = remoteGeoBroker.receive()
            }
        }
        logger.info("Received geoBroker's answer (Conn ACK): {}", connAck)
    }

    fun subscribeFunction(funcName: String, fence: Geofence): MutableSet<ListeningTopic>? {
        logger.debug("subscribeFunction() call. params:'{}', '{}'", funcName, fence)
        var newTopics: MutableSet<ListeningTopic> = mutableSetOf()
        var baseTopic = "functions/$funcName"
        baseTopic += when (mode) {
            ClientType.EDGE   -> "/call"
            ClientType.CLIENT -> "/result"
            ClientType.CLOUD  -> "/nack"
        }
        val topic = Topic(baseTopic)
        val newSubscribe = subscribe(topic, fence) //subscribe(baseTopic, fence, functionAction)
        if (newSubscribe != null) { newTopics.add(ListeningTopic(topic, fence)) }

        if (mode == ClientType.CLIENT && newSubscribe != null) { // Client subscribes to two topics
            val ackTopic = Topic("functions/$funcName/ack")
            val ackSubscribe = subscribe(ackTopic, fence)
            if (ackSubscribe != null) { newTopics.add(ListeningTopic(ackTopic, fence)) }
        }
        return if (newTopics.isNotEmpty()) {
            newTopics.forEach {  listeningTopics.add(it) } // add to local registry
            logger.debug("ListeningTopics appended by: {}", listeningTopics)
            newTopics // for error handling purposes
        } else {
            logger.debug("ListeningTopics didn't change. Nothing subscribed new!")
            null
        }
    }

    private fun subscribe(topic: Topic, fence: Geofence): ListeningTopic? {
        if (!isSubscribedTo(topic.topic)) {
            remoteGeoBroker.send(Payload.SUBSCRIBEPayload(topic, fence))
            val subAck = remoteGeoBroker.receive()
            if (subAck is Payload.SUBACKPayload){
                if (subAck.reasonCode == ReasonCode.GrantedQoS0){
                    logger.info("GeoBroker's Sub ACK by ${mode.name}:  for '${topic.topic}' in $fence: {}", subAck)
                    return ListeningTopic(topic, fence)
                } else { logger.error("Error Subscribing to '${topic.topic}' by ${mode.name}. Reason: {}.", subAck.reasonCode) }
            }
        } else {
            logger.error("already subscribed to the '${topic.topic}'")
        }
        return null
    }

    fun listen(): FunctionMessage? {
        // function call
        logger.info("Listening to the geoBroker server...")
        val msg = remoteGeoBroker.receive() // blocking
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

    // returns a list of function names, either listening to call, results, or ack/nack (since a client is either pub or sub of a topic not both)
    fun subscribedFunctionsList(): List<String> {
        val functionCalls = listeningTopics.map { pair -> pair.topic.topic }//.filter { it.endsWith("/call") }
        logger.debug("functions that $id already listening to: {}", functionCalls)
        return functionCalls.map { it.substringAfter("/").substringBefore('/') }.distinct() // name of function is between '/', e.g. "functions/f1/call"
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

     private fun isSubscribedTo(topic: String): Boolean { // NOTE: checks only the topic, not the fence
        return listeningTopics.map { pair -> pair.topic.topic }.any { it == topic }
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