package my.github.MrxSiN.modeevolved.engine

import my.github.MrxSiN.modeevolved.trigger.Signal
import my.github.MrxSiN.modeevolved.trigger.StateUpdate

/**
 * Watches one [Signal] and reports it as updates to its own slice of the device state.
 *
 * Every update must be delivered on the engine thread.
 */
interface SignalSource {

    val signal: Signal

    fun start(publish: (StateUpdate) -> Unit)

    /** Stops watching and publishes the slice as unknown again. */
    fun stop()
}
