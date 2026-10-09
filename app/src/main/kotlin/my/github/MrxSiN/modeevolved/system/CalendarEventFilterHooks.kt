package my.github.MrxSiN.modeevolved.system

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
import my.github.MrxSiN.modeevolved.compat.SystemServerApis as SS
import my.github.MrxSiN.modeevolved.core.Feature
import my.github.MrxSiN.modeevolved.core.FeatureHooks
import my.github.MrxSiN.modeevolved.core.Logger
import my.github.MrxSiN.modeevolved.dex.HostResolver

/** Narrows Android's stock calendar condition to the [EventFilter] embedded by Settings. */
class CalendarEventFilterHooks(
    private val xposed: XposedInterface,
    private val apis: HostResolver,
    private val logger: Logger,
) : FeatureHooks {

    override val feature = Feature.CALENDAR_FILTER

    private val filters = Collections.synchronizedMap(WeakHashMap<Any, EventFilter>())

    override fun install() {
        val userContext = apis.field(SS.CALENDAR_TRACKER_USER_CONTEXT)

        xposed.hook(apis.method(SS.ZEN_MODE_CONFIG_PARSE_EVENT)).setExceptionMode(ExceptionMode.PROTECTIVE).intercept { chain ->
            val parsed = chain.proceed()
            val filter = (chain.args[0] as? Uri)?.let(CalendarConditionFilter::filterOf)
            if (parsed != null && filter != null && filter != EventFilter.AnyEvent) filters[parsed] = filter
            parsed
        }
        xposed.hook(apis.method(SS.CALENDAR_TRACKER_MEETS_ATTENDEE)).setExceptionMode(ExceptionMode.PROTECTIVE).intercept { chain ->
            val filter = filters[chain.args[0]] ?: return@intercept chain.proceed()
            val eventId = (chain.args[1] as Int).toLong()
            val context = userContext.get(chain.thisObject) as Context
            if (filter.matches(eventId) { titleOf(context, eventId) }) chain.proceed() else false
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
