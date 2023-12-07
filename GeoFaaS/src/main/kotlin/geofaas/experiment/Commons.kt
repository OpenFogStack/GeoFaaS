package geofaas.experiment

import de.hasenburg.geobroker.commons.model.spatial.Location

object Commons {
    /** Location Pairs */
    val clientLoc = listOf("middleButCloserToParis" to Location(50.289339,3.801270),
        "parisNonOverlap" to Location(48.719961,1.153564),
        "parisOverlap" to Location(48.858391, 2.327385), // overlaps with broker area but the broker is not inside the fence
        "paris1" to Location(48.835797,2.244301), // Boulogne Bilancourt area in Paris
        "reims" to Location(49.246293,4.031982), // East France. inside parisEdge
        "parisEdge" to Location(48.877366, 2.359708),
        "saarland" to Location(49.368066,6.976318),
        "frankfurtEdge" to Location(50.106732,8.663124),
        "darmstadt" to Location(49.883132,8.646240),
        "potsdam" to Location(52.400953,13.060169),
        "franceEast" to Location(47.323931,5.174561),
        "amsterdam" to Location(52.315195,4.894409),
        "hamburg" to Location(53.527248,9.986572),
        "belgium" to Location(50.597186,4.822998)
    )
    val locFranceToPoland = listOf("Paris" to Location(48.936935,2.702637), // paris
        "Reims" to Location(49.282140,4.042969), // Reims
        "Saarland" to Location(49.375220,6.998291), // Saarland
        "Bonn" to Location(50.736455,7.119141), // Bonn
        "Leipzig" to Location(51.426614,12.194824), // Leipzig
        "Potsdam" to Location(52.348763,13.051758), // Potsdam
        "Warsaw" to Location(52.254709,20.939941) // warsaw
    )
    val locBerlinToFrance = listOf("Potsdam" to Location(52.348763,13.051758), // Potsdam
        "Leipzig" to Location(51.426614,12.194824), // Leipzig
        "Bonn" to Location(50.736455,7.119141), // Bonn
        "Saarland" to Location(49.375220,6.998291), // Saarland
        "Reims" to Location(49.282140,4.042969), // Reims
        "Paris" to Location(48.936935,2.702637), // paris
        "Rennes" to Location(48.107431,-1.691895) // rennes
    )
    val locFrankParisBerlin = listOf("Nurnberg" to Location(49.468124,11.074219),
        "Mannheim" to Location(49.482401,8.459473),
        "Dusseldorf" to Location(51.165567,6.811523),
        "Letzebuerg" to Location(49.951220,6.086426),
        "Hessen" to Location(50.597186,9.382324),
        "North Leipzig" to Location(51.631657,12.260742), // berlin is responsible
        "Reims" to Location(49.081062,4.262695),
        "South Paris" to Location(48.502048,2.812500),
        "North Paris" to Location(49.453843,1.735840),
    )

    // Scenarios' locations
    val locScenario1 = listOf("Pankow" to Location(52.565708,13.402634),
        "Wedding" to Location(52.549010,13.342552),
        "Charlottenburg Palace" to Location(52.520713,13.294315),
        "Grunewald" to Location(52.488634,13.254318),
        "Steglitz" to Location(52.452871,13.316116),
        "Zehlendorf" to Location(52.435607,13.260326),
        "Kleinmachnow" to Location(52.414566,13.206940),
        "Duppeler Forst" to Location(52.401790,13.157158), // switches to potsdam broker
        "Babelsberg" to Location(52.403885,13.092270), // inside Potsdam city
        "Bornim" to Location(52.423779,13.016739),
        "Fahrland" to Location(52.469188,13.002663),
        "Ketzin" to Location(52.479017,12.883873), // switches to cloud
        "Havel" to Location(52.477408, 12.811737)
    )
    val locScenario2 = listOf(
        "Pots1" to Location(52.420943, 12.957593),
        "Pots2" to Location(52.243407, 13.110622),
        "Pots3" to Location(52.256774, 12.983982),
        "Pots4" to Location(52.352012, 13.171031),
        "Pots5" to Location(52.450150, 13.121540),
        "Pots6" to Location(52.343565, 12.921389),
        "Pots7" to Location(52.479416, 13.013094),
        "Pots8" to Location(52.370235, 13.051669),
        "Pots9" to Location(52.305309, 13.052397),
        "Pots10" to Location(52.213984, 12.992716),
        "Pots11" to Location(52.441918, 13.116519),
        "Pots12" to Location(52.226793, 13.146610),
        "Pots13" to Location(52.249713, 13.050892),
        "Pots14" to Location(52.383379, 12.988094),
        "Pots15" to Location(52.461356, 13.007009),
        "Pots16" to Location(52.319371, 12.904111),
    )
    val locScenario3 = listOf(
        "Pots1" to Location(52.420943, 12.957593),
        "Pots2" to Location(52.243407, 13.110622),
        "Pots3" to Location(52.256774, 12.983982),
        "Pots4" to Location(52.352012, 13.171031),
        "Pots5" to Location(52.450150, 13.121540),
//        "Pots6" to Location(52.343565, 12.921389),
//        "Pots7" to Location(52.479416, 13.013094),
//        "Pots8" to Location(52.370235, 13.051669),
//        "Pots9" to Location(52.305309, 13.052397),
//        "Pots10" to Location(52.213984, 12.992716),
//        "Pots11" to Location(52.441918, 13.116519),
//        "Pots12" to Location(52.226793, 13.146610),
//        "Pots13" to Location(52.249713, 13.050892),
//        "Pots14" to Location(52.383379, 12.988094),
//        "Pots15" to Location(52.461356, 13.007009),
//        "Pots16" to Location(52.319371, 12.904111),
    )

    /** Broker Addresses for first connection */
    val brokerAddresses = mapOf("Frankfurt" to "141.23.28.205", "FrankfurtW" to "192.168.0.125", // LAN/WLAN address
        "Paris" to "141.23.28.208", "ParisW" to "192.168.0.122", "Potsdam" to "141.23.28.208",
        "Berlin" to "141.23.28.207", "BerlinW" to "192.168.0.116",
        "Cloud" to "141.23.28.209", "CloudW" to "192.168.0.206",
        "Local" to "localhost")

    /** Misc */
    val debug = false
}