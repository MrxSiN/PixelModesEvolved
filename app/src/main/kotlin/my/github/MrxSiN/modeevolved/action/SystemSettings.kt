package my.github.MrxSiN.modeevolved.action

import org.json.JSONObject

import my.github.MrxSiN.modeevolved.core.Feature

/** What a system setting's value is, which decides how it is chosen and checked. */
enum class SettingValue {

    /** 0 off, 1 on. */
    SWITCH,

    /** 0 to 100, as the setting's own slider shows it. */
    PERCENT,

    /** Milliseconds, one of [SystemSetting.TIMEOUTS]. */
    TIMEOUT,

    /** [SystemSetting.RINGER_SILENT], [SystemSetting.RINGER_VIBRATE] or [SystemSetting.RINGER_NORMAL]. */
    RINGER,
    ;

    fun isValid(value: Int): Boolean = when (this) {
        SWITCH -> value == 0 || value == 1
        PERCENT -> value in 0..100
        TIMEOUT -> value in SystemSetting.TIMEOUTS
        RINGER -> value in SystemSetting.RINGER_SILENT..SystemSetting.RINGER_NORMAL
    }
}

/** Where a setting is listed, as Android's own Settings groups them. */
enum class SettingGroup { DISPLAY, SOUND, CONNECTIVITY }

/**
 * One device setting a Mode can hold while it is on.
 *
 * [id] is stored and must outlive a rename. [feature] is the row on the compatibility page
 * that says whether this build lets system_server change it.
 */
enum class SystemSetting(val id: String, val group: SettingGroup, val value: SettingValue, val feature: Feature) {

    AUTO_ROTATE("autoRotate", SettingGroup.DISPLAY, SettingValue.SWITCH, Feature.SETTING_AUTO_ROTATE),
    DARK_THEME("darkTheme", SettingGroup.DISPLAY, SettingValue.SWITCH, Feature.SETTING_DARK_THEME),
    BRIGHTNESS("brightness", SettingGroup.DISPLAY, SettingValue.PERCENT, Feature.SETTING_BRIGHTNESS),
    ADAPTIVE_BRIGHTNESS("adaptiveBrightness", SettingGroup.DISPLAY, SettingValue.SWITCH, Feature.SETTING_ADAPTIVE_BRIGHTNESS),
    EXTRA_DIM("extraDim", SettingGroup.DISPLAY, SettingValue.SWITCH, Feature.SETTING_EXTRA_DIM),
    SCREEN_TIMEOUT("screenTimeout", SettingGroup.DISPLAY, SettingValue.TIMEOUT, Feature.SETTING_SCREEN_TIMEOUT),
    ALWAYS_ON_DISPLAY("alwaysOnDisplay", SettingGroup.DISPLAY, SettingValue.SWITCH, Feature.SETTING_ALWAYS_ON_DISPLAY),
    SMOOTH_DISPLAY("smoothDisplay", SettingGroup.DISPLAY, SettingValue.SWITCH, Feature.SETTING_SMOOTH_DISPLAY),

    RINGER_MODE("ringerMode", SettingGroup.SOUND, SettingValue.RINGER, Feature.SETTING_RINGER_MODE),
    RING_VOLUME("ringVolume", SettingGroup.SOUND, SettingValue.PERCENT, Feature.SETTING_RING_VOLUME),
    NOTIFICATION_VOLUME("notificationVolume", SettingGroup.SOUND, SettingValue.PERCENT, Feature.SETTING_NOTIFICATION_VOLUME),
    MEDIA_VOLUME("mediaVolume", SettingGroup.SOUND, SettingValue.PERCENT, Feature.SETTING_MEDIA_VOLUME),
    ALARM_VOLUME("alarmVolume", SettingGroup.SOUND, SettingValue.PERCENT, Feature.SETTING_ALARM_VOLUME),

    WIFI("wifi", SettingGroup.CONNECTIVITY, SettingValue.SWITCH, Feature.SETTING_WIFI),
    BLUETOOTH("bluetooth", SettingGroup.CONNECTIVITY, SettingValue.SWITCH, Feature.SETTING_BLUETOOTH),
    NFC("nfc", SettingGroup.CONNECTIVITY, SettingValue.SWITCH, Feature.SETTING_NFC),
    HOTSPOT("hotspot", SettingGroup.CONNECTIVITY, SettingValue.SWITCH, Feature.SETTING_HOTSPOT),
    MOBILE_DATA("mobileData", SettingGroup.CONNECTIVITY, SettingValue.SWITCH, Feature.SETTING_MOBILE_DATA),
    ;

    /** The value a newly chosen setting starts at in the editor. */
    val defaultValue: Int
        get() = when (value) {
            SettingValue.SWITCH -> 1
            SettingValue.PERCENT -> 50
            SettingValue.TIMEOUT -> TIMEOUTS[2]
            SettingValue.RINGER -> RINGER_VIBRATE
        }

    companion object {
        /** What Settings offers under Screen timeout, shortest first. */
        val TIMEOUTS = intArrayOf(15_000, 30_000, 60_000, 120_000, 300_000, 600_000, 1_800_000)

        /** `AudioManager.RINGER_MODE_*`. */
        const val RINGER_SILENT = 0
        const val RINGER_VIBRATE = 1
        const val RINGER_NORMAL = 2

        private val byId = entries.associateBy(SystemSetting::id)

        fun of(id: String): SystemSetting? = byId[id]
    }
}

/**
 * Holds [setting] at [value] while its Mode is on, and gives it back afterwards.
 *
 * Not a one-shot action: turning the Mode on claims the setting, turning it off releases the
 * claim, and `SettingClaims` decides what the setting is between the two. [restore] puts back
 * what the setting was before the first claim, unless someone changed it in the meantime.
 */
data class SystemSettingAction(
    val setting: SystemSetting,
    val value: Int,
    val restore: Boolean = true,
) : ModeAction {

    init {
        require(setting.value.isValid(value)) { "${setting.id} cannot be $value" }
    }

    /** A claim lasts as long as its Mode is on, so it has no moment of its own. */
    override val timing: Timing get() = Timing.START

    override val cooldownMillis: Long get() = 0L
}

object SystemSettingKind : ActionKind<SystemSettingAction>("systemSetting", SystemSettingAction::class.java) {

    override fun write(item: SystemSettingAction): JSONObject =
        JSONObject().put(SETTING, item.setting.id).put(VALUE, item.value).put(RESTORE, item.restore)

    /** An unknown setting or a value out of range throws, which drops this action alone. */
    override fun decode(json: JSONObject) = SystemSettingAction(
        requireNotNull(SystemSetting.of(json.getString(SETTING))) { "Unknown setting" },
        json.getInt(VALUE),
        json.optBoolean(RESTORE, true),
    )

    private const val SETTING = "setting"
    private const val VALUE = "value"
    private const val RESTORE = "restore"
}
