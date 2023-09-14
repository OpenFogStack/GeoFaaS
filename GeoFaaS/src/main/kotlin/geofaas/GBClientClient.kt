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
    fun callFunction(funcName: String, data: String, pubFence: Geofence, subFence: Geofence): String? {
        val sub: MutableSet<Model.ListeningTopic>? = subscribeFunction(funcName, subFence)
        if (sub != null) {
            val message = gson.toJson(FunctionMessage(funcName, FunctionAction.CALL, data, TypeCode.NORMAL))
            remoteGeoBroker.send(Payload.PUBLISHPayload(Topic("functions/$funcName/call"), pubFence, message))
            val pubAck = remoteGeoBroker.receive()
            val logMsg = "GeoBroker's 'Publish ACK' by ${mode.name} for the '$funcName' CALL: {}"
            if (pubAck is Payload.PUBACKPayload) {
                when (pubAck.reasonCode) {
                    ReasonCode.GrantedQoS0 -> logger.info(logMsg, pubAck)
                    ReasonCode.NoMatchingSubscribersButForwarded -> logger.warn(logMsg, pubAck.reasonCode)
                    ReasonCode.NoMatchingSubscribers -> {
                        logger.error("$logMsg. Terminating...", pubAck.reasonCode)
                        return null
                    }
                    ReasonCode.NotConnectedOrNoLocation -> {
                        logger.error(logMsg, pubAck)
                        return null
                    }
                    else -> logger.warn(logMsg, pubAck)
                }
            } else { logger.error("Unexpected! $logMsg", pubAck); return null }

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
                            return null //TODO: Implement if Ack is piggybacked
                        }
                    }
                } else { logger.error("expected Ack but received '{}'", ack.funcAction)}
                logger.debug("(any?) response received: {}", res)
                return res.toString()
            } else { logger.error("null response received from GeoBroker! (Client.Listen())") }
            return null
            //TODO: Unsubscribe
        }
        logger.error("Call function failed! Failed to subscribe to /result and /ack")
        return null
    }
}

fun main() {
//    val parisLoc = Location(48.877366, 2.359708)
    val frankfurtLocOnly = Location(50.106732,8.663124); val parisLoc = frankfurtLocOnly
    val client1 = GBClientClient(parisLoc, true, "localhost", 5560)
    val res: String? = client1.callFunction("sieve", "", pubFence = Geofence.circle(parisLoc, 10.0), subFence = Geofence.circle(parisLoc, 10.0))
    println("Result: $res")
    sleepNoLog(2000, 0)
    client1.terminate()
}