package my.github.MrxSiN.modeevolved.trigger

/**
 * Decides whether the device is on board a flying aircraft, from cabin pressure and position fixes.
 *
 * Two kinds of evidence start a flight, either one enough:
 * - Cabin pressure. An airliner's cabin climbs to 1,800–2,400 m within minutes of takeoff, so the
 *   barometer falls by [CLIMB_DROP_HPA] within [CLIMB_WINDOW_MILLIS]. No road climbs that fast, and
 *   weather moves a few hPa an hour. It needs no satellite fix, which a phone in a pocket rarely gets
 *   in a cabin.
 * - Speed. Ground travel rarely passes [TAKEOFF_SPEED], and never passes [CLIMB_SPEED] while also
 *   above [CLIMB_ALTITUDE]. Two such fixes in a row are needed, so one bad satellite fix is not a flight.
 *
 * A flight ends on whichever sign of landing comes first:
 * - the cabin has descended by [DESCENT_RISE_HPA] from its lowest pressure and held steady for
 *   [SETTLE_MILLIS], as it does on the ground;
 * - a fix reports a speed below [LANDED_SPEED], unless fresh pressure still puts the cabin aloft;
 * - nothing has confirmed the flight for [STALE_FLIGHT_MILLIS]. On the ground most fixes come from
 *   Wi-Fi or cell towers and carry no speed; without this a landing seen only by them would keep the
 *   flight going for good.
 *
 * ponytail: a cable car rising 1,000 m in under 12 minutes reads as a takeoff; no road vehicle does.
 *
 * Not thread-safe. The caller delivers everything on one thread.
 */
class FlightDetector {

    /**
     * One position fix. A null field is one the fix does not carry.
     *
     * @param atMillis when the fix was taken, on the monotonic clock.
     */
    data class Fix(val speedMetersPerSecond: Float?, val altitudeMeters: Double?, val atMillis: Long = 0L)

    private class Reading(val atMillis: Long, val hPa: Float)

    var flying: Boolean = false
        private set

    /** The last moment something said the device was still in the air. */
    private var confirmedAtMillis = 0L

    /** Fixes in a row fast enough to be airborne, and when the last of them was taken. */
    private var airborneFixes = 0
    private var airborneAtMillis = 0L

    /** Pressure over the last [CLIMB_WINDOW_MILLIS], oldest first. */
    private val pressures = ArrayDeque<Reading>()

    /** Readings in a row far enough below the window's highest to be a climb. */
    private var climbReadings = 0

    /** The lowest cabin pressure of this flight. */
    private var lowestHpa = Float.MAX_VALUE

    /** Takes [fix] into account and returns whether the device is flying now. */
    fun onFix(fix: Fix): Boolean {
        val at = fix.atMillis
        val speed = fix.speedMetersPerSecond ?: return expireIfStale(at)
        if (!flying) {
            val airborne = isAirborne(speed, fix.altitudeMeters)
            airborneFixes = if (!airborne) 0 else if (airborneFixes > 0 && at - airborneAtMillis <= AIRBORNE_PAIR_MILLIS) airborneFixes + 1 else 1
            if (airborne) airborneAtMillis = at
            if (airborneFixes >= 2) takeOff(at)
        } else if (speed >= LANDED_SPEED) {
            confirmedAtMillis = at
        } else if (!pressureSaysAloft(at)) {
            land()
        }
        return flying
    }

    /** Takes one barometer reading of [hPa], taken at [atMillis] on the monotonic clock, into account. */
    fun onPressure(hPa: Float, atMillis: Long): Boolean {
        while (pressures.isNotEmpty() && atMillis - pressures.first().atMillis > CLIMB_WINDOW_MILLIS) pressures.removeFirst()
        pressures.addLast(Reading(atMillis, hPa))

        if (!flying) {
            val highest = pressures.maxOf { it.hPa }
            climbReadings = if (highest - hPa >= CLIMB_DROP_HPA) climbReadings + 1 else 0
            if (climbReadings >= 2) takeOff(atMillis)
            return flying
        }
        lowestHpa = minOf(lowestHpa, hPa)
        if (hPa < lowestHpa + DESCENT_RISE_HPA) {
            confirmedAtMillis = atMillis
        } else if (settled(atMillis)) {
            land()
        }
        return expireIfStale(atMillis)
    }

    private fun takeOff(atMillis: Long) {
        flying = true
        confirmedAtMillis = atMillis
        lowestHpa = pressures.lastOrNull()?.hPa ?: Float.MAX_VALUE
    }

    private fun land() {
        flying = false
        airborneFixes = 0
        climbReadings = 0
        lowestHpa = Float.MAX_VALUE
        // The climb that started this flight must not start the next one.
        pressures.clear()
    }

    private fun expireIfStale(atMillis: Long): Boolean {
        if (flying && atMillis - confirmedAtMillis >= STALE_FLIGHT_MILLIS) land()
        return flying
    }

    /** Whether the readings of the last [SETTLE_MILLIS] stayed within [STABLE_RANGE_HPA], as on the ground. */
    private fun settled(atMillis: Long): Boolean {
        if (pressures.first().atMillis > atMillis - SETTLE_MILLIS) return false
        var low = Float.MAX_VALUE
        var high = -Float.MAX_VALUE
        for (reading in pressures) {
            if (reading.atMillis < atMillis - SETTLE_MILLIS) continue
            low = minOf(low, reading.hPa)
            high = maxOf(high, reading.hPa)
        }
        return high - low <= STABLE_RANGE_HPA
    }

    /** Whether a recent reading still puts the cabin near its cruising pressure. */
    private fun pressureSaysAloft(atMillis: Long): Boolean {
        val last = pressures.lastOrNull() ?: return false
        return atMillis - last.atMillis <= PRESSURE_FRESH_MILLIS && last.hPa < lowestHpa + DESCENT_RISE_HPA
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

        /** How far apart two fast fixes may be and still count as one run. */
        const val AIRBORNE_PAIR_MILLIS = 10 * 60_000L

        /** About 850 m of climb near sea level: a cabin does it in minutes, a mountain road in half an hour. */
        const val CLIMB_DROP_HPA = 100f

        /** How quickly [CLIMB_DROP_HPA] must happen: about 70 m a minute, above any sustained road climb. */
        const val CLIMB_WINDOW_MILLIS = 12 * 60_000L

        /** A cabin descending to land rises this far above its cruising pressure; a step climb or descent at cruise does not. */
        const val DESCENT_RISE_HPA = 40f

        /** How long the pressure must hold after a descent to count as on the ground, longer than most holding patterns. */
        const val SETTLE_MILLIS = 10 * 60_000L

        /** Weather and the cabin's own regulation stay within this over [SETTLE_MILLIS]. */
        const val STABLE_RANGE_HPA = 3f

        /** A reading older than this says nothing about where the cabin is now. */
        const val PRESSURE_FRESH_MILLIS = 5 * 60_000L

        /** Longer than any gap in evidence at cruise, short enough to let a landed Mode go. */
        const val STALE_FLIGHT_MILLIS = 20 * 60_000L
    }
}
