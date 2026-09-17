package my.github.MrxSiN.modeevolved.page

import android.annotation.SuppressLint
import android.content.Context

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.ExceptionMode

import my.github.MrxSiN.modeevolved.R
import my.github.MrxSiN.modeevolved.calendar.CalendarConditionFilter
import my.github.MrxSiN.modeevolved.calendar.EventFilter
import my.github.MrxSiN.modeevolved.core.Logger

/** Adds an event filter to Android's existing Calendar events schedule page. */
@SuppressLint("PrivateApi")
class StockCalendarHooks(
    private val xposed: XposedInterface,
    private val classLoader: ClassLoader,
    private val logger: Logger,
    private val textFor: (Context) -> ModuleText,
) {

    fun install() {
        runCatching {
            val controller = classLoader.loadClass(CONTROLLER)
            val preference = classLoader.loadClass("androidx.preference.Preference")
            val zenMode = classLoader.loadClass("com.android.settingslib.notification.modes.ZenMode")
            val update = controller.getDeclaredMethod("updateState", preference, zenMode)
            val section = StockCalendarSection(
                PreferenceApi(classLoader),
                ZenModeApi(classLoader),
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
            .onSuccess { logger.info("Hooked Calendar events") }
            .onFailure { logger.warn("Could not hook Calendar events", it) }
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
