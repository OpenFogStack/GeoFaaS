
import de.hasenburg.geobroker.commons.model.spatial.Location
import geofaas.GBClientClient
import geofaas.Model.FunctionMessage
import org.apache.logging.log4j.LogManager
import org.junit.Test
import org.junit.Assert.*

class FullRemoteTest {

    private val logger = LogManager.getLogger()
    private lateinit var gbhost : String
    private val gbPort = 5560
    private lateinit var client1 :GBClientClient
    private val brokerAddress = mapOf("Frankfurt" to "141.23.28.205",
        "Paris" to "141.23.28.208",
        "Berlin" to "141.23.28.207",
        "Cloud" to "141.23.28.209")
    private val clientLoc = mapOf("middleButCloserToParis" to Location(50.289339,3.801270),
        "parisNonOverlap" to Location(48.719961,1.153564),
        "parisOverlap" to Location(48.858391, 2.327385), // overlaps with broker area but the broker is not inside the fence
        "paris2" to Location(48.835797,2.244301), // Boulogne Bilancourt area in Paris
        "parisEdge" to Location(48.877366, 2.359708),
        "frankfurtEdge" to Location(50.106732,8.663124),
        "potsdam" to Location(52.400953,13.060169),
        "franceEast" to Location(47.323931,5.174561),
        "amsterdam" to Location(52.315195,4.894409),
        "hamburg" to Location(53.527248,9.986572),
        "belgium" to Location(50.597186,4.822998)
    )

//    @Before
//    fun setUp() {
//        logger.info("Running test setUp")
//    }
//
//    @After
//    fun terminate(){
//        logger.info("Running test terminate.")
//        client1.terminate()
//    }

    @Test
    fun berlinOffloadsToCloud() { // assumes Berlin offloads any request
        gbhost = brokerAddress["Berlin"]!!
        client1 = GBClientClient(clientLoc["potsdam"]!!, true, gbhost, gbPort, "ClientGeoFaaSTest")
        val res: FunctionMessage? = client1.callFunction("sieve", "", 2.1)
        assertNotNull(res)
        assertEquals("Found 1229 primes under 10000", res?.data)
        assertEquals(gbhost, client1.remoteGeoBroker.ip) // client shouldn't switch to the other broker
        assertEquals("{\"wkt\":\"ENVELOPE (-180, 180, 90, -90)\"}" ,res?.responseTopicFence?.fence) // the response should be from the cloud
        client1.terminate()
    }
    @Test
    fun parisRespondsToParisClient() {
        gbhost = brokerAddress["Paris"]!!
        client1 = GBClientClient(clientLoc["parisNonOverlap"]!!, true, gbhost, gbPort, "ClientGeoFaaSTest")
        val res: FunctionMessage? = client1.callFunction("sieve", "", 2.1)
        assertNotNull(res)
        assertEquals("Found 1229 primes under 10000", res?.data)
        assertEquals(gbhost, client1.remoteGeoBroker.ip) // client shouldn't switch to the other broker
        assertNotEquals("{\"wkt\":\"ENVELOPE (-180, 180, 90, -90)\"}" ,res?.responseTopicFence?.fence)// not from the cloud
        client1.terminate()
    }
// Redirects
    @Test
    fun frankfurtClientRedirectsToFrankfurtBroker() {
        gbhost = brokerAddress["Berlin"]!!
        client1 = GBClientClient(clientLoc["frankfurtEdge"]!!, true, gbhost, gbPort, "ClientGeoFaaSTest")
        assertNotEquals(gbhost, client1.remoteGeoBroker.ip) // client should switch to the other broker
        assertEquals(brokerAddress["Frankfurt"]!!, client1.remoteGeoBroker.ip)
        client1.terminate()
    }

    @Test
    fun amsterdamClientRedirectsToCloudBrokerAndGetsResponse() {
        gbhost = brokerAddress["Frankfurt"]!!
        client1 = GBClientClient(clientLoc["amsterdam"]!!, true, gbhost, gbPort, "ClientGeoFaaSTest")
        assertNotEquals(gbhost, client1.remoteGeoBroker.ip) // client should switch to the other broker
        assertEquals(brokerAddress["Cloud"]!!, client1.remoteGeoBroker.ip)
        val res: FunctionMessage? = client1.callFunction("sieve", "", 2.1)
        assertNotNull(res)
        assertEquals("Found 1229 primes under 10000", res?.data)
        client1.terminate()
    }

}