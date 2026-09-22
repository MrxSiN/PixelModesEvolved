package my.github.MrxSiN.modeevolved.core

/**
 * One thing this module hooks, which an Android update can move out from under it.
 *
 * Named here once so the log line, the stored self-test result and the row on
 * the Mode page all mean the same thing by it. [id] is written to storage and
 * must outlive a rename; [logName] is only read by a person.
 */
enum class Feature(val id: String, val logName: String) {

    // In the Settings app.
    MODE_PAGE("modePage", "Mode page"),
    MODES_LIST("modesList", "Modes list"),
    EDITOR_RESULTS("editorResults", "editor results"),
    BUTTON_GROUP("buttonGroup", "button groups"),
    CALENDAR_ROW("calendarRow", "Calendar events"),
    SCHEDULE_REMOVAL("scheduleRemoval", "schedule removal"),
    SETTINGS_RESTART("settingsRestart", "app start"),

    // In system_server.
    AUTOMATION_START("automationStart", "boot completed"),
    CALENDAR_FILTER("calendarFilter", "stock calendar event filter"),
    TRIGGER_AUTOMATION("triggerAutomation", "trigger automation"),
    ACTION_AUTOMATION("actionAutomation", "action automation"),
    ;

    companion object {
        /** The feature stored as [id], or null when it belongs to another build. */
        fun of(id: String): Feature? = entries.firstOrNull { it.id == id }
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

/**
 * Installs one feature, logging and recording what happened.
 *
 * Every hook in this module goes through here. A hook that throws costs its own
 * feature and nothing else, which is what keeps a moved private API from taking
 * out Settings or system_server — and recording the outcome is what lets a
 * person see which feature went, instead of having to read logcat for it.
 */
class FeatureInstaller(private val logger: Logger, private val health: HealthRecorder) {

    fun install(feature: Feature, install: () -> Unit) {
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
