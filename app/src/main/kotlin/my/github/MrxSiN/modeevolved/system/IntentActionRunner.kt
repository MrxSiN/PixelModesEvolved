package my.github.MrxSiN.modeevolved.system

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.UserHandle

import my.github.MrxSiN.modeevolved.action.ActionRunner
import my.github.MrxSiN.modeevolved.action.BroadcastAction
import my.github.MrxSiN.modeevolved.action.IntentActions
import my.github.MrxSiN.modeevolved.action.LaunchAppAction
import my.github.MrxSiN.modeevolved.action.ModeAction
import my.github.MrxSiN.modeevolved.action.SendableAction
import my.github.MrxSiN.modeevolved.action.TaskerTaskAction
import my.github.MrxSiN.modeevolved.compat.SystemServerApis as SS
import my.github.MrxSiN.modeevolved.core.Logger
import my.github.MrxSiN.modeevolved.core.castOrNull
import my.github.MrxSiN.modeevolved.dex.HostResolver

/**
 * Carries out actions from system_server, for the person using the phone.
 *
 * Each kind of action has one [Performer]; a new kind adds one here.
 * Broadcasts carry which Mode changed and how, for automation apps to read.
 */
// system_server holds every permission and may act for the current user.
@SuppressLint("MissingPermission", "WrongConstant")
class IntentActionRunner(
    private val context: Context,
    private val notifications: NotificationManager,
    apis: HostResolver,
    private val logger: Logger,
) : ActionRunner {

    private val currentUser = apis.field(SS.USER_HANDLE_CURRENT).get(null) as UserHandle
    private val startActivityAsUser = apis.method(SS.START_ACTIVITY_AS_USER)

    private val performers = listOf(LaunchApp(), Broadcast(), TaskerTask())

    override fun run(action: ModeAction, modeId: String, active: Boolean) {
        val change = Change(modeId, notifications.getAutomaticZenRule(modeId)?.name.orEmpty(), active)
        if (performers.none { it.performIfOwned(action, change) }) logger.warn("Nothing performs $action")
    }

    /** The Mode change an action runs for. */
    private data class Change(val modeId: String, val modeName: String, val active: Boolean)

    private abstract inner class Performer<T : ModeAction>(private val type: Class<T>) {
        fun performIfOwned(action: ModeAction, change: Change): Boolean {
            val owned = type.castOrNull(action) ?: return false
            perform(owned, change)
            return true
        }

        abstract fun perform(action: T, change: Change)
    }

    private inner class LaunchApp : Performer<LaunchAppAction>(LaunchAppAction::class.java) {
        override fun perform(action: LaunchAppAction, change: Change) {
            val intent = context.packageManager.getLaunchIntentForPackage(action.packageName)
                ?: return logger.warn("${action.packageName} cannot be opened")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivityAsUser.invoke(context, intent, currentUser)
        }
    }

    /**
     * Sends the configured broadcast, unless the action name belongs to the platform.
     *
     * The send comes from system_server, so receivers see it as the system's own.
     * [IntentActions] decides what may be sent; the check is repeated here because
     * what the editor refuses can still reach storage from an older build or from
     * `adb shell settings put`. [SendableAction] makes it the only way to [send].
     */
    private inner class Broadcast : Performer<BroadcastAction>(BroadcastAction::class.java) {
        override fun perform(action: BroadcastAction, change: Change) {
            val sendable = SendableAction.of(action.intentAction)
                ?: return logger.warn("Refused to broadcast ${action.intentAction}: the system process may not send that name")
            if (action.packageName == null) {
                logger.info("Broadcasting $sendable to every app that listens for it")
            }
            send(sendable) {
                putExtra(EXTRA_MODE_ID, change.modeId)
                putExtra(EXTRA_MODE_NAME, change.modeName)
                putExtra(EXTRA_MODE_ACTIVE, change.active)
                action.packageName?.let(::setPackage)
            }
        }
    }

    private inner class TaskerTask : Performer<TaskerTaskAction>(TaskerTaskAction::class.java) {
        override fun perform(action: TaskerTaskAction, change: Change) {
            val tasker = TASKER_PACKAGES.firstOrNull(::isInstalled) ?: return logger.warn("Tasker is not installed")
            // The extras Tasker's own TaskerIntent class sends.
            send(TASKER_ACTION_TASK) {
                setPackage(tasker)
                putExtra("version_number", "1.1")
                putExtra("task_name", action.taskName)
                putExtra("task_priority", TASKER_PRIORITY)
            }
        }
    }

    /** The one way a broadcast leaves this runner: under a name [IntentActions] cleared. */
    private fun send(action: SendableAction, extras: Intent.() -> Unit) {
        val intent = Intent(action.name).apply(extras)
        // Lets manifest receivers of apps that are not running receive the broadcast.
        intent.addFlags(FLAG_RECEIVER_INCLUDE_BACKGROUND)
        context.sendBroadcastAsUser(intent, currentUser)
    }

    private fun isInstalled(packageName: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess

    companion object {
        const val EXTRA_MODE_ID = "mode_id"
        const val EXTRA_MODE_NAME = "mode_name"
        const val EXTRA_MODE_ACTIVE = "mode_active"

        /** Play Store and direct-download builds of Tasker. */
        val TASKER_PACKAGES = listOf("net.dinglisch.android.taskerm", "net.dinglisch.android.tasker")

        private val TASKER_ACTION_TASK = checkNotNull(SendableAction.of("net.dinglisch.android.tasker.ACTION_TASK"))
        private const val TASKER_PRIORITY = 5

        /** `Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND`, hidden from the SDK. */
        private const val FLAG_RECEIVER_INCLUDE_BACKGROUND = 0x01000000
    }
}
