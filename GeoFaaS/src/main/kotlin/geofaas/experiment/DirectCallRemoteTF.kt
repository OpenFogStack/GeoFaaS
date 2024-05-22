package geofaas.experiment

import geofaas.Model.RequestID
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

fun main(args: Array<String>) {
//    val numClients = args[0].toInt()
//    val numRequests = args[1].toInt()
//    val debug = args[2].toBoolean()
//    val locations = Commons.locPotsdamClients.take(numClients)
//
////    Measurement.log("", -1, "ScenarioHighload (numCl/numReq)", "$numClients;$numRequests", null)
//    // run the experiment
//    FaultToleranceScenarios.runThreaded(
//        numClients, numRequests, "sievehard",
//        locations,
//        6000, 25000, 0, 1, 1, debug
//    )

    if (args.size < 2) {
        println("Usage: <number of threads> <number of times>")
        return
    }

    val numberOfClients = args[0].toIntOrNull() ?: return
    val numberOfRequests = args[1].toIntOrNull() ?: return
    val locations = Commons.locPotsdamClients.take(numberOfClients)
    val client = OkHttpClient()


    // Create a latch to synchronize the start of all threads.
    val latch = java.util.concurrent.CountDownLatch(numberOfClients)

//    val executor = Executors.newFixedThreadPool(numberOfClients)

    val threads = Array(numberOfClients) { i ->
        val clientId = "c${i + 1}"
        val threadName = clientId
        Thread(Runnable {
            latch.countDown()
            latch.await()

            for (r in 1..numberOfRequests) {

                Measurement.logRuntime(clientId, "Done", "", RequestID( r, clientId, locations.first().first)) {
                    val request = Request.Builder()
                        .url("http://x.x.x.x:8000/sieve")
                        .build()

                    var response : Response? = null
//                    Measurement.logRuntime(clientId, "RESULT;Received", "", RequestID( r, clientId, locations.first().first)) {
//                    }
                    response = client.newCall(request).execute()

                    response?.close()
                }
            }

        }, threadName)
    }

    // Call start to initiate execution
    threads.forEach { it.start() }


    // Wait for all threads to finish.
    threads.forEach { it.join() }
    Measurement.close()

}