package my.github.MrxSiN.modeevolved.system

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import java.util.Collections
import java.util.WeakHashMap

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.ExceptionMode

import my.github.MrxSiN.modeevolved.calendar.CalendarConditionFilter
import my.github.MrxSiN.modeevolved.calendar.EventFilter
import my.github.MrxSiN.modeevolved.core.Feature
import my.github.MrxSiN.modeevolved.core.FeatureInstaller
import my.github.MrxSiN.modeevolved.core.Logger
import my.github.MrxSiN.modeevolved.dex.ClassFinder

/** Narrows Android's stock calendar condition to the [EventFilter] embedded by Settings. */
@SuppressLint("PrivateApi", "SoonBlockedPrivateApi")
class CalendarEventFilterHooks(
    private val xposed: XposedInterface,
    private val classes: ClassFinder,
    private val features: FeatureInstaller,
    private val logger: Logger,
) {

    private val filters = Collections.synchronizedMap(WeakHashMap<Any, EventFilter>())

    fun install() {
        features.install(Feature.CALENDAR_FILTER) {
            val config = classes.load("android.service.notification.ZenModeConfig")
            val eventInfo = classes.load("android.service.notification.ZenModeConfig\$EventInfo")
            val parse = classes.member("ZenModeConfig.tryParseEventConditionId(Uri)") {
                config.getDeclaredMethod("tryParseEventConditionId", Uri::class.java)
            }
            val tracker = classes.load("com.android.server.modes.CalendarTracker")
            val userContext = classes.member("CalendarTracker.mUserContext") {
                tracker.getDeclaredField("mUserContext").apply { isAccessible = true }
            }
            val meetsAttendee = classes.member("CalendarTracker.meetsAttendee(EventInfo, int, String)") {
                tracker.getDeclaredMethod(
                    "meetsAttendee",
                    eventInfo,
                    Int::class.javaPrimitiveType,
                    String::class.java,
                )
            }

            xposed.hook(parse).setExceptionMode(ExceptionMode.PROTECTIVE).intercept { chain ->
                val parsed = chain.proceed()
                val filter = (chain.args[0] as? Uri)?.let(CalendarConditionFilter::filterOf)
                if (parsed != null && filter != null && filter != EventFilter.AnyEvent) filters[parsed] = filter
                parsed
            }
            xposed.hook(meetsAttendee).setExceptionMode(ExceptionMode.PROTECTIVE).intercept { chain ->
                val filter = filters[chain.args[0]] ?: return@intercept chain.proceed()
                val eventId = (chain.args[1] as Int).toLong()
                val context = userContext.get(chain.thisObject) as Context
                if (filter.matches(eventId) { titleOf(context, eventId) }) chain.proceed() else false
            }
        }
    }

    /** The event's title, read as the user whose calendar the tracker watches. */
    private fun titleOf(context: Context, eventId: Long): String? = runCatching {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        context.contentResolver.query(uri, arrayOf(CalendarContract.Events.TITLE), null, null, null)?.use { rows ->
            if (rows.moveToFirst()) rows.getString(0) else null
        }
    }.onFailure { logger.warn("Could not read the title of event $eventId", it) }.getOrNull()
}
