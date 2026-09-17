package my.github.MrxSiN.modeevolved.page

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.Context

/**
 * Stores a Mode's rule the way Settings' own backend does.
 *
 * The hidden overload marks the change as made by the person, as every stock
 * Settings edit is, so the system treats it as a user choice. Builds without
 * it fall back to the public call.
 */
class ModeRuleWriter(context: Context) {

    private val notifications = context.getSystemService(NotificationManager::class.java)

    private val fromUserUpdate = runCatching {
        NotificationManager::class.java.getMethod(
            "updateAutomaticZenRule",
            String::class.java,
            AutomaticZenRule::class.java,
            Boolean::class.javaPrimitiveType,
        )
    }.getOrNull()

    fun get(modeId: String): AutomaticZenRule? = notifications.getAutomaticZenRule(modeId)

    fun update(modeId: String, rule: AutomaticZenRule) {
        val stored = fromUserUpdate?.invoke(notifications, modeId, rule, true) as Boolean?
            ?: notifications.updateAutomaticZenRule(modeId, rule)
        check(stored) { "Android rejected the update to mode $modeId" }
    }
}
