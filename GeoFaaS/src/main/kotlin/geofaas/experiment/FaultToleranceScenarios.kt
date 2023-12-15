package geofaas.experiment

import de.hasenburg.geobroker.commons.model.spatial.Location
import geofaas.Client
import geofaas.Model.FunctionMessage
import geofaas.Model.RequestID
import kotlin.concurrent.thread
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

object FaultToleranceScenarios {
     // synchronously starts and calls a given function for each thread in the array.
    fun runThreaded(numClients: Int, numRequests: Int,
                    locations: List<Pair<String, Location>>,
                    ackT: Int, resT: Int, retries: Int, ackAttempts: Int) {

        val clientsPair = mutableListOf<Pair<Client, Pair<String, Location>>>()
        clientsPair.addAll( // all clients will start connecting to the broker
            locations.mapIndexed { i, p ->
                Client(p.second, Commons.debug, Commons.brokerAddresses["Potsdam"]!!, 5560, "Client${i+1}",
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
                nonMovingCalls(clientsPair[i], numRequests, retries, ackAttempts)
            }
        }

        // Wait for all threads to finish.
        for (thread in threads) {
            thread!!.join()
        }
         Measurement.close()
    }

    private fun nonMovingCalls(clientPair: Pair<Client, Pair<String, Location>>, numRequests: Int, retries: Int, ackAttempts: Int) {
        val client = clientPair.first
        val locPair = clientPair.second
        var cloudCounter = 0; var edgeCounter = 0
        Measurement.log(client.id, -1, "Started at", locPair.first, null)
        val elapsed = measureTimeMillis {
            for (i in 1..numRequests) {
                val reqId = RequestID(i, client.id, locPair.first)

                val res: Pair<FunctionMessage?, Long> = client.call("sieve", "", reqId, retries, ackAttempts)
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
}





