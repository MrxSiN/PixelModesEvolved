package my.github.MrxSiN.modeevolved.trigger

/**
 * One position fix, as a trigger reads it.
 *
 * A fix is not a bare point. It carries how far out it may be and how old it
 * was when it arrived, and both decide whether a boundary crossing is real, so
 * both travel with the point instead of being dropped at the source.
 *
 * @property accuracyMeters radius the true position lies within, or null when the fix carries none.
 * @property ageMillis how long before it was published the fix was taken.
 */
data class LocationSample(
    val point: GeoPoint,
    val accuracyMeters: Double? = null,
    val ageMillis: Long = 0L,
) {

    /** Whether the fix was taken no longer than [maxAgeMillis] before it arrived. */
    fun isFreshWithin(maxAgeMillis: Long): Boolean = ageMillis <= maxAgeMillis
}
