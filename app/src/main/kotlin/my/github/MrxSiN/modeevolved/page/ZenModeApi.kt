package my.github.MrxSiN.modeevolved.page

import android.app.AutomaticZenRule
import android.content.Context
import android.net.Uri

import my.github.MrxSiN.modeevolved.compat.SettingsApis as S
import my.github.MrxSiN.modeevolved.dex.HostResolver

/**
 * Reflection boundary around Settings' `ZenMode` and the framework's `ZenModeConfig`.
 *
 * Changing a Mode's schedule goes through the same `ZenMode` method the stock
 * schedule chooser calls, so the rule's type, owner and description stay the
 * way Settings itself would leave them.
 *
 * Who a Mode is ([ref]) is looked up at once; its rule and schedule only when first
 * asked for, since the Mode page draws its rows without them and only loses the
 * schedule warning when they are gone.
 */
class ZenModeApi(private val apis: HostResolver) {

    private val getId = apis.method(S.ZEN_MODE_GET_ID)
    private val getName = apis.method(S.ZEN_MODE_GET_NAME)
    private val getRule by lazy { apis.method(S.ZEN_MODE_GET_RULE) }
    private val setCondition by lazy { apis.method(S.ZEN_MODE_SET_CONDITION) }
    private val getPackageName by lazy { apis.method(S.AUTOMATIC_ZEN_RULE_GET_PACKAGE) }
    private val manualCondition by lazy { apis.method(S.ZEN_MODE_CONFIG_MANUAL_CONDITION) }

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
