package my.github.MrxSiN.modeevolved.action

import java.util.concurrent.Executor

import my.github.MrxSiN.modeevolved.core.Logger
import my.github.MrxSiN.modeevolved.store.ModeSource

/** Whether each Mode is on, as the notification service reports it. */
interface ModeStates {

    /** Null when the Mode is unknown or its state cannot be read. */
    fun isActive(modeId: String): Boolean?

    /** Calls [listener], on any thread, after any Mode may have changed state, until the returned handle is closed. */
    fun observe(listener: () -> Unit): AutoCloseable
}

/** Carries out one action. */
fun interface ActionRunner {
    fun run(action: ModeAction, modeId: String, active: Boolean)
}

/**
 * Runs a Mode's actions when it turns on or off, however it was switched: by
 * a trigger, a schedule, Quick Settings or by hand.
 *
 * States seen when a Mode's actions are first loaded are only remembered, so
 * booting the phone or saving an action never replays an old change.
 *
 * Not thread-safe. Every call runs on [thread].
 */
class ActionAutomation(
    private val actions: ModeSource<ModeActions>,
    private val states: ModeStates,
    private val runner: ActionRunner,
    private val thread: Executor,
    private val logger: Logger,
) {

    private var lists: Map<String, ModeActions> = emptyMap()
    private val known = mutableMapOf<String, Boolean>()
    private var observers: List<AutoCloseable> = emptyList()

    fun start() {
        observers = listOf(
            actions.observe { thread.execute(::reload) },
            states.observe { thread.execute(::check) },
        )
        thread.execute(::reload)
    }

    /** Stops watching. Runs no action for what changes afterwards. */
    fun stop() {
        observers.forEach(AutoCloseable::close)
        observers = emptyList()
    }

    private fun reload() {
        lists = actions.all().associateBy { it.modeId }
        known.keys.retainAll(lists.keys)
        check()
    }

    private fun check() {
        for ((modeId, list) in lists) {
            val active = states.isActive(modeId) ?: continue
            val previous = known.put(modeId, active)
            if (previous == null || previous == active) continue

            val due = list.at(if (active) Timing.START else Timing.END)
            logger.info("Mode $modeId turned ${if (active) "on" else "off"}: running ${due.size} action(s)")
            for (action in due) {
                runCatching { runner.run(action, modeId, active) }
                    .onFailure { logger.warn("An action for mode $modeId failed: $action", it) }
            }
        }
    }
}
