package my.github.MrxSiN.modeevolved.editor

import android.content.Context
import android.location.Address
import android.location.Geocoder
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

import my.github.MrxSiN.modeevolved.trigger.GeoPoint

/** A named place a search found. */
data class Place(val name: String, val address: String, val point: GeoPoint)

/** Finds places by name, so an area can be set somewhere the map is not showing yet. */
fun interface PlaceSearch {

    /** Places matching [query], best first. Empty when nothing matches or search is unavailable. */
    suspend fun find(query: String): List<Place>
}

/**
 * [PlaceSearch] through the platform [Geocoder], which needs no key and no
 * location permission: it only turns names into coordinates.
 */
class GeocoderPlaceSearch(context: Context) : PlaceSearch {

    private val geocoder = Geocoder(context)

    override suspend fun find(query: String): List<Place> {
        if (query.isBlank() || !Geocoder.isPresent()) return emptyList()
        return suspendCancellableCoroutine { continuation ->
            geocoder.getFromLocationName(
                query,
                MAX_RESULTS,
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        continuation.resume(addresses.map(::placeOf))
                    }

                    override fun onError(errorMessage: String?) {
                        continuation.resume(emptyList())
                    }
                },
            )
        }
    }

    private fun placeOf(address: Address): Place {
        val line = (0..address.maxAddressLineIndex).joinToString(", ") { address.getAddressLine(it) }
        // A feature name that is only a house number says less than the first part of the address.
        val name = address.featureName?.takeUnless { it.isBlank() || it.all(Char::isDigit) } ?: line.substringBefore(",")
        return Place(name, line, GeoPoint(address.latitude, address.longitude))
    }

    private companion object {
        const val MAX_RESULTS = 6
    }
}
