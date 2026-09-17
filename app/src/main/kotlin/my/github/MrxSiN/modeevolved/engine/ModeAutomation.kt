package my.github.MrxSiN.modeevolved.engine

import java.util.concurrent.Executor

import my.github.MrxSiN.modeevolved.store.ModeSource
import my.github.MrxSiN.modeevolved.trigger.ModeRule

/**
 * Keeps the engine and the signal hub in step with the stored rules.
 *
 * Sources a changed rule needs start before the engine sees the rule, so a
 * freshly saved trigger is judged on what the device reports now rather than
 * on a signal that has not started yet. Sources no rule needs stop afterwards.
 *
 * @param thread the single thread every engine and hub call runs on.
 */
class ModeAutomation(
    private val rules: ModeSource<ModeRule>,
    private val engine: TriggerEngine,
    private val hub: SignalHub,
    private val thread: Executor,
) {

    private var observer: AutoCloseable? = null

    fun start() {
        observer = rules.observe { thread.execute(::reload) }
        thread.execute(::reload)
    }

    /**
     * Stops watching rules and signals, leaving every Mode as it is. Call on [thread].
     *
     * The engine is not told that signals stopped, so no Mode is switched off on the way out.
     */
    fun stop() {
        observer?.close()
        observer = null
        hub.stop()
    }

    private fun reload() {
        val current = rules.all()
        val signals = current.flatMapTo(mutableSetOf()) { it.signals }
        hub.start(signals)
        engine.onRulesChanged(current)
        hub.require(signals)
    }
}
