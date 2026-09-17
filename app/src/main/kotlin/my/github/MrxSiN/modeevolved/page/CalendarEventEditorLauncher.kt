package my.github.MrxSiN.modeevolved.page

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import java.util.concurrent.Executors

import my.github.MrxSiN.modeevolved.calendar.CalendarConditionFilter
import my.github.MrxSiN.modeevolved.calendar.CalendarEventCodec
import my.github.MrxSiN.modeevolved.calendar.CalendarEventEditorInput
import my.github.MrxSiN.modeevolved.calendar.CalendarEventOption
import my.github.MrxSiN.modeevolved.calendar.EventFilter
import my.github.MrxSiN.modeevolved.editor.EditorIntents
import my.github.MrxSiN.modeevolved.editor.EditorSubject

/** Reads upcoming events off the UI thread, then opens their expressive picker. */
class CalendarEventEditorLauncher {

    private val reader = Executors.newSingleThreadExecutor()

    fun open(context: Context, mode: ModeRef, condition: Uri) {
        val activity = context.activity() ?: return
        reader.execute {
            val filter = CalendarConditionFilter.filterOf(condition)
            val events = upcomingEvents(activity, condition.getQueryParameter(CalendarConditionFilter.CALENDAR_ID)?.toLongOrNull())
                .toMutableList()
            // A chosen event with no instance in the window stays choosable.
            if (filter is EventFilter.OneEvent && events.none { it.id == filter.id }) {
                events.add(0, CalendarEventOption(filter.id, filter.title.ifBlank { "Event ${filter.id}" }, "", 0))
            }
            val input = CalendarEventEditorInput(mode.id, mode.name, filter, events)
            val intent = Intent()
                .setClassName(EditorIntents.PACKAGE, EditorIntents.ACTIVITY)
                .putExtra(EditorIntents.EXTRA_SUBJECT, EditorSubject.CALENDAR_EVENT.name)
                .putExtra(EditorIntents.EXTRA_INPUT, CalendarEventCodec.encodeInput(input))
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    activity.startActivityForResult(intent, EditorSubject.CALENDAR_EVENT.requestCode)
                }
            }
        }
    }

    private fun upcomingEvents(context: Context, calendarId: Long?): List<CalendarEventOption> = runCatching {
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now)
            ContentUris.appendId(it, now + EVENT_WINDOW_MILLIS)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
        )
        context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { rows ->
            buildList {
                while (rows.moveToNext()) {
                    val eventCalendarId = rows.getLong(1)
                    val title = rows.getString(3).orEmpty()
                    if (title.isNotBlank() && (calendarId == null || eventCalendarId == calendarId)) {
                        add(CalendarEventOption(rows.getLong(0), title, rows.getString(2).orEmpty(), rows.getLong(4)))
                    }
                }
            }
        }.orEmpty().distinctBy(CalendarEventOption::id).take(MAX_EVENTS)
    }.getOrDefault(emptyList())

    private companion object {
        const val EVENT_WINDOW_MILLIS = 30L * 24 * 60 * 60_000
        const val MAX_EVENTS = 60
    }
}
