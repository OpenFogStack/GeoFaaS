package geofaas.experiment

fun main(args: Array<String>) {
    val numClients = args[0].toInt()
    val numRequests = args[1].toInt()
    val locations = Commons.locPotsdamClients.take(numClients)

    Measurement.log("", -1, "SenarioOutage (numCl/numReq)", "$numClients;$numRequests", null)
    // run the experiment
    FaultToleranceScenarios.runThreaded(
        numClients, numRequests,
        locations,
        2000, 4000, 0, 1
    )
}