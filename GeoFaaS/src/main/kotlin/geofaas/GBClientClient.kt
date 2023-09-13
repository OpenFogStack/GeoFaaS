package geofaas

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

class GBClientClient(loc: Location, debug: Boolean, host: String = "localhost", port: Int = 5559, id: String = "GeoFaaSClient1"): GeoBrokerClient(loc, ClientType.CLIENT, debug, host, port, id) {
    fun callFunction(funcName: String, data: String, fence: Geofence) {
        subscribeFunction(funcName, fence)
        val message = gson.toJson(FunctionMessage(funcName, FunctionAction.CALL, data, TypeCode.NORMAL))
        remoteGeoBroker.send(Payload.PUBLISHPayload(Topic("functions/$funcName/call"), Geofence.circle(location,2.0), message))
        val pubAck = remoteGeoBroker.receive()
        val logMsg = "GeoBroker's Publish ACK by ${mode.name} for the $funcName CALL: {}"
        if (pubAck is Payload.PUBACKPayload && pubAck.reasonCode == ReasonCode.NoMatchingSubscribers) {
            logger.error("$logMsg. Terminating...", pubAck.reasonCode); return } else { logger.info(logMsg, pubAck) }

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
    val parisLoc = Location(48.877366, 2.359708)
//    val frankfurtLocOnly = Location(50.106732,8.663124)
    val client1 = GBClientClient(parisLoc, true, "localhost", 5560)
    val res = client1.callFunction("sieve", "", Geofence.circle(parisLoc, 10.0))
    println(res)
    sleepNoLog(2000, 0)
    client1.terminate()
}