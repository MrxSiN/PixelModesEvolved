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
 * An action with a [ModeAction.cooldownMillis] is skipped while it is still
 * inside that window. A rule's own stabilization already drops edges a noisy
 * signal caused, but a Mode also changes for reasons this module never sees —
 * a schedule boundary, Quick Settings, another app — and a cooldown is what
 * keeps those from opening the same app three times in a row.
 *
 * Not thread-safe. Every call runs on [thread].
 *
 * @param now a monotonic clock in milliseconds, so a cooldown survives the wall clock moving.
 */
class ActionAutomation(
    private val actions: ModeSource<ModeActions>,
    private val states: ModeStates,
    private val runner: ActionRunner,
    private val thread: Executor,
    private val now: () -> Long,
    private val logger: Logger,
) {

    private var lists: Map<String, ModeActions> = emptyMap()
    private val known = mutableMapOf<String, Boolean>()
    private val lastRun = mutableMapOf<Run, Long>()
    private var observers: List<AutoCloseable> = emptyList()

    /** One action of one Mode, which is what a cooldown counts from. */
    private data class Run(val modeId: String, val action: ModeAction)

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
        // An action that was edited or removed is a different action, and starts without a cooldown.
        lastRun.keys.retainAll { lists[it.modeId]?.items?.contains(it.action) == true }
        check()
    }

    private fun check() {
        for ((modeId, list) in lists) {
            val active = states.isActive(modeId) ?: continue
            val previous = known.put(modeId, active)
            if (previous == null || previous == active) continue

            val due = list.at(if (active) Timing.START else Timing.END)
            logger.info("Mode $modeId turned ${if (active) "on" else "off"}: running ${due.size} action(s)")
            due.forEach { run(it, modeId, active) }
        }
    }

    private fun run(action: ModeAction, modeId: String, active: Boolean) {
        val key = Run(modeId, action)
        val since = lastRun[key]?.let { now() - it }
        if (action.cooldownMillis > 0L && since != null && since < action.cooldownMillis) {
            logger.info("Skipped an action for mode $modeId: it ran ${since / 1000}s ago, inside its ${action.cooldownMillis / 1000}s cooldown")
            return
        }
        lastRun[key] = now()
        runCatching { runner.run(action, modeId, active) }
            .onFailure { logger.warn("An action for mode $modeId failed: $action", it) }
    }
}
