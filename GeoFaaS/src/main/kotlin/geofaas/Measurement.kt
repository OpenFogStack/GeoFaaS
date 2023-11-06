package geofaas

import org.apache.logging.log4j.LogManager
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.system.measureTimeMillis

object Measurement {
    private val log4j = LogManager.getLogger("measurement") //Note:  log/measurement.log

    fun log(who: String, time: Long, data: String, misc: String) {
        // Note: you can comma separate the data and misc
        log4j.info("$who (${time}ms) [$data]: {$misc}")
    }

    fun <T> logRuntime(who: String, data: String, misc: String, block: () -> T): T {
        val result: T
        val runtime = measureTimeMillis {
            result = block()
        }
        log(who, runtime, data, misc)
        return result
    }

    fun logStartHeader(msg: String) {
        log4j.debug(".: Beginning of '$msg' :.")
    }
    fun logEndHeader(msg: String) {
        log4j.debug(".: Ending of '$msg' :.")
    }
}