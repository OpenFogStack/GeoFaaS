
import de.hasenburg.geobroker.commons.model.spatial.Location
import de.hasenburg.geobroker.commons.sleepNoLog
import geofaas.Client
import geofaas.ClientGBClient
import geofaas.Model.FunctionMessage
import geofaas.brokerAddresses
import org.apache.logging.log4j.LogManager
import org.junit.After
import org.junit.Test
import org.junit.Assert.*

class FullRemoteTest {

    private val logger = LogManager.getLogger()
    private lateinit var gbhost : String
    private val gbPort = 5560
    private lateinit var client1 :ClientGBClient
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
    private val locParisToPotsdom = listOf(Location(48.922499,2.570801),
        Location(49.188884,3.955078), // paris
//    Location(50.520412,4.702148), // Reims
        Location(50.722547,7.097168), // Belgium
        Location(51.371780,9.624023), // Bonn
        Location(53.199452,10.678711), //Hamburg. out of range
        Location(52.308479,13.073730) // potsdam
    )

//    @Before
//    fun setUp() {
//        logger.info("Running test setUp")
//    }
//
    @After
    fun terminate(){
        logger.info("Running test terminate.")
        client1.terminate()
    }

    @Test
    fun berlinOffloadsToCloud() { // assumes Berlin offloads any request
        gbhost = brokerAddress["Berlin"]!!
        client1 = ClientGBClient(clientLoc["potsdam"]!!, true, gbhost, gbPort, "ClientGeoFaaSTest")
        val res: FunctionMessage? = client1.callFunction("sieve", "", 2.1)
        assertNotNull(res)
        assertEquals("Found 1229 primes under 10000", res?.data)
        assertEquals(gbhost, client1.gbSimpleClient.ip) // client shouldn't switch to the other broker
        assertEquals("{\"wkt\":\"ENVELOPE (-180, 180, 90, -90)\"}" ,res?.responseTopicFence?.fence) // the response should be from the cloud
        client1.terminate()
    }
    @Test
    fun parisRespondsToParisClient() {
        gbhost = brokerAddress["Paris"]!!
        client1 = ClientGBClient(clientLoc["parisNonOverlap"]!!, true, gbhost, gbPort, "ClientGeoFaaSTest")
        val res: FunctionMessage? = client1.callFunction("sieve", "", 2.1)
        assertNotNull(res)
        assertEquals("Found 1229 primes under 10000", res?.data)
        assertEquals(gbhost, client1.gbSimpleClient.ip) // client shouldn't switch to the other broker
        assertNotEquals("{\"wkt\":\"ENVELOPE (-180, 180, 90, -90)\"}" ,res?.responseTopicFence?.fence)// not from the cloud
        client1.terminate()
    }
// Redirects
    @Test
    fun frankfurtClientRedirectsToFrankfurtBroker() {
        gbhost = brokerAddress["Berlin"]!!
        client1 = ClientGBClient(clientLoc["frankfurtEdge"]!!, true, gbhost, gbPort, "ClientGeoFaaSTest")
        assertNotEquals(gbhost, client1.gbSimpleClient.ip) // client should switch to the other broker
        assertEquals(brokerAddress["Frankfurt"]!!, client1.gbSimpleClient.ip)
        client1.terminate()
    }

    @Test
    fun amsterdamClientRedirectsToCloudBrokerAndGetsResponse() {
        gbhost = brokerAddress["Frankfurt"]!!
        client1 = ClientGBClient(clientLoc["amsterdam"]!!, true, gbhost, gbPort, "ClientGeoFaaSTest")
//        assertNotEquals(gbhost, client1.gbSimpleClient.ip) // client should switch to the other broker. UPDATE: functionality changed. will switch after the call
//        assertEquals(brokerAddress["Cloud"]!!, client1.gbSimpleClient.ip)
        val res: FunctionMessage? = client1.callFunction("sieve", "", 2.1)
        assertNotNull(res)
        assertEquals("Found 1229 primes under 10000", res?.data)
        client1.terminate()
    }

    @Test
    fun clientMovesAndCallsGetsResult() {
        client1 = ClientGBClient(locParisToPotsdom.first(), false, brokerAddresses["Paris"]!!, 5560, "ClientGeoFaaSTest")
        locParisToPotsdom.forEach { loc ->
            client1.updateLocation(loc)
            val res: FunctionMessage? = client1.callFunction("sieve", "")
            if(res != null) println("Result: ${res.data}")
            assertNotNull(res)
            assertEquals("Found 1229 primes under 10000", res?.data)
        }
        client1.terminate()
    }

}