package my.github.MrxSiN.modeevolved.page

import android.annotation.SuppressLint
import android.app.AutomaticZenRule
import android.content.Context
import android.net.Uri

import my.github.MrxSiN.modeevolved.dex.ClassFinder
import my.github.MrxSiN.modeevolved.dex.SettingsClasses

/**
 * Reflection boundary around Settings' `ZenMode` and the framework's `ZenModeConfig`.
 *
 * Changing a Mode's schedule goes through the same `ZenMode` method the stock
 * schedule chooser calls, so the rule's type, owner and description stay the
 * way Settings itself would leave them.
 */
// Settings runs as the system uid, so hidden framework members are reachable.
@SuppressLint("PrivateApi")
class ZenModeApi(classes: ClassFinder) {

    private val zenMode = classes.find(SettingsClasses.zenMode)
    private val getRule = classes.member("ZenMode.getRule()") { zenMode.getDeclaredMethod("getRule").apply { isAccessible = true } }
    private val getId = classes.member("ZenMode.getId()") { zenMode.getMethod("getId") }
    private val getName = classes.member("ZenMode.getName()") { zenMode.getMethod("getName") }
    private val setCondition = classes.member("ZenMode.setCustomModeConditionId(Context, Uri)") {
        zenMode.getMethod("setCustomModeConditionId", Context::class.java, Uri::class.java)
    }
    // Hidden on AutomaticZenRule: the app that owns the rule.
    private val getPackageName = classes.member("AutomaticZenRule.getPackageName()") {
        AutomaticZenRule::class.java.getMethod("getPackageName")
    }
    private val manualCondition = classes.member("ZenModeConfig.toCustomManualConditionId()") {
        Class.forName("android.service.notification.ZenModeConfig").getMethod("toCustomManualConditionId")
    }

    fun ref(mode: Any) = ModeRef(getId.invoke(mode) as String, getName.invoke(mode) as String)

    fun rule(mode: Any): AutomaticZenRule = getRule.invoke(mode) as AutomaticZenRule

    fun conditionId(mode: Any): Uri? = rule(mode).conditionId

    /** Whether the system owns [mode], so Settings may change it, and it follows a time or calendar schedule. */
    fun hasSchedule(mode: Any): Boolean {
        val rule = rule(mode)
        return getPackageName.invoke(rule) == SYSTEM_PACKAGE && rule.type in SCHEDULE_TYPES
    }

    /** Turns [mode] back into one without a schedule, in memory. Store it with [ModeRuleWriter]. */
    fun clearSchedule(context: Context, mode: Any) {
        setCondition.invoke(mode, context, manualCondition.invoke(null) as Uri)
    }

    private companion object {
        const val SYSTEM_PACKAGE = "android"
        val SCHEDULE_TYPES = setOf(AutomaticZenRule.TYPE_SCHEDULE_TIME, AutomaticZenRule.TYPE_SCHEDULE_CALENDAR)
    }
}
