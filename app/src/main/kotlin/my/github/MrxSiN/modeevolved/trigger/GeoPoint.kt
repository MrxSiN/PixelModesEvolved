package my.github.MrxSiN.modeevolved.trigger

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A position on Earth in degrees. */
data class GeoPoint(val latitude: Double, val longitude: Double) {

    /** Great-circle distance in meters, by the haversine formula. */
    fun distanceTo(other: GeoPoint): Double {
        val deltaLatitude = Math.toRadians(other.latitude - latitude)
        val deltaLongitude = Math.toRadians(other.longitude - longitude)
        val a = sin(deltaLatitude / 2).pow(2) +
            cos(Math.toRadians(latitude)) * cos(Math.toRadians(other.latitude)) *
            sin(deltaLongitude / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(a))
    }

    private companion object {
        const val EARTH_RADIUS_METERS = 6_371_008.8
    }
}
