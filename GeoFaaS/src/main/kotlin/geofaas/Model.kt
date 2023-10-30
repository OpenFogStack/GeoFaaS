package geofaas

import de.hasenburg.geobroker.commons.model.message.Topic
import de.hasenburg.geobroker.commons.model.spatial.Geofence

object Model {
    enum class ClientType { EDGE, CLIENT, CLOUD }
    enum class FunctionAction { ACK, NACK, CALL, RESULT }
    enum class TypeCode { NORMAL, RETRY }

    enum class StatusCode { Success, Failure, // common
        AlreadyExist, NotExist,               // for Sub/unSub
        Retry,                                // for listens with unexpected msg
        WrongBroker,                          // for handling the case another responsible broker exist
        NotConnected                          // for any disGb ack (e.g. subAck) to wrong broker
    }

    data class GeoFaaSFunction (val name: String)
    data class ListeningTopic(val topic: Topic, val fence: Geofence)
    data class ResponseInfoPatched(val senderId: String, val topic: Topic?, val fence: String)
    //NOTE: null Topic = not listening for any response
    //NOTE2: the fence is a JSON String, because Geofence is incompatible with GSON and cause stackoverflow
    data class FunctionMessage (val funcName: String, val funcAction: FunctionAction, val data: String,
                                val typeCode: TypeCode, val receiverId: String,
                                val responseTopicFence: ResponseInfoPatched)
}