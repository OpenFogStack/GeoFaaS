package geofaas

import de.hasenburg.geobroker.client.main.SimpleClient
import de.hasenburg.geobroker.commons.communication.ZMQProcessManager
import de.hasenburg.geobroker.commons.model.message.Payload
import de.hasenburg.geobroker.commons.model.message.ReasonCode
import de.hasenburg.geobroker.commons.model.message.Topic
import de.hasenburg.geobroker.commons.model.spatial.Geofence
import de.hasenburg.geobroker.commons.model.spatial.Location
import de.hasenburg.geobroker.commons.setLogLevel
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager

private val logger = LogManager.getLogger()

// A Geobroker client to integrate with GeoFaaS for a func
class GeoBrokerClient(val location: Location = Location(0.0,0.0)) {

    private val processManager = ZMQProcessManager()
    private val client = SimpleClient("localhost", 5559, identity = "GeoFaaS")
    init {
        setLogLevel(logger, Level.DEBUG)
        client.send(Payload.CONNECTPayload(location)) // connect //FIXME: location of the client?
        logger.info("Received server answer (Conn ACK): {}", client.receive())
        // TODO: Check if this is success else error and terminate
    }

    fun subscribeFunction(funcName: String) {
        client.send(Payload.SUBSCRIBEPayload(Topic("/$funcName/call"),
            Geofence.circle(location, 2.0))) //FIXME: change location of subscription
        val subAck = client.receive()
        if (subAck is Payload.SUBACKPayload){
            if (subAck.reasonCode == ReasonCode.GrantedQoS0){
                logger.info("Received server answer(Sub ACK for '$funcName' call): {}", subAck)
            } else {
                logger.error("Error Subscribing to /$funcName/call. Terminating...")
                this.terminate()
            }
        }
        //TODO: subscribe to /function/ack?
    }

    fun listen(funcName: String): String {
        // function call
        logger.info("Listening on the topic: '/$funcName/call'...")
        val msg = client.receive()
        logger.info("Received server answer: {}", msg)
        if (msg is Payload.PUBLISHPayload) {
            logger.info("new publish msg:")
            logger.debug(msg.topic) // Topic(topic=/f1/call)
            logger.debug(msg.content) // {messaaggee}
            logger.debug(msg.geofence) // BUFFER (POINT (0 0), 2)
            if (msg.topic.topic == "/$funcName/call") {
                logger.info("new call on the '{}' topic", msg.topic.topic)
                // TODO: call tinyfaas (async)?
                // TODO: send ack
                return "ok"

            } else {
                logger.debug("Wrong topic: ${msg.topic.topic}")
                return "no"
            }
        }
        return "no"
    }

    // publishes result for a function request
    fun sendResult(funcName: String, res: String) { //TODO: what location to send the result?
        client.send(Payload.PUBLISHPayload(Topic("/$funcName/result"),Geofence.circle(location,2.0), res))
        val msg = client.receive()
        logger.info("Received server answer: {}", msg)
        if (msg is Payload.PUBACKPayload) {
            if (msg.reasonCode == ReasonCode.NoMatchingSubscribers) {
                logger.error("No matching subscriber found when sending the result for the function '$funcName'")
            } else {
                logger.info("result sent with reasonCode: {}", msg.reasonCode)
            }
        }
    }

    // publishes an Acknowledgement for receiving and processing a request
    fun sendAck(funcName: String) {
        // TODO: I got your request! Send an ack on "/$funcname/ack"
        // TODO: check if reasonCode is okay. log error if not.
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

}

//fun main() {
//    val f1 = GeoBrokerClient()
//    f1.subscribeFunction("f1")
//    f1.listen("f1") //TODO: call in a coroutine
//    f1.listen("f1")
//    f1.terminate()
//}