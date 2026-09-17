package my.github.MrxSiN.modeevolved.system

import android.app.NotificationManager
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.provider.Settings
import android.service.notification.Condition

import my.github.MrxSiN.modeevolved.action.ModeStates

/**
 * Mode states from the notification service, noticed through its config etag.
 *
 * ZenModeHelper writes `Settings.Global.zen_mode_config_etag` after every
 * configuration change, and a Mode turning on or off is one. Watching that one
 * setting says when to look, without hooking the service.
 */
class ZenModeStates(
    context: Context,
    private val handler: Handler,
    private val notifications: NotificationManager,
) : ModeStates {

    private val resolver = context.contentResolver

    override fun isActive(modeId: String): Boolean? = when (notifications.getAutomaticZenRuleState(modeId)) {
        Condition.STATE_TRUE -> true
        Condition.STATE_FALSE -> false
        else -> null
    }

    override fun observe(listener: () -> Unit): AutoCloseable {
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) = listener()
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(CONFIG_ETAG), false, observer)
        return AutoCloseable { resolver.unregisterContentObserver(observer) }
    }

    private companion object {
        /** `Settings.Global.ZEN_MODE_CONFIG_ETAG`, hidden from the SDK. */
        const val CONFIG_ETAG = "zen_mode_config_etag"
    }
}
