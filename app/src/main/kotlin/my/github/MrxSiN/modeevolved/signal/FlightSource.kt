package my.github.MrxSiN.modeevolved.signal

import android.content.Context
import android.location.Location
import android.location.LocationRequest
import android.os.Handler

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
 */
class FlightSource(context: Context, handler: Handler) : SignalSource {

    override val signal: Signal = Signal.FLIGHT

    private val updates = LocationUpdates(
        context,
        handler,
        LocationRequest.QUALITY_HIGH_ACCURACY,
        intervalMillis = 3 * 60_000L,
        minIntervalMillis = 30_000L,
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

    private fun emit(location: Location) {
        val fix = FlightDetector.Fix(
            speedMetersPerSecond = location.speed.takeIf { location.hasSpeed() },
            altitudeMeters = location.altitude.takeIf { location.hasAltitude() },
        )
        val flying = detector.onFix(fix)
        publish?.invoke { it.copy(flying = flying) }
    }
}
