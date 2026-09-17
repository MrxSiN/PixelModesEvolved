package my.github.MrxSiN.modeevolved.system

import android.app.NotificationManager
import android.net.Uri
import android.service.notification.Condition

import my.github.MrxSiN.modeevolved.core.Logger
import my.github.MrxSiN.modeevolved.engine.ModeController

/**
 * Switches Modes through the public NotificationManager API.
 *
 * Called from inside system_server, so the calling uid is the system uid:
 * ZenModeHelper lets it manage every rule, whoever owns it, and records the
 * change with ORIGIN_SYSTEM. That origin updates the rule's condition and
 * leaves a manual override in place until the two agree again.
 *
 * A reset turns a Mode on through a manual "off": the condition is first set
 * false, which agrees with the override and so ends it, then true.
 */
class NotificationModeController(
    private val notifications: NotificationManager,
    private val logger: Logger,
) : ModeController {

    override fun setActive(modeId: String, active: Boolean, reset: Boolean) {
        val rule = notifications.getAutomaticZenRule(modeId)
            ?: return logger.warn("Mode $modeId no longer exists")
        if (!rule.isEnabled) return logger.info("Mode $modeId is disabled; left alone")

        val id = rule.conditionId ?: Uri.EMPTY
        if (reset && active && notifications.getAutomaticZenRuleState(modeId) != Condition.STATE_TRUE) {
            notifications.setAutomaticZenRuleState(modeId, condition(id, Condition.STATE_FALSE))
        }
        notifications.setAutomaticZenRuleState(modeId, condition(id, if (active) Condition.STATE_TRUE else Condition.STATE_FALSE))
    }

    private fun condition(id: Uri, state: Int) = Condition(id, SUMMARY, state, Condition.SOURCE_CONTEXT)

    private companion object {
        const val SUMMARY = "ModeEvolved trigger"
    }
}
