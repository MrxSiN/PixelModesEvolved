package my.github.MrxSiN.modeevolved.engine

/** Turns Modes on and off. The engine decides when; this decides how. */
fun interface ModeController {

    /**
     * @param reset whether this decision should also end a manual override. True when a person
     * has just saved the trigger, so what they configured takes effect at once.
     */
    fun setActive(modeId: String, active: Boolean, reset: Boolean)
}
