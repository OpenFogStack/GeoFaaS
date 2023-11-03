package geofaas

import org.apache.logging.log4j.LogManager

object Measurement {
    private val log4j = LogManager.getLogger("measurement")

    fun log(msg: String) {
        log4j.info(msg)
    }
}