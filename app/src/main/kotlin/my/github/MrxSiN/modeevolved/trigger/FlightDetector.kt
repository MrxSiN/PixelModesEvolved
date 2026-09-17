package my.github.MrxSiN.modeevolved.trigger

/**
 * Decides from position fixes whether the device is on board a flying aircraft.
 *
 * Ground travel rarely passes [TAKEOFF_SPEED], and never passes [CLIMB_SPEED]
 * while also above [CLIMB_ALTITUDE]. Once flying, the answer holds until a fix
 * reports a speed below [LANDED_SPEED], so a cruise with few fixes, or fixes
 * without a speed, does not flicker.
 *
 * Not thread-safe. The caller delivers every fix on one thread.
 */
class FlightDetector {

    /** One position fix. A null field is one the fix does not carry. */
    data class Fix(val speedMetersPerSecond: Float?, val altitudeMeters: Double?)

    var flying: Boolean = false
        private set

    /** Takes [fix] into account and returns whether the device is flying now. */
    fun onFix(fix: Fix): Boolean {
        val speed = fix.speedMetersPerSecond ?: return flying
        flying = if (flying) speed >= LANDED_SPEED else isAirborne(speed, fix.altitudeMeters)
        return flying
    }

    private fun isAirborne(speed: Float, altitude: Double?): Boolean =
        speed >= TAKEOFF_SPEED || (speed >= CLIMB_SPEED && (altitude ?: 0.0) >= CLIMB_ALTITUDE)

    companion object {
        /** 360 km/h: faster than any scheduled train or road vehicle. */
        const val TAKEOFF_SPEED = 100f

        /** 180 km/h, counted as flight only together with [CLIMB_ALTITUDE]. */
        const val CLIMB_SPEED = 50f

        /** Meters above sea level; above most roads and railways that allow [CLIMB_SPEED]. */
        const val CLIMB_ALTITUDE = 1_500.0

        /** 90 km/h: an aircraft rolling out or taxiing after landing. */
        const val LANDED_SPEED = 25f
    }
}
