package my.github.MrxSiN.modeevolved.page

import android.annotation.SuppressLint
import android.content.Context

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.ExceptionMode

import my.github.MrxSiN.modeevolved.R
import my.github.MrxSiN.modeevolved.calendar.CalendarConditionFilter
import my.github.MrxSiN.modeevolved.calendar.EventFilter
import my.github.MrxSiN.modeevolved.core.Feature
import my.github.MrxSiN.modeevolved.core.FeatureInstaller
import my.github.MrxSiN.modeevolved.core.Logger
import my.github.MrxSiN.modeevolved.dex.ClassFinder
import my.github.MrxSiN.modeevolved.dex.SettingsClasses

/** Adds an event filter to Android's existing Calendar events schedule page. */
@SuppressLint("PrivateApi")
class StockCalendarHooks(
    private val xposed: XposedInterface,
    private val classes: ClassFinder,
    private val features: FeatureInstaller,
    private val logger: Logger,
    private val textFor: (Context) -> ModuleText,
) {

    fun install() {
        features.install(Feature.CALENDAR_ROW) {
            val controller = classes.load(CONTROLLER)
            val preference = classes.load("androidx.preference.Preference")
            val zenMode = classes.find(SettingsClasses.zenMode)
            val update = classes.member("ZenModeSetCalendarPreferenceController.updateState(Preference, ZenMode)") {
                controller.getDeclaredMethod("updateState", preference, zenMode)
            }
            val section = StockCalendarSection(
                PreferenceApi(classes),
                ZenModeApi(classes),
                CalendarEventEditorLauncher(),
                textFor,
            )

            xposed.hook(update).setExceptionMode(ExceptionMode.PROTECTIVE).intercept { chain ->
                val result = chain.proceed()
                runCatching { section.render(chain.args[0], chain.args[1]) }
                    .onFailure { logger.warn("Could not extend Calendar events", it) }
                result
            }
        }
    }

    private companion object {
        const val CONTROLLER = "com.android.settings.notification.modes.ZenModeSetCalendarPreferenceController"
    }
}

/** Adds one stock-styled row that opens the expressive event filter editor. */
private class StockCalendarSection(
    private val preferences: PreferenceApi,
    private val modes: ZenModeApi,
    private val editor: CalendarEventEditorLauncher,
    private val textFor: (Context) -> ModuleText,
) {

    fun render(category: Any, mode: Any) {
        val condition = modes.conditionId(mode)?.takeIf(CalendarConditionFilter::isEvent) ?: return
        val context = preferences.context(category)
        val text = textFor(context)
        val ref = modes.ref(mode)
        val summary = when (val filter = CalendarConditionFilter.filterOf(condition)) {
            EventFilter.AnyEvent -> text.string(R.string.calendar_event_any)
            is EventFilter.OneEvent -> filter.title
            is EventFilter.TitleKeyword -> text.raw.getString(R.string.calendar_keyword_summary, filter.keyword)
        }

        preferences.replaceRows(
            category,
            KEY_PREFIX,
            listOf(
                PreferenceRow.Link(
                    key = EVENT_KEY,
                    title = text.string(R.string.calendar_event_filter),
                    summary = summary,
                    icon = null,
                    iconSpaceReserved = false,
                ) { editor.open(context, ref, condition) },
            ),
        )
    }

    private companion object {
        const val KEY_PREFIX = "mode_evolved_calendar_"
        const val EVENT_KEY = "${KEY_PREFIX}event"
    }
}
