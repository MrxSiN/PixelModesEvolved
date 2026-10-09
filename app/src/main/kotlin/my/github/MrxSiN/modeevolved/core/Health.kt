package my.github.MrxSiN.modeevolved.core

import my.github.MrxSiN.modeevolved.compat.Host

/**
 * One thing this module hooks, which an Android update can move out from under it, and the
 * process it is hooked in.
 *
 * Named here once so the log line, the stored self-test result, the compatibility contracts
 * (`FeatureContracts`) and the row on the compatibility page all mean the same thing by it.
 * [id] is written to storage and must outlive a rename; [logName] is only read by a person.
 * Each feature fails on its own: a missing member costs its feature and no other.
 */
enum class Feature(val id: String, val logName: String, val host: Host) {

    MODE_PAGE("modePage", "Mode page", Host.SETTINGS),
    MODES_LIST("modesList", "Modes list", Host.SETTINGS),
    EDITOR_RESULTS("editorResults", "editor results", Host.SETTINGS),
    BUTTON_GROUP("buttonGroup", "button groups", Host.SETTINGS),
    CALENDAR_ROW("calendarRow", "Calendar events", Host.SETTINGS),
    SCHEDULE_REMOVAL("scheduleRemoval", "schedule removal", Host.SETTINGS),
    SETTINGS_RESTART("settingsRestart", "app start", Host.SETTINGS),

    AUTOMATION_START("automationStart", "boot completed", Host.SYSTEM_SERVER),
    CALENDAR_FILTER("calendarFilter", "stock calendar event filter", Host.SYSTEM_SERVER),
    TRIGGER_AUTOMATION("triggerAutomation", "trigger automation", Host.SYSTEM_SERVER),
    ACTION_AUTOMATION("actionAutomation", "action automation", Host.SYSTEM_SERVER),

    // System settings a Mode can hold, each available or not on its own.
    SETTING_AUTO_ROTATE("settingAutoRotate", "Auto-rotate", Host.SYSTEM_SERVER),
    SETTING_DARK_THEME("settingDarkTheme", "Dark theme", Host.SYSTEM_SERVER),
    SETTING_BRIGHTNESS("settingBrightness", "Brightness", Host.SYSTEM_SERVER),
    SETTING_ADAPTIVE_BRIGHTNESS("settingAdaptiveBrightness", "Adaptive brightness", Host.SYSTEM_SERVER),
    SETTING_EXTRA_DIM("settingExtraDim", "Extra dim", Host.SYSTEM_SERVER),
    SETTING_SCREEN_TIMEOUT("settingScreenTimeout", "Screen timeout", Host.SYSTEM_SERVER),
    SETTING_ALWAYS_ON_DISPLAY("settingAlwaysOnDisplay", "Always show time and info", Host.SYSTEM_SERVER),
    SETTING_SMOOTH_DISPLAY("settingSmoothDisplay", "Smooth Display", Host.SYSTEM_SERVER),
    SETTING_RINGER_MODE("settingRingerMode", "Ring mode", Host.SYSTEM_SERVER),
    SETTING_RING_VOLUME("settingRingVolume", "Ring volume", Host.SYSTEM_SERVER),
    SETTING_NOTIFICATION_VOLUME("settingNotificationVolume", "Notification volume", Host.SYSTEM_SERVER),
    SETTING_MEDIA_VOLUME("settingMediaVolume", "Media volume", Host.SYSTEM_SERVER),
    SETTING_ALARM_VOLUME("settingAlarmVolume", "Alarm volume", Host.SYSTEM_SERVER),
    SETTING_WIFI("settingWifi", "Wi-Fi", Host.SYSTEM_SERVER),
    SETTING_BLUETOOTH("settingBluetooth", "Bluetooth", Host.SYSTEM_SERVER),
    SETTING_NFC("settingNfc", "NFC", Host.SYSTEM_SERVER),
    SETTING_HOTSPOT("settingHotspot", "Hotspot", Host.SYSTEM_SERVER),
    SETTING_MOBILE_DATA("settingMobileData", "Mobile data", Host.SYSTEM_SERVER),
    ;

    companion object {
        /** The feature stored as [id], or null when it belongs to another build. */
        fun of(id: String): Feature? = entries.firstOrNull { it.id == id }

        fun of(host: Host): List<Feature> = entries.filter { it.host == host }
    }
}

/** Whether one feature installed, and what stopped it when it did not. */
data class FeatureHealth(val feature: Feature, val ok: Boolean, val detail: String? = null)

/** Where the outcome of installing each feature is kept, for the compatibility check on a Mode's page. */
fun interface HealthRecorder {

    fun record(health: FeatureHealth)
}

/** A recorder that keeps nothing, for parts with nowhere to write. */
object NoHealthRecorder : HealthRecorder {
    override fun record(health: FeatureHealth) = Unit
}

/**
 * Holds results until there is somewhere to put them.
 *
 * system_server hooks before it has a context, so its first results exist
 * before the store that keeps them does. Those are replayed the moment
 * [writeTo] is handed the real recorder, and go straight through after that.
 *
 * Written from the thread that installs hooks and then from the boot thread,
 * one after the other, never at the same time.
 */
class BufferedHealthRecorder : HealthRecorder {

    private val held = linkedMapOf<Feature, FeatureHealth>()

    @Volatile private var downstream: HealthRecorder? = null

    override fun record(health: FeatureHealth) {
        val target = downstream
        if (target == null) held[health.feature] = health else target.record(health)
    }

    /** Sends everything held so far to [recorder], and everything after it straight through. */
    fun writeTo(recorder: HealthRecorder) {
        held.values.forEach(recorder::record)
        held.clear()
        downstream = recorder
    }
}

/** One feature's hooks, installed as a unit and failing as one. */
interface FeatureHooks {

    val feature: Feature

    /** Installs the hooks. Throwing costs [feature] and nothing else. */
    fun install()
}

/**
 * Installs one feature, logging and recording what happened.
 *
 * Every hook in this module goes through here. A feature whose host lacks a
 * member its contract requires is not installed at all, and says which member;
 * one that throws while installing costs itself and nothing else. That is what
 * keeps a moved private API from taking out Settings or system_server, and
 * recording the outcome is what lets a person see which feature went, instead
 * of having to read logcat for it.
 *
 * @param missing the ids of the required contract members [Feature]'s host lacks.
 */
class FeatureInstaller(
    private val logger: Logger,
    private val health: HealthRecorder,
    private val missing: (Feature) -> List<String> = { emptyList() },
) {

    fun install(hooks: FeatureHooks) = install(hooks.feature, hooks::install)

    fun install(feature: Feature, install: () -> Unit) {
        val absent = runCatching { missing(feature) }.getOrElse { listOf(summarise(it)) }
        if (absent.isNotEmpty()) {
            val detail = "Missing ${absent.joinToString()}"
            logger.warn("${feature.logName} is unavailable on this build: $detail")
            health.record(FeatureHealth(feature, ok = false, detail = detail))
            return
        }
        runCatching(install)
            .onSuccess {
                logger.info("Hooked ${feature.logName}")
                health.record(FeatureHealth(feature, ok = true))
            }
            .onFailure { error ->
                logger.warn("Could not hook ${feature.logName}", error)
                health.record(FeatureHealth(feature, ok = false, detail = summarise(error)))
            }
    }

    /** The one line worth showing a person: what the failure said, or its type when it said nothing. */
    private fun summarise(error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
}
