package geofaas

import de.hasenburg.geobroker.commons.model.message.Topic
import de.hasenburg.geobroker.commons.model.spatial.Geofence

object Model {
    enum class ClientType { EDGE, CLIENT, CLOUD }
    enum class FunctionAction { ACK, NACK, CALL, RESULT }
    enum class TypeCode { NORMAL, PIGGY }

    data class FunctionMessage (val funcName: String, val funcAction: FunctionAction, val data: String, val typeCode: TypeCode)
//    @Serializable
    data class GeoFaaSFunction (val name: String)
    data class ListeningTopic(val topic: Topic, val fence: Geofence)
}