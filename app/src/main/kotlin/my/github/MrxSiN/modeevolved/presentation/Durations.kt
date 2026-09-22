package my.github.MrxSiN.modeevolved.presentation

/**
 * How a length of time is said.
 *
 * @property seconds whether [amount] counts seconds rather than minutes, which is
 * what picks between two wordings of the same plural.
 * @property amount the number, which also decides singular or plural.
 */
data class DurationLabel(val seconds: Boolean, val amount: Int)

/** Lengths of time a person picks from, said the same way on the Mode page and in the editor. */
object Durations {

    fun label(millis: Long): DurationLabel = if (millis < MINUTE) {
        DurationLabel(seconds = true, amount = (millis / SECOND).toInt())
    } else {
        DurationLabel(seconds = false, amount = (millis / MINUTE).toInt())
    }

    private const val SECOND = 1_000L
    private const val MINUTE = 60_000L
}

/**
 * The waits a rule can be given before its Mode is switched.
 *
 * A short list rather than a free number. The wait exists to outlast a Wi-Fi
 * roam, a Bluetooth reconnect or a position fix wandering along a boundary,
 * and every length that helps with those is under a few minutes.
 */
object Stabilizations {

    /** Offered in this order, starting with no wait at all. */
    val offered: List<Long> = listOf(0L, 30_000L, 60_000L, 120_000L, 300_000L)
}

/** The least time between two runs of one action. */
object Cooldowns {

    /** Offered in this order, starting with no cooldown at all. */
    val offered: List<Long> = listOf(0L, 60_000L, 300_000L, 900_000L, 1_800_000L)
}
