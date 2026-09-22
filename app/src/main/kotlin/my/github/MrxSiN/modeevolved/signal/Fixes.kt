package my.github.MrxSiN.modeevolved.signal

/** Arithmetic on position fixes, kept apart from Android so it can be tested. */
object Fixes {

    private const val NANOS_PER_MILLI = 1_000_000L

    /**
     * How old a fix taken at [fixElapsedRealtimeNanos] is at [nowNanos], in milliseconds.
     *
     * Both are readings of the same monotonic clock, so a fix cannot be newer
     * than now; a negative difference is rounding and counts as no age at all.
     */
    fun ageMillis(fixElapsedRealtimeNanos: Long, nowNanos: Long): Long =
        ((nowNanos - fixElapsedRealtimeNanos) / NANOS_PER_MILLI).coerceAtLeast(0L)
}
