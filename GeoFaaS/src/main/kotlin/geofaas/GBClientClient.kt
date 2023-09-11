package geofaas

import com.google.gson.Gson
import de.hasenburg.geobroker.commons.model.message.Payload
import de.hasenburg.geobroker.commons.model.message.ReasonCode
import de.hasenburg.geobroker.commons.model.message.Topic
import de.hasenburg.geobroker.commons.model.spatial.Geofence
import de.hasenburg.geobroker.commons.model.spatial.Location
import de.hasenburg.geobroker.commons.sleepNoLog
import geofaas.Model.ClientType
import geofaas.Model.FunctionAction
import geofaas.Model.FunctionMessage
import geofaas.Model.TypeCode
import kotlinx.serialization.json.Json
import org.apache.logging.log4j.LogManager

private val logger = LogManager.getLogger()

class GBClientClient(loc: Location, debug: Boolean, host: String = "localhost", port: Int = 5559, id: String = "GeoFaaSClient1"): GeoBrokerClient(loc, ClientType.CLIENT, debug, host, port, id) {
    fun callFunction(funcName: String, data: String) {
        subscribeFunction(funcName, Geofence.circle(Location(0.0, 0.0), 2.0))
        val message = gson.toJson(FunctionMessage(funcName, FunctionAction.CALL, data, TypeCode.NORMAL))
        remoteGeoBroker.send(Payload.PUBLISHPayload(Topic("functions/$funcName/call"), Geofence.circle(location,2.0), message))
        val pubAck = remoteGeoBroker.receive()
        logger.info("GeoBroker's Publish ACK by ${mode.name} for the $funcName CALL: {}", pubAck)
        if (pubAck is Payload.PUBACKPayload && pubAck.reasonCode == ReasonCode.NoMatchingSubscribers) { return }

        logger.info("Listening for an ack from the server...")
        val ack: FunctionMessage? = listen()
        var res: FunctionMessage? = null
        if (ack != null) {
            if (ack.funcAction == FunctionAction.ACK) {
                logger.info("new Ack received")
                when (ack.typeCode) {
                    TypeCode.NORMAL -> {
                        res = listen()
                    }
                    TypeCode.PIGGY -> {
                        //TODO: Implement if Ack is piggybacked
                    }
                }
            } else { logger.error("expected Ack but received '{}'", ack.funcAction) }
            logger.debug("(any?) response received: {}", res)
            //TODO return the res
        } else { logger.error("null response received from GeoBroker! (Client.Listen())") }
        //TODO: Unsubscribe
    }
}

fun main() {
    val client1 = GBClientClient(Location(0.0, 0.0), true)
    val res = client1.callFunction("sieve", "")
    sleepNoLog(2000, 0)
    client1.terminate()
}