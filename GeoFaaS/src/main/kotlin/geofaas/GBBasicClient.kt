package geofaas

import de.hasenburg.geobroker.commons.communication.SPDealer
import de.hasenburg.geobroker.commons.model.message.*
import de.hasenburg.geobroker.commons.model.message.Payload.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

// this class is a 'SimpleClient.kt' clone from 'github.com/MoeweX/geobroker' for development purposes

/**
 * The GBBasicClient (SimpleClient) connects to the GeoBroker running at [ip]:[port].
 * If an [identity] is provided on startup, it announces itself with this identify; otherwise, a default identify is used.
 *
 * It is possible to supply a [socketHWM], for more information on HWM, check out the ZeroMQ documentation.
 * If no HWM is supplied, 1000 is used.
 */
class GBBasicClient (val ip: String, val port: Int, socketHWM: Int = 1000, val identity: String = "GBBasicClient-" + System.nanoTime()){

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