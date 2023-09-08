package geofaas

import de.hasenburg.geobroker.commons.model.message.Topic
import de.hasenburg.geobroker.commons.model.spatial.Geofence
import kotlinx.serialization.Serializable

object Model {
    enum class ClientType { EDGE, CLIENT, CLOUD }
    enum class FunctionAction { ACK, NACK, CALL, RESULT }
    enum class TypeCode { NORMAL, PIGGY }
    @Serializable
    data class FunctionMessage (val funcName: String, val funcAction: FunctionAction, val data: String, val typeCode: TypeCode)
    @Serializable
    data class GeoFaaSFunction (val name: String)
    @Serializable
    data class ListeningTopic(val topic: Topic, val fence: Geofence)
}