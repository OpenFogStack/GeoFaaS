package geofaas.experiment

fun main(args: Array<String>) {
    val numClients = args[0].toInt()
    val numRequests = args[1].toInt()
    val debug = args[2].toBoolean()
    val locations = Commons.locPotsdamClients.take(numClients)

//    Measurement.log("", -1, "ScenarioOutage (numCl/numReq)", "$numClients;$numRequests", null)
    // run the experiment
    FaultToleranceScenarios.runThreaded(
        numClients, numRequests, "sieve",
        locations,
//        200, 300, 1, 1, -1L, debug
        70, 70, 1, 1, -1L, debug
    )
}