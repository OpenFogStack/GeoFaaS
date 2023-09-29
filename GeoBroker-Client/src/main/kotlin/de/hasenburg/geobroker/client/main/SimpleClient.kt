package de.hasenburg.geobroker.client.main

import de.hasenburg.geobroker.commons.communication.SPDealer
import de.hasenburg.geobroker.commons.communication.ZMQProcessManager
import de.hasenburg.geobroker.commons.model.message.*
import de.hasenburg.geobroker.commons.model.message.Payload.*
import de.hasenburg.geobroker.commons.model.spatial.Geofence
import de.hasenburg.geobroker.commons.model.spatial.Location
import de.hasenburg.geobroker.commons.sleepNoLog
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.apache.logging.log4j.LogManager
import kotlin.system.exitProcess

private val logger = LogManager.getLogger()

/**
 * The SimpleClient connects to the GeoBroker running at [ip]:[port].
 * If an [identity] is provided on startup, it announces itself with this identify; otherwise, a default identify is used.
 *
 * It is possible to supply a [socketHWM], for more information on HWM, check out the ZeroMQ documentation.
 * If no HWM is supplied, 1000 is used.
 */
class SimpleClient(val ip: String, port: Int, socketHWM: Int = 1000, val identity: String = "SimpleClient-" + System.nanoTime()) {

    private val spDealer = SPDealer(ip, port, socketHWM)

    fun tearDownClient() {
        if (spDealer.isActive) {
            spDealer.shutdown()
        }
    }

    /**
     * Send the given [payload] to the broker.
     * Returns true, if successful, otherwise false.
     */
    fun send(payload: Payload): Boolean {
        val zMsg = payload.toZMsg(clientIdentifier = identity)
        return spDealer.toSent.trySend(zMsg).isSuccess
    }

    /**
     * Receives a message from the broker, blocks until a message was received.
     * Then, it returns the [Payload] of the message.
     */
    fun receive(): Payload {
        return runBlocking {
            val zMsgTP = spDealer.wasReceived.receive()
            zMsgTP.msg.toPayloadAndId()!!.second
        }
    }

    /**
     * Receives a message from the blocker, blocks as defined by [timeout] in ms.
     * Then, it returns the [Payload] or the message or null, if none was received.
     **/
    fun receiveWithTimeout(timeout: Int): Payload? {
        return runBlocking {
            withTimeoutOrNull(timeout.toLong()) {
                val zMsgTP = spDealer.wasReceived.receive()
                zMsgTP.msg.toPayloadAndId()!!.second
            }
        }
    }

}

fun main() {
    val processManager = ZMQProcessManager()
    val client = SimpleClient("localhost", 5559)

    // connect
    client.send(CONNECTPayload(Location.random()))

    // receive one message
    logger.info("Received server answer: {}", client.receive())

    // subscribe
    client.send(SUBSCRIBEPayload(Topic("test"), Geofence.circle(Location.random(), 2.0)))

    // receive one message
    logger.info("Received server answer: {}", client.receive())

    // wait 5 seconds
    sleepNoLog(5000, 0)

    // disconnect
    client.send(DISCONNECTPayload(ReasonCode.NormalDisconnection))

    client.tearDownClient()
    if (processManager.tearDown(3000)) {
        logger.info("SimpleClient shut down properly.")
    } else {
        logger.fatal("ProcessManager reported that processes are still running: {}",
                processManager.incompleteZMQProcesses)
    }
    exitProcess(0)
}