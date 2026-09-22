package my.github.MrxSiN.modeevolved.engine

/**
 * Runs work later, on the one thread the engine and its sources share.
 *
 * Every timed decision in this module waits through this rather than through a
 * Handler of its own, so each of them can be driven by a test clock instead of
 * by ten real minutes.
 */
fun interface Delays {

    /** Runs [task] after [millis]. Closing the returned handle cancels it if it has not run. */
    fun after(millis: Long, task: () -> Unit): AutoCloseable
}
