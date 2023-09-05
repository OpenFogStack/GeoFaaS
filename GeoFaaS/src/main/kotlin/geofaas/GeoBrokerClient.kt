package geofaas

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
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager

private val logger = LogManager.getLogger()

// A Geobroker client to integrate with GeoFaaS for a func
class GeoBrokerClient(val location: Location = Location(0.0,0.0), debug: Boolean, host: String = "localhost", port: Int = 5559) {

    private var listeningTopics = mutableSetOf<Pair<Topic, Geofence>>()
    private val processManager = ZMQProcessManager()
    private val client = SimpleClient(host, port, identity = "GeoFaaS")
    init {
        if (debug) { setLogLevel(logger, Level.DEBUG) }
        client.send(Payload.CONNECTPayload(location)) // connect //FIXME: location of the client?
        logger.info("Received geoBroker's answer (Conn ACK): {}", client.receive())
        // TODO: Check if this is success else error and terminate
    }

    fun subscribeFunction(funcName: String) {
        if (notSubscribedTo("functions/$funcName/call")) { // check if already subscribed
            val topic = Topic("functions/$funcName/call")
            val fence = Geofence.circle(location, 2.0) //FIXME: change the location of subscription
            client.send(Payload.SUBSCRIBEPayload(topic, fence))
            val subAck = client.receive()
            if (subAck is Payload.SUBACKPayload){
                if (subAck.reasonCode == ReasonCode.GrantedQoS0){
                    logger.info("Received geoBroker's answer(Sub ACK for '$funcName' call): {}", subAck)
                    listeningTopics.add(Pair(topic, fence))
                } else {
                    logger.fatal("Error Subscribing to 'functions/$funcName/call'. Terminating...")
                    this.terminate()
                }
            }
        } else {
            logger.error("already subscribed to the '$funcName'")
        }
    }

    fun listen(): FunctionMessage? {
        // function call
        logger.info("Listening to the geoBroker server...")
        val msg = client.receive() // blocking
        logger.info("new geoBroker msg: {}", msg)
        if (msg is Payload.PUBLISHPayload) {
// wiki:    msg.topic    => Topic(topic=functions/f1/call)
// wiki:    msg.content  => message
// wiki:    msg.geofence => BUFFER (POINT (0 0), 2)
            val topic = msg.topic.topic.split("/")
            if(topic.first() == "functions") {
                val funcName = topic[1]
                val funcAction = topic[2].uppercase() //NOTE: [2] supposed to be last word, but don't replace with .last()
                return FunctionMessage(funcName, FunctionAction.valueOf(funcAction), msg.content)
            } else {
                logger.error("msg is not related to the functions! {}", msg.topic.topic)
                return null
            }
        } else {
            logger.error("Unexpected geoBroker message")
            return null
        }
    }

    // publishes result for a function request
    fun sendResult(funcName: String, res: String) { //TODO: what location to send the result?
        client.send(Payload.PUBLISHPayload(Topic("functions/$funcName/result"),Geofence.circle(location,2.0), res))
        val msg = client.receive()
        logger.info("Received geoBroker's answer: {}", msg)
        if (msg is Payload.PUBACKPayload) {
            if (msg.reasonCode == ReasonCode.NoMatchingSubscribers) {
                logger.error("No matching subscriber found when sending the result for the function '$funcName'")
            } else {
                logger.info("result sent with reasonCode: {}", msg.reasonCode)
            }
        }
    }

    // publishes an Acknowledgement for receiving a client's request. the client listens to it
    fun sendAck(funcName: String) {
        client.send(Payload.PUBLISHPayload(Topic("functions/$funcName/ack"),Geofence.circle(location,2.0), "$funcName"))
        val msg = client.receive()
        logger.info("Received geoBroker's answer for publishing ack ACK: {}", msg)
        // TODO: check if reasonCode is okay. log error if not.
    }

    // publishes a NotAck to offload to the cloud
    fun sendNack(funcName: String, data: String) { // piggyback the data to the nack
        client.send(Payload.PUBLISHPayload(Topic("functions/$funcName/nack"),Geofence.circle(location,2.0), data))
        val msg = client.receive()
        logger.info("Received geoBroker's answer for publishing NACK: {}", msg)
        // TODO: check if reasonCode is okay. log error if not.
    }

    fun subscribedFunctionsList(): List<String> {
        val functionCalls = listeningTopics.map { pair -> pair.first.topic }.filter { it.endsWith("/call") }
        return functionCalls.map { it.substringAfter("/").substringBefore('/') } // name of function is between '/', e.g. "functions/f1/call"
    }

    // follow geoBroker instructions to Disconnect
    fun terminate() { // FIXME: can be called twice. once in initialization, once in the continue of an error
        client.send(Payload.DISCONNECTPayload(ReasonCode.NormalDisconnection)) // disconnect
        client.tearDownClient()
        if (processManager.tearDown(3000)) {
            logger.info("GBClient Channel shut down properly.")
        } else {
            logger.fatal("ProcessManager reported that processes are still running: {}",
                processManager.incompleteZMQProcesses)
        }
//        exitProcess(0) // terminates current process
    }

    private fun notSubscribedTo(topic: String): Boolean { // NOTE: checks only the topic, not the fence
        return listeningTopics.map { pair -> pair.first.topic }.filter { it == topic }.isEmpty()
    }

}

//fun main() {
//    val f1 = GeoBrokerClient()
//    f1.subscribeFunction("f1")
//    f1.listen("f1") //TODO: call in a coroutine
//    f1.listen("f1")
//    f1.terminate()
//}