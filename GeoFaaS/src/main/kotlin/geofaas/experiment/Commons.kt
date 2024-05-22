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
        "Havel" to Location(52.477408, 12.811737),
        "Havel2" to Location(52.462820, 12.740869),
        "Havel3" to Location(52.448292, 12.691420)
    )

    val locScenario1Long = listOf("Pankow" to Location(52.565708,13.402634),
        "Pankow-0" to Location(52.56332257142857,13.394050857142858),
        "Pankow-1" to Location(52.56093714285714,13.385467714285715),
        "Pankow-2" to Location(52.55855171428571,13.376884571428572),
        "Pankow-3" to Location(52.55616628571428,13.36830142857143),
        "Pankow-4" to Location(52.553780857142854,13.359718285714287),
        "Pankow-5" to Location(52.551395428571425,13.351135142857144),
        "Wedding" to Location(52.54901,13.342552),
        "Wedding-0" to Location(52.54496757142857,13.335661),
        "Wedding-1" to Location(52.54092514285714,13.32877),
        "Wedding-2" to Location(52.53688271428571,13.321879000000001),
        "Wedding-3" to Location(52.53284028571428,13.314988000000001),
        "Wedding-4" to Location(52.52879785714285,13.308097000000002),
        "Wedding-5" to Location(52.52475542857142,13.301206000000002),
        "Charlottenburg Palace" to Location(52.520713,13.294315),
        "Charlottenburg Palace-0" to Location(52.51613028571428,13.288601142857143),
        "Charlottenburg Palace-1" to Location(52.511547571428565,13.282887285714287),
        "Charlottenburg Palace-2" to Location(52.50696485714285,13.27717342857143),
        "Charlottenburg Palace-3" to Location(52.50238214285713,13.271459571428574),
        "Charlottenburg Palace-4" to Location(52.49779942857141,13.265745714285718),
        "Charlottenburg Palace-5" to Location(52.493216714285694,13.260031857142861),
        "Grunewald" to Location(52.488634,13.254318),
        "Grunewald-0" to Location(52.483525,13.263146285714285),
        "Grunewald-1" to Location(52.478416,13.27197457142857),
        "Grunewald-2" to Location(52.473307000000005,13.280802857142856),
        "Grunewald-3" to Location(52.46819800000001,13.289631142857141),
        "Grunewald-4" to Location(52.46308900000001,13.298459428571427),
        "Grunewald-5" to Location(52.45798000000001,13.307287714285712),
        "Steglitz" to Location(52.452871,13.316116),
        "Steglitz-0" to Location(52.45040471428572,13.308145999999999),
        "Steglitz-1" to Location(52.44793842857143,13.300175999999999),
        "Steglitz-2" to Location(52.44547214285715,13.292205999999998),
        "Steglitz-3" to Location(52.443005857142865,13.284235999999998),
        "Steglitz-4" to Location(52.44053957142858,13.276265999999998),
        "Steglitz-5" to Location(52.438073285714296,13.268295999999998),
        "Zehlendorf" to Location(52.435607,13.260326),
        "Zehlendorf-0" to Location(52.43260114285714,13.252699428571427),
        "Zehlendorf-1" to Location(52.42959528571428,13.245072857142855),
        "Zehlendorf-2" to Location(52.42658942857142,13.237446285714283),
        "Zehlendorf-3" to Location(52.42358357142856,13.22981971428571),
        "Zehlendorf-4" to Location(52.4205777142857,13.222193142857138),
        "Zehlendorf-5" to Location(52.41757185714284,13.214566571428566),
        "Kleinmachnow" to Location(52.414566,13.20694),
        "Kleinmachnow-0" to Location(52.41274085714286,13.199828285714286), // Moved to Potsdam
        "Kleinmachnow-1" to Location(52.410915714285714,13.192716571428573),
        "Kleinmachnow-2" to Location(52.40909057142857,13.18560485714286),
        "Kleinmachnow-3" to Location(52.40726542857143,13.178493142857146),
        "Kleinmachnow-4" to Location(52.405440285714285,13.171381428571433),
        "Kleinmachnow-5" to Location(52.40361514285714,13.16426971428572),
        "Duppeler Forst" to Location(52.40179,13.157158),
        "Duppeler Forst-0" to Location(52.40208928571428,13.147888285714286),
        "Duppeler Forst-1" to Location(52.40238857142857,13.138618571428571),
        "Duppeler Forst-2" to Location(52.40268785714285,13.129348857142856),
        "Duppeler Forst-3" to Location(52.402987142857135,13.120079142857142),
        "Duppeler Forst-4" to Location(52.40328642857142,13.110809428571427),
        "Duppeler Forst-5" to Location(52.403585714285704,13.101539714285712),
        "Babelsberg" to Location(52.403885,13.09227),
        "Babelsberg-0" to Location(52.406727000000004,13.081479857142856),
        "Babelsberg-1" to Location(52.409569000000005,13.070689714285713),
        "Babelsberg-2" to Location(52.412411000000006,13.05989957142857),
        "Babelsberg-3" to Location(52.41525300000001,13.049109428571427),
        "Babelsberg-4" to Location(52.41809500000001,13.038319285714284),
        "Babelsberg-5" to Location(52.42093700000001,13.02752914285714),
        "Bornim" to Location(52.423779,13.016739),
        "Bornim-0" to Location(52.430266,13.014728142857143),
        "Bornim-1" to Location(52.436753,13.012717285714286),
        "Bornim-2" to Location(52.44324,13.01070642857143),
        "Bornim-3" to Location(52.449727,13.008695571428573),
        "Bornim-4" to Location(52.456214,13.006684714285717),
        "Bornim-5" to Location(52.462701,13.00467385714286),
        "Fahrland" to Location(52.469188,13.002663),
        "Fahrland-0" to Location(52.47059214285714,12.985693),
        "Fahrland-1" to Location(52.47199628571428,12.968722999999999),
        "Fahrland-2" to Location(52.47340042857142,12.951752999999998),
        "Fahrland-3" to Location(52.474804571428564,12.934782999999998), // Move to Cloud
        "Fahrland-4" to Location(52.476208714285704,12.917812999999997),
        "Fahrland-5" to Location(52.477612857142844,12.900842999999997),
        "Ketzin" to Location(52.479017,12.883873),
        "Ketzin-0" to Location(52.47878714285714,12.873567857142858),
        "Ketzin-1" to Location(52.47855728571429,12.863262714285716),
        "Ketzin-2" to Location(52.47832742857143,12.852957571428574),
        "Ketzin-3" to Location(52.47809757142858,12.842652428571432),
        "Ketzin-4" to Location(52.47786771428572,12.83234728571429),
        "Ketzin-5" to Location(52.47763785714287,12.822042142857148),
        "Havel" to Location(52.477408,12.811737),
        "Havel-0" to Location(52.475324,12.801613000000001),
        "Havel-1" to Location(52.473240000000004,12.791489000000002),
        "Havel-2" to Location(52.47115600000001,12.781365000000003),
        "Havel-3" to Location(52.46907200000001,12.771241000000003),
        "Havel-4" to Location(52.466988000000015,12.761117000000004),
        "Havel-5" to Location(52.46490400000002,12.750993000000005),
        "Havel2" to Location(52.46282,12.740869),
        "Havel2-0" to Location(52.46074457142857,12.733804857142857),
        "Havel2-1" to Location(52.45866914285714,12.726740714285715),
        "Havel2-2" to Location(52.45659371428571,12.719676571428572),
        "Havel2-3" to Location(52.45451828571428,12.712612428571429),
        "Havel2-4" to Location(52.45244285714285,12.705548285714286),
        "Havel2-5" to Location(52.45036742857142,12.698484142857144),
        "Havel3" to Location(52.448292,12.69142),
    )

    val locPotsdamClients = listOf(
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

    /** Broker Addresses for first connection */
    val brokerAddresses = mapOf("Frankfurt" to "141.23.28.205", "FrankfurtW" to "192.168.0.125", // LAN/WLAN address
        "Paris" to "141.23.28.208", "ParisW" to "192.168.0.122", "Potsdam" to "141.23.28.208",
        "Berlin" to "141.23.28.207", "BerlinW" to "192.168.0.116",
        "Cloud" to "141.23.28.209", "CloudW" to "192.168.0.206", "CloudGCP" to "35.246.90.119",
        "GCPEdge" to "34.32.35.36",
        "Local" to "localhost")

    /** Misc */
    val debug = false //TODO replace it with cmd argument project-wide
}