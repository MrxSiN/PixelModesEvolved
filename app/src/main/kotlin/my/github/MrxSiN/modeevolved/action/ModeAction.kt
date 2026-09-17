package my.github.MrxSiN.modeevolved.action

import my.github.MrxSiN.modeevolved.store.ModeItems

/** When an action runs: as its Mode turns on, or as it turns off. */
enum class Timing { START, END }

/**
 * Something done when a Mode turns on or off.
 *
 * A new kind of action is a new implementation of this interface, added beside
 * the others with its own stored kind, presentation, editor and runner.
 */
interface ModeAction {
    val timing: Timing
}

/** Opens the app [packageName]. [label] is what the app was called when chosen, kept for display only. */
data class LaunchAppAction(override val timing: Timing, val packageName: String, val label: String) : ModeAction

/**
 * Sends a broadcast with [intentAction], optionally to [packageName] only.
 *
 * Automation apps listen for these: Tasker's "Intent Received" event, for one.
 */
data class BroadcastAction(override val timing: Timing, val intentAction: String, val packageName: String?) : ModeAction

/** Runs the Tasker task named [taskName]. Tasker must allow external access. */
data class TaskerTaskAction(override val timing: Timing, val taskName: String) : ModeAction

/** The actions attached to one Mode, in the order a person added them. */
data class ModeActions(
    override val modeId: String,
    override val items: List<ModeAction> = emptyList(),
) : ModeItems<ModeActions, ModeAction> {

    fun at(timing: Timing): List<ModeAction> = items.filter { it.timing == timing }

    override fun plus(item: ModeAction): ModeActions = if (item in items) this else copy(items = items + item)

    override fun replace(old: ModeAction, new: ModeAction): ModeActions {
        val index = items.indexOf(old)
        return when {
            index < 0 -> plus(new)
            new != old && new in items -> minus(old)
            else -> copy(items = items.toMutableList().apply { set(index, new) })
        }
    }

    override fun minus(item: ModeAction): ModeActions = copy(items = items - item)
}
