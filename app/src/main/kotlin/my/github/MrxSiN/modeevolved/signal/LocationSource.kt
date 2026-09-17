package my.github.MrxSiN.modeevolved.signal

import android.content.Context
import android.location.Location
import android.location.LocationRequest
import android.os.Handler

import my.github.MrxSiN.modeevolved.engine.SignalSource
import my.github.MrxSiN.modeevolved.trigger.GeoPoint
import my.github.MrxSiN.modeevolved.trigger.Signal
import my.github.MrxSiN.modeevolved.trigger.StateUpdate

/**
 * Coarse, low-rate position fixes.
 *
 * Area triggers are tens of meters wide at the least, so balanced accuracy
 * every few minutes is enough and keeps the radio mostly idle.
 */
class LocationSource(context: Context, handler: Handler) : SignalSource {

    override val signal: Signal = Signal.LOCATION

    private val updates = LocationUpdates(
        context,
        handler,
        LocationRequest.QUALITY_BALANCED_POWER_ACCURACY,
        intervalMillis = 5 * 60_000L,
        minIntervalMillis = 60_000L,
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

    private fun emit(location: Location) {
        val point = GeoPoint(location.latitude, location.longitude)
        publish?.invoke { it.copy(location = point) }
    }
}
