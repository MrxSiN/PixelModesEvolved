package my.github.MrxSiN.modeevolved.signal

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.os.Handler
import android.os.SystemClock

import my.github.MrxSiN.modeevolved.core.Logger

/**
 * One stream of position fixes requested from inside system_server.
 *
 * Owns the parts every location-reading source shares: the attribution
 * system_server must name, the provider choice, the request lifecycle, and how
 * old a fix may be.
 *
 * The provider answers with its last known fix before anything is measured
 * afresh. That one can be hours old — the phone has not moved, or positioning
 * was off — and taking it at face value would turn a Mode on for the minutes
 * until the current fix lands, edge-triggering its actions on the way. Every
 * fix is therefore weighed against [maxAgeMillis] and an older one is dropped.
 */
// Runs inside system_server, which holds every permission these calls check.
@SuppressLint("MissingPermission")
class LocationUpdates(
    context: Context,
    private val handler: Handler,
    private val quality: Int,
    private val intervalMillis: Long,
    private val minIntervalMillis: Long,
    private val maxAgeMillis: Long,
    private val logger: Logger,
) {

    /** A position fix and how old it was when it arrived. */
    data class Reading(val location: Location, val ageMillis: Long)

    // system_server must name who inside it asks for location, or the request is refused.
    private val locations = context.createAttributionContext(ATTRIBUTION_TAG).getSystemService(LocationManager::class.java)
    private var listener: LocationListener? = null

    /** Asks for fresh fixes, then offers the last known one when it is recent enough to trust. */
    fun start(onFix: (Reading) -> Unit) {
        val provider = if (locations.hasProvider(LocationManager.FUSED_PROVIDER)) {
            LocationManager.FUSED_PROVIDER
        } else {
            LocationManager.NETWORK_PROVIDER
        }
        val request = LocationRequest.Builder(intervalMillis)
            .setQuality(quality)
            .setMinUpdateIntervalMillis(minIntervalMillis)
            .build()
        val listener = LocationListener { deliver(it, onFix) }.also { listener = it }
        locations.requestLocationUpdates(provider, request, handler::post, listener)
        locations.getLastKnownLocation(provider)?.let { deliver(it, onFix) }
    }

    fun stop() {
        listener?.let(locations::removeUpdates)
        listener = null
    }

    /** The fused provider can repeat a cached fix, so age is weighed on every fix, not only the first. */
    private fun deliver(location: Location, onFix: (Reading) -> Unit) {
        val age = Fixes.ageMillis(location.elapsedRealtimeNanos, SystemClock.elapsedRealtimeNanos())
        if (age > maxAgeMillis) {
            logger.info("Dropped a position fix ${age / 1000}s old; this rule trusts nothing over ${maxAgeMillis / 1000}s")
            return
        }
        onFix(Reading(location, age))
    }

}

/** Who inside system_server is calling, which location, Bluetooth and other services require of it. */
const val ATTRIBUTION_TAG = "PixelModesEvolved"
