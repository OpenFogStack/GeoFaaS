package geofaas

import com.google.gson.annotations.SerializedName
import de.hasenburg.geobroker.commons.model.message.Topic
import de.hasenburg.geobroker.commons.model.spatial.Geofence

object Model {
    enum class ClientType { EDGE, CLIENT, CLOUD }
    enum class FunctionAction { ACK, NACK, CALL, RESULT }
    enum class TypeCode { NORMAL, PIGGY }

    data class GeoFaaSFunction (val name: String)
    data class ListeningTopic(val topic: Topic, val fence: Geofence)
    data class ListeningTopicPatched(val topic: Topic?, val fence: String)
    //NOTE: null Topic = not listening for any response
    //NOTE2: fence is JSON String, because Geofence is incompatible with GSON and cause stackoverflow
    data class FunctionMessage (val funcName: String, val funcAction: FunctionAction, val data: String,
                                val typeCode: TypeCode, val responseTopicFence: ListeningTopicPatched)
}