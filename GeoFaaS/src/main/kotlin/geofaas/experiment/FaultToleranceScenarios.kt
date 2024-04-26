package geofaas.experiment

import de.hasenburg.geobroker.commons.model.spatial.Location
import geofaas.Client
import geofaas.Model.FunctionMessage
import geofaas.Model.RequestID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

object FaultToleranceScenarios {
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

         val threads = Array<Thread?>(numClients){null}
         for (i in 0 until numClients) {
             threads[i] = thread {
                println("Thread $i with id ${Thread.currentThread().id} is running")


                // Wait for all threads to start.
                latch.countDown()
                latch.await()

                // launch the experiment
                 if (arrivalInterval >= 0) { // highload scenario
                     nonMovingCallsWithArrivalInterval(clientsPair[i], numRequests, function, retries, ackAttempts, ackT, resT, arrivalInterval, debug)
                 } else { // outage scenario
                     nonMovingCalls(clientsPair[i], numRequests, function, retries, ackAttempts)
                 }
            }
        }

        // Wait for all threads to finish.
        for (thread in threads) {
            thread!!.join()
        }
         Measurement.close()
    }

    private fun nonMovingCalls(clientPair: Pair<Client, Pair<String, Location>>, numRequests: Int, function: String, retries: Int, ackAttempts: Int) {
        val client = clientPair.first
        val locPair = clientPair.second
        var cloudCounter = 0; var edgeCounter = 0
        Measurement.log(client.id, -1, "Started at", locPair.first, null)
        val elapsed = measureTimeMillis {
            for (i in 1..numRequests) {
                val reqId = RequestID(i, client.id, locPair.first)

                val res: Pair<FunctionMessage?, Long> =
                    client.call(function, "", reqId, retries, ackAttempts, false)
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
                    client.throwSafeException("${client.id}-($i-${locPair.first}): NOOOOOOOOOOOOOOO Response! (${res.second}ms)")
                    exitProcess(0) // terminates current process
                }
            }
        }
        client.shutdown()
        Measurement.log(client.id, elapsed, "Finished", "$numRequests requests", null)
        Measurement.log(client.id, elapsed, "byCloud/Edge", "$cloudCounter;$edgeCounter", null)
    }

    private fun nonMovingCallsWithArrivalInterval(clientPair: Pair<Client, Pair<String, Location>>, numRequests: Int, function: String, retries: Int, ackAttempts: Int, ackT: Int, resT: Int, arrivalInterval: Long, debug: Boolean) {
        val client = clientPair.first
        val locPair = clientPair.second
        val cloudCounter = AtomicInteger(); val edgeCounter = AtomicInteger()
        val tempClients = mutableListOf(client)
        tempClients.addAll( // all clients will start connecting to the broker here
            (2..numRequests).map { i ->
                Client(locPair.second, debug, Commons.brokerAddresses["Potsdam"]!!, 60001, client.id +"($i)",
                    ackT, resT)
            }
        )
        Measurement.log(client.id, -1, "Started at", locPair.first, null)

        val threads = Array<Thread?>(numRequests){null}
        var failedResponse = false
        val elapsed = measureTimeMillis {
            tempClients.forEachIndexed { i, c ->
                if(!failedResponse) {
                    val reqId = RequestID(i+1, c.id, locPair.first)

                    threads[i] = thread {
//                        val startTime = System.currentTimeMillis()
                        val res: Pair<FunctionMessage?, Long> =
                            c.call(function, "", reqId, retries, ackAttempts, false)
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
                            failedResponse = true
                            c.throwSafeException("${c.id}-(${i+1}-${locPair.first}): NOOOOOOOOOOOOOOO Response! (${res.second}ms)")
                            exitProcess(0) // terminates current process
                        }
//                        val endTime = System.currentTimeMillis()
//                        val runtime = endTime - startTime
                        c.shutdown()
                    }
                    Thread.sleep(arrivalInterval * 1000L)
                }
            }
            // Wait for all threads to finish.
            for (thread in threads) {
                thread?.join()
            }
        }
        if(failedResponse) {
            Measurement.log(client.id, elapsed, "Failed", "$numRequests requests", null)
        } else {
            Measurement.log(client.id, elapsed, "Finished", "$numRequests requests", null)
            Measurement.log(client.id, elapsed, "byCloud/Edge", "$cloudCounter;$edgeCounter", null)
        }
    }
}





