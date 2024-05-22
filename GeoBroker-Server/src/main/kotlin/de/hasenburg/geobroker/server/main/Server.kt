package de.hasenburg.geobroker.server.main

import de.hasenburg.geobroker.commons.setLogLevel
import de.hasenburg.geobroker.server.main.server.*
import de.hasenburg.geobroker.server.main.server.other.SingleNoGeoServerLogic
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.HTTPServer
import io.prometheus.client.hotspot.DefaultExports
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import java.io.IOException

private val logger = LogManager.getLogger()

/**
 * This is a command line application for the GeoBroker server.
 * When you do not provide any command line arguments, GeoBroker is started with the default configuration.
 * You can also provide a file path from which the configuration will be parsed.
 */
fun main(args: Array<String>) {
//    setLogLevel(logger, Level.DEBUG)  ////////////////// <---- \\\\\\\\\\\\\\\ //\\

    // read in configuration
    val configuration: Configuration = if (args.isNotEmpty()) {
        readConfiguration(args[0])
    } else {
        getDefaultConfiguration()
    }

    // setup prometheus
    var prometheusServer: HTTPServer? = null
    if (configuration.prometheusPort != -1) {
        try {
            prometheusServer = HTTPServer(configuration.prometheusPort)
            logger.info("Prometheus server started, reachable at port {}",
                    configuration.prometheusPort)
        } catch (e: IOException) {
            logger.warn("Prometheus can't start server at {}",
                    configuration.prometheusPort,
                    e)
        }
        // Expose stats about jvm
        DefaultExports.initialize()
    } else {
        logger.info("Starting without prometheus.")
    }

    // setup server logic
    val logic: IServerLogic = when (configuration.mode) {
        Mode.single -> {
            logger.info("GeoBroker is configured to run standalone")
            SingleGeoBrokerServerLogic()
        }
        Mode.disgb_subscriberMatching -> {
            logger.info("GeoBroker is configured to run geo-distributed (subscriber matching)")
            DisGBSubscriberMatchingServerLogic()
        }
        Mode.disgb_publisherMatching -> {
            logger.info("GeoBroker is configured to run geo-distributed (publisher matching)")
            DisGBPublisherMatchingServerLogic()
        }
        Mode.single_noGeo -> {
            logger.info("[OtherMode]: GeoBroker is configured to run without doing GeoChecks.")
            SingleNoGeoServerLogic()
        }
    }

    // start lifecycle
    val lifecycle = ServerLifecycle(logic)
    logger.info("Starting lifecycle of broker {}", configuration.brokerId)
    logger.info("Config: {}", configuration.toString())
    lifecycle.run(configuration)

    // shutdown
    logger.info("End of lifecycle reached, shutting down")
    prometheusServer?.run {
        stop()
        CollectorRegistry.defaultRegistry.clear();
        logger.info("Stopped prometheus")
    }

}
