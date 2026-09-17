package my.github.MrxSiN.modeevolved.engine

import my.github.MrxSiN.modeevolved.core.Logger
import my.github.MrxSiN.modeevolved.trigger.DeviceState
import my.github.MrxSiN.modeevolved.trigger.Signal
import my.github.MrxSiN.modeevolved.trigger.StateUpdate

/**
 * Runs the signal sources the rules need and merges what they report.
 *
 * A source nobody needs is not running, so a person with only Wi-Fi rules
 * pays nothing for location or motion sensing.
 *
 * Not thread-safe. The caller delivers every call on one thread.
 */
class SignalHub(
    sources: List<SignalSource>,
    private val onState: (DeviceState) -> Unit,
    private val logger: Logger,
) {

    private val sources = sources.associateBy { it.signal }
    private val running = mutableSetOf<Signal>()
    private var state = DeviceState()
    private var stopped = false

    /** Starts every source in [signals] that is not running, leaving the others alone. Does nothing once stopped. */
    fun start(signals: Set<Signal>) {
        if (stopped) return
        // A source that failed to start stays out of the running set, so the next rule change retries it.
        for (signal in signals - running) {
            if (guarded("start", signal) { it.start(::publish) }) running += signal
        }
    }

    /** Runs exactly the sources in [signals]. */
    fun require(signals: Set<Signal>) {
        for (signal in running - signals) {
            running -= signal
            guarded("stop", signal) { it.stop() }
        }
        start(signals)
    }

    /** Stops every source for good, without reporting the unknown state they leave behind. */
    fun stop() {
        stopped = true
        require(emptySet())
    }

    private fun publish(update: StateUpdate) {
        if (stopped) return
        state = update(state)
        onState(state)
    }

    /** A source that fails must cost its own triggers, never the process hosting the hub. */
    private fun guarded(action: String, signal: Signal, block: (SignalSource) -> Unit): Boolean {
        val source = sources[signal] ?: return false.also { logger.warn("No source for $signal") }
        return runCatching { block(source) }
            .onSuccess { logger.info("Signal $signal: $action") }
            .onFailure { logger.warn("Signal $signal: $action failed", it) }
            .isSuccess
    }
}
