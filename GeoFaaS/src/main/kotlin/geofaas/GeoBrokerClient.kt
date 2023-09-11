package geofaas

import com.google.gson.Gson
import de.hasenburg.geobroker.client.main.SimpleClient
import de.hasenburg.geobroker.commons.communication.ZMQProcessManager
import de.hasenburg.geobroker.commons.model.message.Payload
import de.hasenburg.geobroker.commons.model.message.ReasonCode
import de.hasenburg.geobroker.commons.model.message.Topic
import de.hasenburg.geobroker.commons.model.spatial.Geofence
import de.hasenburg.geobroker.commons.model.spatial.Location
import de.hasenburg.geobroker.commons.model.spatial.toJson
import de.hasenburg.geobroker.commons.setLogLevel
import geofaas.Model.FunctionAction
import geofaas.Model.FunctionMessage
import geofaas.Model.ListeningTopic
import geofaas.Model.ClientType
import kotlinx.serialization.json.Json
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager

private val logger = LogManager.getLogger()

// Basic Geobroker client for GeoFaaS system
abstract class GeoBrokerClient(val location: Location, val mode: ClientType, debug: Boolean, host: String = "localhost", port: Int = 5559, id: String = "GeoFaaS") {

    private var listeningTopics = mutableSetOf<ListeningTopic>()
    private val processManager = ZMQProcessManager()
    val remoteGeoBroker = SimpleClient(host, port, identity = id)
    val gson = Gson()
    init {
        if (debug) { setLogLevel(logger, Level.DEBUG) }
        remoteGeoBroker.send(Payload.CONNECTPayload(location)) // connect //FIXME: location of the client?
        logger.info("Received geoBroker's answer (Conn ACK): {}", remoteGeoBroker.receive())
        // TODO: Check if this is success else error and terminate
    }

    fun subscribeFunction(funcName: String, fence: Geofence) {
        var newTopics: MutableSet<ListeningTopic> = mutableSetOf()
        var baseTopic = "functions/$funcName"
        var functionAction: FunctionAction
        when (mode) {
            ClientType.EDGE   -> {
                baseTopic += "/call"; functionAction = FunctionAction.CALL
            }
            ClientType.CLIENT -> {
                baseTopic += "/result"; functionAction = FunctionAction.RESULT
            }
            ClientType.CLOUD  -> {
                baseTopic += "/nack"; functionAction = FunctionAction.NACK
            }
        }
        val topic = Topic(baseTopic)
        val newSubscribe = subscribe(topic, fence, functionAction) //subscribe(baseTopic, fence, functionAction)
        if (newSubscribe != null) { newTopics.add(ListeningTopic(topic, fence)) }
        logger.debug("new topic: {}", newSubscribe)

        if (mode == ClientType.CLIENT) { // Client subscribes to two topics
            val ackTopic = Topic("functions/$funcName/ack")
            val ackSubscribe = subscribe(ackTopic, fence, functionAction)
            if (ackSubscribe != null) { newTopics.add(ListeningTopic(ackTopic, fence)) }
        }
        newTopics.forEach {  listeningTopics.add(it) } // add to local registry
        logger.debug("ListeningTopics updated: {}", listeningTopics)
    }
    private fun subscribe(topic: Topic, fence: Geofence, action: FunctionAction): ListeningTopic? {
        logger.debug("new subscribe request: t: {}, f: {}", topic, fence)//, action)
        if (!isSubscribedTo(topic.topic)) {
            remoteGeoBroker.send(Payload.SUBSCRIBEPayload(topic, fence))
            val subAck = remoteGeoBroker.receive()
            if (subAck is Payload.SUBACKPayload){
                if (subAck.reasonCode == ReasonCode.GrantedQoS0){
                    logger.info("GeoBroker's Sub ACK by ${mode.name}:  for '${topic.topic}' in $fence: {}", subAck)
                    return ListeningTopic(topic, fence)
                } else {
                    logger.fatal("Error Subscribing to '${topic.topic}' by ${mode.name}. Reason: {}. Terminating...", subAck.reasonCode)
                    this.terminate()
                }
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
        logger.info("new geoBroker msg: {}", msg)
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
        val functionCalls =  listeningTopics.map { pair -> pair.topic.topic }//.filter { it.endsWith("/call") }
        logger.debug("functions already listening: {}", functionCalls)
        return functionCalls.map { it.substringAfter("/").substringBefore('/') }.distinct() // name of function is between '/', e.g. "functions/f1/call"
    }

    // follow geoBroker instructions to Disconnect
    fun terminate() { // FIXME: can be called twice. once in initialization, once in the continue from an error
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
}