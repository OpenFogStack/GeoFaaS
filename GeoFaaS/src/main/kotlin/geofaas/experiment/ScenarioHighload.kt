package geofaas.experiment

fun main(args: Array<String>) {
    val numClients = args[0].toInt()
    val numRequests = args[1].toInt()
    val debug = args[2].toBoolean()
    val locations = Commons.locPotsdamClients.take(numClients)

    Measurement.log("", -1, "SenarioHighload (numCl/numReq)", "$numClients;$numRequests", null)
    // run the experiment
    FaultToleranceScenarios.runThreaded(
        numClients, numRequests, "sievehard",
        locations,
        4000, 25000, 0, 1, 5, debug
    )
}