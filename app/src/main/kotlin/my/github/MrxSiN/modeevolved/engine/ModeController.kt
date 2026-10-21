package my.github.MrxSiN.modeevolved.engine

/** Turns Modes on and off. The engine decides when; this decides how. */
fun interface ModeController {

    /**
     * @param reset whether this decision should also end a manual override. True when a person
     * has just saved the trigger, so what they configured takes effect at once.
     */
    fun setActive(modeId: String, active: Boolean, reset: Boolean)

    /**
     * Whether this module switched [modeId] on, and it is on still. Asked only when a rule is
     * first seen with its triggers unmet (at boot, after an update or a reload), so a Mode this
     * module left on is switched off, and one a person or a schedule switched on is left alone.
     */
    fun switchedOnHere(modeId: String): Boolean = false
}
