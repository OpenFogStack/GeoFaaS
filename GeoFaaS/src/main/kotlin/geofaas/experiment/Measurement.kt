package geofaas.experiment

import org.apache.logging.log4j.LogManager
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.system.measureTimeMillis
import geofaas.Model.RequestID

object Measurement {
    private val threadLocalFileMap = ThreadLocal<BufferedWriter?>()

    private val log4j = LogManager.getLogger("measurement") //Note:  console only
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss")
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SS")
    private val timestamp = dateFormat.format(Date())

    fun log(who: String, time: Long, event: String, details: String, reqId: RequestID?) {
        try {
            val currentTimestamp = timeFormat.format(Date())
            val fileWriter = getOrCreateFileWriter()
            fileWriter.write("$event,$who,$details,$time,$currentTimestamp")
            if (reqId != null) {
                fileWriter.write(",${reqId.reqNum};${reqId.clientId};${reqId.place}")
                log4j.info("$who (${time}ms) [$event]: {$details} req: ${reqId.clientId}-${reqId.reqNum}@${reqId.place}")
            } else {
                log4j.info("$who (${time}ms) [$event]: {$details}")
            }
            fileWriter.newLine()
            fileWriter.flush()
        } catch (e: IOException) {
            throw RuntimeException("Failed to write log message to file", e)
        }
    }

    private fun getOrCreateFileWriter(): BufferedWriter {
        var fileWriter = threadLocalFileMap.get()
        if (fileWriter == null) {
            val timestamp = dateFormat.format(Date())
            val threadName = Thread.currentThread().name.replace(" ", "_")
            val fileName = "logs/meas-$timestamp-$threadName.csv"
            try {
                fileWriter = BufferedWriter(FileWriter(fileName, true))
                fileWriter.write("event,who,details,time,timestamp,requestId\n")
                threadLocalFileMap.set(fileWriter)
            } catch (e: IOException) {
                throw RuntimeException("Failed to create BufferedWriter for file", e)
            }
        }
        return fileWriter
    }


    fun close() {
        val fileWriter = threadLocalFileMap.get()
        if (fileWriter != null) {
            try {
                fileWriter.close()
            } catch (e: IOException) {
                throw RuntimeException("Failed to close BufferedWriter for file", e)
            } finally {
                threadLocalFileMap.remove()
            }
        }
    }

    fun <T> logRuntime(who: String, event: String, details: String, reqId: RequestID?, block: () -> T): T {
        val result: T
        val runtime = measureTimeMillis {
            result = block()
        }
        log(who, runtime, event, details, reqId)
        return result
    }
}