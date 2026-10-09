package my.github.MrxSiN.modeevolved.presentation

import android.content.res.Resources

import my.github.MrxSiN.modeevolved.R
import my.github.MrxSiN.modeevolved.action.SettingGroup
import my.github.MrxSiN.modeevolved.action.SettingValue
import my.github.MrxSiN.modeevolved.action.SystemSetting
import my.github.MrxSiN.modeevolved.action.SystemSettingAction
import my.github.MrxSiN.modeevolved.action.SystemSettingKind

/** "Auto-rotate screen · On": the setting and what this Mode holds it at. */
object SystemSettingPresentation : ActionPresentation<SystemSettingAction>(SystemSettingKind) {
    override val label = R.string.action_system_settings
    override val hint = R.string.action_system_settings_hint
    override val icon = R.drawable.ic_action_settings
    override fun summary(resources: Resources, item: SystemSettingAction): String =
        resources.getString(R.string.setting_summary, resources.getString(SettingTexts.labelOf(item.setting)), SettingTexts.valueOf(resources, item.setting, item.value))

    /** Under the summary on the Mode page: how long the Mode holds the setting. */
    fun lifetimeOf(item: SystemSettingAction): Int =
        if (item.restore) R.string.setting_while_on_restores else R.string.setting_while_on
}

/** How each system setting and its values are named and drawn. */
object SettingTexts {

    fun labelOf(setting: SystemSetting): Int = when (setting) {
        SystemSetting.AUTO_ROTATE -> R.string.setting_auto_rotate
        SystemSetting.DARK_THEME -> R.string.setting_dark_theme
        SystemSetting.BRIGHTNESS -> R.string.setting_brightness
        SystemSetting.ADAPTIVE_BRIGHTNESS -> R.string.setting_adaptive_brightness
        SystemSetting.EXTRA_DIM -> R.string.setting_extra_dim
        SystemSetting.SCREEN_TIMEOUT -> R.string.setting_screen_timeout
        SystemSetting.ALWAYS_ON_DISPLAY -> R.string.setting_always_on_display
        SystemSetting.SMOOTH_DISPLAY -> R.string.setting_smooth_display
        SystemSetting.RINGER_MODE -> R.string.setting_ringer_mode
        SystemSetting.RING_VOLUME -> R.string.setting_ring_volume
        SystemSetting.NOTIFICATION_VOLUME -> R.string.setting_notification_volume
        SystemSetting.MEDIA_VOLUME -> R.string.setting_media_volume
        SystemSetting.ALARM_VOLUME -> R.string.setting_alarm_volume
        SystemSetting.WIFI -> R.string.setting_wifi
        SystemSetting.BLUETOOTH -> R.string.setting_bluetooth
        SystemSetting.NFC -> R.string.setting_nfc
        SystemSetting.HOTSPOT -> R.string.setting_hotspot
        SystemSetting.MOBILE_DATA -> R.string.setting_mobile_data
    }

    fun groupLabelOf(group: SettingGroup): Int = when (group) {
        SettingGroup.DISPLAY -> R.string.settings_group_display
        SettingGroup.SOUND -> R.string.settings_group_sound
        SettingGroup.CONNECTIVITY -> R.string.settings_group_connectivity
    }

    fun iconOf(setting: SystemSetting): Int = when (setting) {
        SystemSetting.WIFI, SystemSetting.HOTSPOT -> R.drawable.ic_trigger_wifi
        SystemSetting.BLUETOOTH -> R.drawable.ic_trigger_bluetooth
        else -> when (setting.group) {
            SettingGroup.DISPLAY -> R.drawable.ic_setting_display
            SettingGroup.SOUND -> R.drawable.ic_setting_sound
            SettingGroup.CONNECTIVITY -> R.drawable.ic_setting_connectivity
        }
    }

    /** A note shown under the value, for settings that behave in a way worth saying. */
    fun noteOf(setting: SystemSetting): Int? = when (setting) {
        SystemSetting.BRIGHTNESS -> R.string.setting_note_brightness
        SystemSetting.MEDIA_VOLUME -> R.string.setting_note_media_volume
        SystemSetting.RINGER_MODE, SystemSetting.RING_VOLUME, SystemSetting.NOTIFICATION_VOLUME -> R.string.setting_note_ringer
        SystemSetting.WIFI -> R.string.setting_note_wifi
        SystemSetting.HOTSPOT -> R.string.setting_note_hotspot
        SystemSetting.MOBILE_DATA -> R.string.setting_note_mobile_data
        else -> null
    }

    fun valueOf(resources: Resources, setting: SystemSetting, value: Int): String = when (setting.value) {
        SettingValue.SWITCH -> resources.getString(if (value == 1) R.string.setting_on else R.string.setting_off)
        SettingValue.PERCENT -> resources.getString(R.string.setting_percent, value)
        SettingValue.RINGER -> resources.getString(ringerLabelOf(value))
        SettingValue.TIMEOUT -> timeoutLabel(resources, value)
    }

    fun ringerLabelOf(value: Int): Int = when (value) {
        SystemSetting.RINGER_NORMAL -> R.string.setting_ringer_normal
        SystemSetting.RINGER_VIBRATE -> R.string.setting_ringer_vibrate
        else -> R.string.setting_ringer_silent
    }

    fun timeoutLabel(resources: Resources, millis: Int): String {
        val label = Durations.label(millis.toLong())
        val wording = if (label.seconds) R.plurals.setting_timeout_seconds else R.plurals.setting_timeout_minutes
        return resources.getQuantityString(wording, label.amount, label.amount)
    }
}
