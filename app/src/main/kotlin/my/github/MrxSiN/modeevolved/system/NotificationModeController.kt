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
 *
 * The Modes this module switched on are remembered ([save], `Settings.Secure`), because
 * Android keeps a rule's condition across a reboot and this module's own engine starts
 * afresh: without the record, a Mode switched on by a trigger that no longer holds would
 * stay on until a person switched it off.
 */
class NotificationModeController(
    private val notifications: NotificationManager,
    private val logger: Logger,
    saved: String?,
    private val save: (String) -> Unit,
) : ModeController {

    private val owned = LinkedHashSet<String>().apply { saved?.split(SEPARATOR)?.filterTo(this) { it.isNotBlank() } }

    override fun setActive(modeId: String, active: Boolean, reset: Boolean) {
        val rule = notifications.getAutomaticZenRule(modeId)
            ?: return logger.warn("Mode $modeId no longer exists").also { forget(modeId) }
        if (!rule.isEnabled) return logger.info("Mode $modeId is disabled; left alone")

        val id = rule.conditionId ?: Uri.EMPTY
        if (reset && active && notifications.getAutomaticZenRuleState(modeId) != Condition.STATE_TRUE) {
            notifications.setAutomaticZenRuleState(modeId, condition(id, Condition.STATE_FALSE))
        }
        notifications.setAutomaticZenRuleState(modeId, condition(id, if (active) Condition.STATE_TRUE else Condition.STATE_FALSE))
        if (active) remember(modeId) else forget(modeId)
    }

    // ponytail: a Mode this module switched on that a schedule then switched off and on again
    // still reads as this module's; the record is cleared on this module's own "off" only.
    override fun switchedOnHere(modeId: String): Boolean {
        if (modeId !in owned) return false
        if (notifications.getAutomaticZenRule(modeId) == null) return false.also { forget(modeId) }
        return notifications.getAutomaticZenRuleState(modeId) == Condition.STATE_TRUE
    }

    private fun remember(modeId: String) {
        if (owned.add(modeId)) save(owned.joinToString(SEPARATOR))
    }

    private fun forget(modeId: String) {
        if (owned.remove(modeId)) save(owned.joinToString(SEPARATOR))
    }

    private fun condition(id: Uri, state: Int) = Condition(id, SUMMARY, state, Condition.SOURCE_CONTEXT)

    private companion object {
        const val SUMMARY = "Pixel Modes Evolved trigger"
        const val SEPARATOR = ","
    }
}
