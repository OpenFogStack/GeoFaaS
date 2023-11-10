package geofaas

import org.apache.logging.log4j.LogManager
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.system.measureTimeMillis

object Measurement {
    private val log4j = LogManager.getLogger("measurement") //Note:  console only
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss")
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SS")
    private val timestamp = dateFormat.format(Date())
    private val f = "logs/measurement-$timestamp.csv"
    private val bufferedWriter: BufferedWriter = try {
        BufferedWriter(FileWriter(f, true))
    } catch (e: IOException) {
        throw RuntimeException("Failed to create BufferedWriter for file", e)
    }
    init {
        bufferedWriter.write("data,who,details,time,timestamp\n")
    }

    fun log(who: String, time: Long, data: String, misc: String) {
        try {
            log4j.info("$who (${time}ms) [$data]: {$misc}")
            val currentTimestamp = timeFormat.format(Date())
            bufferedWriter.write("$data,$who,$misc,$time,$currentTimestamp")
            bufferedWriter.newLine()
            bufferedWriter.flush()
        } catch (e: IOException) {
            throw RuntimeException("Failed to write log message to file: $f", e)
        }
    }

    fun close() {
        try {
            bufferedWriter.close()
        } catch (e: IOException) {
            throw RuntimeException("Failed to close BufferedWriter for file: $f", e)
        }
    }

//    fun log(who: String, time: Long, data: String, misc: String) {
//        // Note: you can comma separate the data and misc
//        log4j.info("$who (${time}ms) [$data]: {$misc}")
//    }

    fun <T> logRuntime(who: String, data: String, misc: String, block: () -> T): T {
        val result: T
        val runtime = measureTimeMillis {
            result = block()
        }
        log(who, runtime, data, misc)
        return result
    }
}