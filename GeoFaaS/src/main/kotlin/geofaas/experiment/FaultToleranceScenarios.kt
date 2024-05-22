package geofaas.experiment

import de.hasenburg.geobroker.commons.model.spatial.Location
import geofaas.Client
import geofaas.Model.FunctionMessage
import geofaas.Model.RequestID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

object FaultToleranceScenarios {
    private val gotaNoResponse = AtomicBoolean(false)
     // synchronously starts and calls a given function for each thread in the array.
    fun runThreaded(numClients: Int, numRequests: Int, function: String,
                    locations: List<Pair<String, Location>>,
                    ackT: Int, resT: Int, retries: Int, ackAttempts: Int, arrivalInterval: Long,
                    debug: Boolean) {

        val clientsPair = mutableListOf<Pair<Client, Pair<String, Location>>>()
        clientsPair.addAll( // all clients will start connecting to the broker here
            locations.mapIndexed { i, p ->
                Client(p.second, debug, Commons.brokerAddresses["Potsdam"]!!, 60001, "Client${i+1}",
                    ackT, resT) to p
            }
        )

        // Create a latch to synchronize the start of all threads.
        val latch = java.util.concurrent.CountDownLatch(numClients)

         val threads = Array(numClients) { i ->
             val clientId = i + 1
             val threadName = if (arrivalInterval >= 0) "highload-c$clientId" else "outage-c$clientId"
             Thread(Runnable {
                 println("Thread $threadName with id ${Thread.currentThread().id} is running")

                 // launch the experiment
                 if (arrivalInterval >= 0) { // highload scenario
                     // Wait for all threads to start together.
                     latch.countDown()
                     latch.await()

                     nonMovingCallsWithArrivalInterval(clientsPair[i], numRequests, function, retries, ackAttempts, ackT, resT, arrivalInterval, debug)
                 } else { // outage scenario
                     // Warmup
                     repeat(5000) { z ->
                         clientsPair[i].first.call(function, "", RequestID( -z, clientsPair[i].first.id, clientsPair[i].second.first),
                             retries, ackAttempts, ackTArg = 300, resTArg = 300, isWithCloudRetry = false, isContinousCall = true)
                     }
                     // Wait for all threads to start together.
                     latch.countDown()
                     latch.await()

                     nonMovingCalls(clientsPair[i], numRequests, function, retries, ackAttempts)
                 }
             }, threadName)
         }

         // Call start to initiate execution
         threads.forEach { it.start() }


         // Wait for all threads to finish.
         threads.forEach { it.join() }
         Measurement.close()
    }

    private fun nonMovingCalls(clientPair: Pair<Client, Pair<String, Location>>, numRequests: Int, function: String, retries: Int, ackAttempts: Int) {
        val client = clientPair.first
        val locPair = clientPair.second

        var cloudCounter = 0; var edgeCounter = 0
        Measurement.log(client.id, -1, "Started at", locPair.first, null)
        val elapsed = measureTimeMillis {
            for (i in 1..numRequests) {
                if (gotaNoResponse.get()) {break} // break if a client got no response and finished
                val reqId = RequestID(i, client.id, locPair.first)

                val res: Pair<FunctionMessage?, Long> =
                    client.call(function, "", reqId, retries, ackAttempts, isWithCloudRetry = false, isContinousCall = true)
                // Note: call's run time also depends on number of the retries
                if (res.first != null) {
                    val serverInfo = res.first!!.responseTopicFence
                    if (serverInfo.senderId.contains("Cloud"))
                        cloudCounter++
                    else edgeCounter++

                    Measurement.log(
                        client.id,
                        res.second,
                        "Done",
                        serverInfo.senderId + ";${res.first!!.typeCode}",
                        reqId
                    )
                } // details shows responder server name and type of response (offload, or normal)
                else {
                    gotaNoResponse.set(true)
                    client.throwSafeException("${client.id}-($i-${locPair.first}): NOOOOOOOOOOOOOOO Response! (${res.second}ms)")
                    exitProcess(0) // terminates current process
                }
            }
        }
        client.shutdown()
        if(gotaNoResponse.get()) {
            Measurement.log(client.id, elapsed, "Failed", "$numRequests requests", null)
        } else {
            Measurement.log(client.id, elapsed, "Finished", "$numRequests requests", null)
            Measurement.log(client.id, elapsed, "byCloud/Edge", "$cloudCounter;$edgeCounter", null)
        }
    }

    private fun nonMovingCallsWithArrivalInterval(clientPair: Pair<Client, Pair<String, Location>>, numRequests: Int, function: String, retries: Int, ackAttempts: Int, ackT: Int, resT: Int, arrivalInterval: Long, debug: Boolean) {
        val client = clientPair.first
        val locPair = clientPair.second
        val cloudCounter = AtomicInteger(); val edgeCounter = AtomicInteger()
        val tempClients = mutableListOf(client)
//        tempClients.addAll( // all clients will start connecting to the broker here
//            (2..numRequests).map { i ->
//                Client(locPair.second, debug, Commons.brokerAddresses["Potsdam"]!!, 60001, client.id +"($i)",
//                    ackT, resT)
//            }
//        )
        Measurement.log(client.id, -1, "Started at", locPair.first, null)

        val threads = Array<Thread?>(numRequests){ null }
        val elapsed = measureTimeMillis {
//            tempClients.forEachIndexed { i, c ->
            for (i in 0 until numRequests) {
                if(!gotaNoResponse.get()) {
                    val c = if (i == 0) {
                        client
                    } else {
                        Client(locPair.second, debug, Commons.brokerAddresses["Potsdam"]!!, 60001, client.id + "($i)",
                            ackT, resT)
                    }
                    val reqId = RequestID(i+1, c.id, locPair.first)

                    val shortName = c.id.replace("Client", "c").replace("(", "_").replace(")", "")
                    threads[i] = thread(name = "highload-$shortName") {
//                        val startTime = System.currentTimeMillis()
                        val res: Pair<FunctionMessage?, Long> =
                            c.call(function, "", reqId, retries, ackAttempts, isWithCloudRetry = false, isContinousCall = true)
                        // Note: call's run time also depends on number of the retries
                        if (res.first != null) {
                            val serverInfo = res.first!!.responseTopicFence
                            if (serverInfo.senderId.contains("Cloud"))
                                cloudCounter.incrementAndGet()
                            else edgeCounter.incrementAndGet()

                            Measurement.log(
                                c.id,
                                res.second,
                                "Done",
                                serverInfo.senderId + ";${res.first!!.typeCode}",
                                reqId
                            ) // 'details' shows responder server name and type of response (offload, or normal)
                        } else {
                            gotaNoResponse.set(true)
                            c.throwSafeException("${c.id}-(${i+1}-${locPair.first}): NOOOOOOOOOOOOOOO Response! (${res.second}ms)")
//                            exitProcess(0) // terminates current process
                        }
//                        val endTime = System.currentTimeMillis()
//                        val runtime = endTime - startTime
                        c.shutdown()
                    }
                    Thread.sleep(arrivalInterval * 1000L) // to seconds
                }
            }
            // Wait for all threads to finish.
            for (thread in threads) {
                thread?.join()
            }
        }
        if(gotaNoResponse.get()) {
            Measurement.log(client.id, elapsed, "Failed", "$numRequests requests", null)
        } else {
            Measurement.log(client.id, elapsed, "Finished", "$numRequests requests", null)
            Measurement.log(client.id, elapsed, "byCloud/Edge", "$cloudCounter;$edgeCounter", null)
        }
    }
}





