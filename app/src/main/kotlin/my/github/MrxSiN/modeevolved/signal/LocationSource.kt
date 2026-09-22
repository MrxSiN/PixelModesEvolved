package my.github.MrxSiN.modeevolved.signal

import android.content.Context
import android.location.LocationRequest
import android.os.Handler

import my.github.MrxSiN.modeevolved.core.Logger
import my.github.MrxSiN.modeevolved.engine.SignalSource
import my.github.MrxSiN.modeevolved.trigger.GeoPoint
import my.github.MrxSiN.modeevolved.trigger.LocationSample
import my.github.MrxSiN.modeevolved.trigger.Signal
import my.github.MrxSiN.modeevolved.trigger.StateUpdate

/**
 * Coarse, low-rate position fixes.
 *
 * Area triggers are tens of meters wide at the least, so balanced accuracy
 * every few minutes is enough and keeps the radio mostly idle. Each fix keeps
 * its accuracy and its age, which is what lets an area trigger tell a real
 * arrival from a fix that merely landed nearby.
 */
class LocationSource(context: Context, handler: Handler, logger: Logger) : SignalSource {

    override val signal: Signal = Signal.LOCATION

    private val updates = LocationUpdates(
        context,
        handler,
        LocationRequest.QUALITY_BALANCED_POWER_ACCURACY,
        intervalMillis = REQUEST_INTERVAL_MILLIS,
        minIntervalMillis = 60_000L,
        maxAgeMillis = MAX_AGE_MILLIS,
        logger = logger,
    )
    private var publish: ((StateUpdate) -> Unit)? = null

    override fun start(publish: (StateUpdate) -> Unit) {
        this.publish = publish
        updates.start(::emit)
    }

    override fun stop() {
        updates.stop()
        publish?.invoke { it.copy(location = null) }
        publish = null
    }

    private fun emit(reading: LocationUpdates.Reading) {
        val location = reading.location
        val sample = LocationSample(
            point = GeoPoint(location.latitude, location.longitude),
            accuracyMeters = location.accuracy.toDouble().takeIf { location.hasAccuracy() },
            ageMillis = reading.ageMillis,
        )
        publish?.invoke { it.copy(location = sample) }
    }

    private companion object {
        const val REQUEST_INTERVAL_MILLIS = 5 * 60_000L

        /** Two request intervals: old enough that an ordinary fix is never dropped, recent enough to still be where you are. */
        const val MAX_AGE_MILLIS = 2 * REQUEST_INTERVAL_MILLIS
    }
}
