package de.hasenburg.geobroker.commons.model.spatial

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.apache.logging.log4j.LogManager
import org.locationtech.spatial4j.distance.DistanceUtils
import org.locationtech.spatial4j.shape.Point
import kotlin.math.min
import kotlin.random.Random

private val logger = LogManager.getLogger()

@Serializable
class Location(@Serializable(with = PointWKTSerializer::class) @SerialName("wkt") val point: Point) {

    /**
     * Creates a location with the given lat/lon coordinates.
     *
     * @param lat - the latitude (Breitengrad)
     * @param lon - the longitude (Längengrad)
     */
    constructor(lat: Double, lon: Double) : this(SpatialContextK.GEO.shapeFactory.pointLatLon(lat, lon))

    val lat: Double
        get() = point.lat

    val lon: Double
        get() = point.lon

    /**
     * @param distance - distance from starting location in km
     * @param direction - direction (0 - 360)
     */
    fun locationInDistance(distance: Double, direction: Double): Location {
        return Location(SpatialContextK.GEO.distCalc.pointOnBearing(this.point,
                distance * DistanceUtils.KM_TO_DEG,
                direction,
                SpatialContextK.GEO,
                SpatialContextK.GEO.shapeFactory.pointLatLon(0.0, 0.0)))
    }

    /**
     * Distance between this location and the given one, as determined by the Haversine formula, in radians
     *
     * @param toL - the other location
     * @return distance in radians
     */
    fun distanceRadiansTo(toL: Location): Double {
        return SpatialContextK.GEO.distCalc.distance(point, toL.point)
    }

    /**
     * Distance between this location and the given one, as determined by the Haversine formula, in km
     *
     * @param toL - the other location
     * @return distance in km or -1 if one location is undefined
     */
    fun distanceKmTo(toL: Location): Double {
        return distanceRadiansTo(toL) * DistanceUtils.DEG_TO_KM
    }

    companion object {
        /**
         * Creates a random location (Not inclusive of (-90, 0))
         */
        @JvmOverloads
        fun random(random: Random = Random.Default): Location {
            // there have been rounding errors
            return Location(min((random.nextDouble() * -180.0) + 90.0, 90.0),
                    min((random.nextDouble() * -360.0) + 180.0, 180.0))
        }

        /**
         * Creates a random location that is inside the given Geofence.
         *
         * @param geofence - may not be a geofence that crosses any datelines!!
         * @return a random location or null if the geofence crosses a dateline
         */
        @JvmOverloads
        fun randomInGeofence(geofence: Geofence, random: Random = Random.Default): Location? {
            var result: Location
            var i = 0
            do {
                // generate lat/lon in bounding box
                val lat = random.nextDouble(geofence.boundingBoxSouthWest.lat, geofence.boundingBoxNorthEast.lat)
                val lon = random.nextDouble(geofence.boundingBoxSouthWest.lon, geofence.boundingBoxNorthEast.lon)

                // create location and hope it is in geofence
                result = Location(lat, lon)
            } while (!geofence.contains(result) && ++i < 1000)

            return if (geofence.contains(result)) {
                // location was in geofence, so let's return it
                result
            } else {
                // none was found
                null
            }
        }

    }

    /*****************************************************************
     * Generated methods
     ****************************************************************/

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Location

        if (point.lat != other.point.lat) return false

        return true
    }

    override fun hashCode(): Int {
        return point.hashCode()
    }

    override fun toString(): String {
        return "Location(lat=$lat, lon=$lon)"
    }

}

/*****************************************************************
 * Json Serialization
 ****************************************************************/

fun Location.toJson(): String {
    return Json.encodeToString(Location.serializer(), this)
}

fun String.toLocation(): Location {
    return Json.decodeFromString(Location.serializer(), this)
}

object PointWKTSerializer : KSerializer<Point> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Location", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Point) {
        encoder.encodeString(SpatialContextK.GEO.formats.wktWriter.toString(value))
    }

    override fun deserialize(decoder: Decoder): Point {
        val reader = SpatialContextK.GEO.formats.wktReader as CustomWKTReader
        return reader.parse(decoder.decodeString()) as Point
    }
}
