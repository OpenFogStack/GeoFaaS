package geofaas

import kotlinx.serialization.Serializable

object Model {
    enum class FunctionAction { ACK, CALL, RESULT }
    @Serializable
    data class FunctionMessage (val funcName: String, val funcAction: FunctionAction, val data: String)
}