package my.github.MrxSiN.modeevolved.page

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.Context

import my.github.MrxSiN.modeevolved.compat.SettingsApis as S
import my.github.MrxSiN.modeevolved.dex.HostResolver

/**
 * Stores a Mode's rule the way Settings' own backend does.
 *
 * The hidden overload marks the change as made by the person, as every stock
 * Settings edit is, so the system treats it as a user choice. A build without
 * it resolves the contract's public fallback, which takes the same arguments
 * but the last.
 */
class ModeRuleWriter(apis: HostResolver) {

    private val update = apis.method(S.NOTIFICATION_MANAGER_UPDATE_RULE)

    fun get(context: Context, modeId: String): AutomaticZenRule? = notifications(context).getAutomaticZenRule(modeId)

    fun update(context: Context, modeId: String, rule: AutomaticZenRule) {
        val arguments = arrayOf(modeId, rule, FROM_USER).copyOf(update.parameterCount)
        val stored = update.invoke(notifications(context), *arguments) as Boolean
        check(stored) { "Android rejected the update to mode $modeId" }
    }

    private fun notifications(context: Context) = context.getSystemService(NotificationManager::class.java)

    private companion object {
        const val FROM_USER = true
    }
}
