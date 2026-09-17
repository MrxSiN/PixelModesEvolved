package my.github.MrxSiN.modeevolved.presentation

import android.content.res.Resources

import my.github.MrxSiN.modeevolved.R
import my.github.MrxSiN.modeevolved.action.BroadcastAction
import my.github.MrxSiN.modeevolved.action.BroadcastKind
import my.github.MrxSiN.modeevolved.action.LaunchAppAction
import my.github.MrxSiN.modeevolved.action.LaunchAppKind
import my.github.MrxSiN.modeevolved.action.ModeAction
import my.github.MrxSiN.modeevolved.action.TaskerTaskAction
import my.github.MrxSiN.modeevolved.action.TaskerTaskKind
import my.github.MrxSiN.modeevolved.action.Timing

/** How one kind of action is named, drawn and described to a person. */
typealias ActionPresentation<T> = Presentation<ModeAction, T>

object LaunchAppPresentation : ActionPresentation<LaunchAppAction>(LaunchAppKind) {
    override val label = R.string.action_launch_app
    override val hint = R.string.action_launch_app_hint
    override val icon = R.drawable.ic_action_app
    override fun summary(resources: Resources, item: LaunchAppAction): String =
        resources.getString(R.string.action_launch_app_summary, item.label.ifEmpty { item.packageName })
}

object BroadcastPresentation : ActionPresentation<BroadcastAction>(BroadcastKind) {
    override val label = R.string.action_broadcast
    override val hint = R.string.action_broadcast_hint
    override val icon = R.drawable.ic_action_broadcast
    override fun summary(resources: Resources, item: BroadcastAction): String =
        resources.getString(R.string.action_broadcast_summary, item.intentAction)
}

object TaskerTaskPresentation : ActionPresentation<TaskerTaskAction>(TaskerTaskKind) {
    override val label = R.string.action_tasker
    override val hint = R.string.action_tasker_hint
    override val icon = R.drawable.ic_action_tasker
    override fun summary(resources: Resources, item: TaskerTaskAction): String =
        resources.getString(R.string.action_tasker_summary, item.taskName)
}

/** Every action presentation, in the order a person chooses between them. */
val ActionPresentations = PresentationSet(listOf(LaunchAppPresentation, BroadcastPresentation, TaskerTaskPresentation))

/** Words for when an action runs. */
object Timings {
    fun labelOf(timing: Timing): Int = when (timing) {
        Timing.START -> R.string.timing_start
        Timing.END -> R.string.timing_end
    }

    fun summaryOf(timing: Timing): Int = when (timing) {
        Timing.START -> R.string.timing_start_summary
        Timing.END -> R.string.timing_end_summary
    }
}
