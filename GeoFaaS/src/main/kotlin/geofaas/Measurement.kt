package geofaas

import org.apache.logging.log4j.LogManager

object Measurement {
    private val log4j = LogManager.getLogger("measurement") //Note:  log/measurement.log

    fun log(who: String, time: Long, data: String, misc: String) {
        // Note: you can comma separate the data and misc
        log4j.info("$who (${time}ms) [$data]: {$misc}")
    }

//    fun log(msg: String) {
//        log4j.info(msg)
//    }
}