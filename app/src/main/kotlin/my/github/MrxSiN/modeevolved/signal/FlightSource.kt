package my.github.MrxSiN.modeevolved.signal

import android.content.Context
import android.location.LocationRequest
import android.os.Handler

import my.github.MrxSiN.modeevolved.core.Logger
import my.github.MrxSiN.modeevolved.engine.SignalSource
import my.github.MrxSiN.modeevolved.trigger.FlightDetector
import my.github.MrxSiN.modeevolved.trigger.Signal
import my.github.MrxSiN.modeevolved.trigger.StateUpdate

/**
 * Whether the device is flying, from the speed and altitude of position fixes.
 *
 * Only satellite fixes carry a speed, and airplane mode leaves satellite
 * positioning on, so this asks for high accuracy. It runs only while a rule
 * needs it, and a short minimum interval lets it share fixes other apps request.
 *
 * Fixes must be fresher here than an area rule's. A speed is a statement about
 * one moment; the fix from before takeoff says nothing about being in the air,
 * and a stale one either side of a flight would start or end it at the wrong time.
 */
class FlightSource(context: Context, handler: Handler, logger: Logger) : SignalSource {

    override val signal: Signal = Signal.FLIGHT

    private val updates = LocationUpdates(
        context,
        handler,
        LocationRequest.QUALITY_HIGH_ACCURACY,
        intervalMillis = REQUEST_INTERVAL_MILLIS,
        minIntervalMillis = 30_000L,
        maxAgeMillis = MAX_AGE_MILLIS,
        logger = logger,
    )
    private var detector = FlightDetector()
    private var publish: ((StateUpdate) -> Unit)? = null

    override fun start(publish: (StateUpdate) -> Unit) {
        this.publish = publish
        detector = FlightDetector()
        updates.start(::emit)
    }

    override fun stop() {
        updates.stop()
        publish?.invoke { it.copy(flying = null) }
        publish = null
    }

    private fun emit(reading: LocationUpdates.Reading) {
        val location = reading.location
        val fix = FlightDetector.Fix(
            speedMetersPerSecond = location.speed.takeIf { location.hasSpeed() },
            altitudeMeters = location.altitude.takeIf { location.hasAltitude() },
        )
        val flying = detector.onFix(fix)
        publish?.invoke { it.copy(flying = flying) }
    }

    private companion object {
        const val REQUEST_INTERVAL_MILLIS = 3 * 60_000L

        /** Under one request interval, so a speed is never carried over from the previous one. */
        const val MAX_AGE_MILLIS = 2 * 60_000L
    }
}
