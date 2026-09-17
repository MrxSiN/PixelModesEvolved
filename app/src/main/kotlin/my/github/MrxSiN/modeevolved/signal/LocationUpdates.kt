package my.github.MrxSiN.modeevolved.signal

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.os.Handler

/**
 * One stream of position fixes requested from inside system_server.
 *
 * Owns the parts every location-reading source shares: the attribution
 * system_server must name, the provider choice and the request lifecycle.
 */
// Runs inside system_server, which holds every permission these calls check.
@SuppressLint("MissingPermission")
class LocationUpdates(
    context: Context,
    private val handler: Handler,
    private val quality: Int,
    private val intervalMillis: Long,
    private val minIntervalMillis: Long,
) {

    // system_server must name who inside it asks for location, or the request is refused.
    private val locations = context.createAttributionContext(ATTRIBUTION_TAG).getSystemService(LocationManager::class.java)
    private var listener: LocationListener? = null

    /** Delivers the last known fix at once, when there is one, then every new fix on [handler]. */
    fun start(onFix: (Location) -> Unit) {
        val provider = if (locations.hasProvider(LocationManager.FUSED_PROVIDER)) {
            LocationManager.FUSED_PROVIDER
        } else {
            LocationManager.NETWORK_PROVIDER
        }
        val request = LocationRequest.Builder(intervalMillis)
            .setQuality(quality)
            .setMinUpdateIntervalMillis(minIntervalMillis)
            .build()
        val listener = LocationListener(onFix).also { listener = it }
        locations.getLastKnownLocation(provider)?.let(onFix)
        locations.requestLocationUpdates(provider, request, handler::post, listener)
    }

    fun stop() {
        listener?.let(locations::removeUpdates)
        listener = null
    }

    private companion object {
        const val ATTRIBUTION_TAG = "ModeEvolved"
    }
}
