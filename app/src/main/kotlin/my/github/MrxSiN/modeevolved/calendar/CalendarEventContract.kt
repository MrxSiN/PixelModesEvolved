package my.github.MrxSiN.modeevolved.calendar

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/** One calendar instance offered by the expressive event picker. */
data class CalendarEventOption(
    val id: Long,
    val title: String,
    val calendarName: String,
    val begin: Long,
)

/** Everything the Settings process hands the calendar-event editor. */
data class CalendarEventEditorInput(
    val modeId: String,
    val modeName: String,
    val filter: EventFilter,
    val events: List<CalendarEventOption>,
)

/** The filter chosen by the editor. */
data class CalendarEventEditorResult(
    val modeId: String,
    val filter: EventFilter,
)

/** JSON boundary used by the Settings and module-app processes. */
object CalendarEventCodec {

    fun encodeInput(input: CalendarEventEditorInput): String = JSONObject()
        .put(MODE_ID, input.modeId)
        .put(MODE_NAME, input.modeName)
        .put(FILTER, encodeFilter(input.filter))
        .put(EVENTS, JSONArray(input.events.map(::encodeEvent)))
        .toString()

    fun decodeInput(text: String): CalendarEventEditorInput {
        val json = JSONObject(text)
        val events = json.getJSONArray(EVENTS)
        return CalendarEventEditorInput(
            modeId = json.getString(MODE_ID),
            modeName = json.getString(MODE_NAME),
            filter = decodeFilter(json.optJSONObject(FILTER)),
            events = (0 until events.length()).map { decodeEvent(events.getJSONObject(it)) },
        )
    }

    fun encodeResult(result: CalendarEventEditorResult): String = JSONObject()
        .put(MODE_ID, result.modeId)
        .put(FILTER, encodeFilter(result.filter))
        .toString()

    fun decodeResult(text: String): CalendarEventEditorResult? = runCatching {
        val json = JSONObject(text)
        CalendarEventEditorResult(json.getString(MODE_ID), decodeFilter(json.getJSONObject(FILTER)))
    }.getOrNull()

    private fun encodeFilter(filter: EventFilter): JSONObject = when (filter) {
        EventFilter.AnyEvent -> JSONObject().put(KIND, ANY)
        is EventFilter.OneEvent -> JSONObject().put(KIND, ONE).put(ID, filter.id).put(TITLE, filter.title)
        is EventFilter.TitleKeyword -> JSONObject().put(KIND, KEYWORD).put(KEYWORD, filter.keyword)
    }

    private fun decodeFilter(json: JSONObject?): EventFilter = when (json?.optString(KIND)) {
        ONE -> EventFilter.OneEvent(json.getLong(ID), json.optString(TITLE))
        KEYWORD -> EventFilter.TitleKeyword(json.getString(KEYWORD))
        else -> EventFilter.AnyEvent
    }

    private fun encodeEvent(event: CalendarEventOption) = JSONObject()
        .put(ID, event.id)
        .put(TITLE, event.title)
        .put(CALENDAR_NAME, event.calendarName)
        .put(BEGIN, event.begin)

    private fun decodeEvent(json: JSONObject) = CalendarEventOption(
        id = json.getLong(ID),
        title = json.getString(TITLE),
        calendarName = json.optString(CALENDAR_NAME),
        begin = json.optLong(BEGIN),
    )

    private const val MODE_ID = "modeId"
    private const val MODE_NAME = "modeName"
    private const val FILTER = "filter"
    private const val EVENTS = "events"
    private const val KIND = "kind"
    private const val ANY = "any"
    private const val ONE = "event"
    private const val KEYWORD = "keyword"
    private const val ID = "id"
    private const val TITLE = "title"
    private const val CALENDAR_NAME = "calendarName"
    private const val BEGIN = "begin"
}

/** An [EventFilter] carried as extra parameters inside an otherwise stock event condition URI. */
object CalendarConditionFilter {
    const val EVENT_ID = "modeEvolvedEventId"
    const val EVENT_TITLE = "modeEvolvedEventTitle"
    const val KEYWORD = "modeEvolvedKeyword"
    const val CALENDAR_ID = "calendarId"

    private val OWN_PARAMETERS = setOf(EVENT_ID, EVENT_TITLE, KEYWORD)

    fun isEvent(uri: Uri): Boolean = uri.scheme == "condition" && uri.authority == "android" && uri.path == "/event"

    fun filterOf(uri: Uri): EventFilter {
        uri.getQueryParameter(KEYWORD)?.takeIf { it.isNotBlank() }?.let { return EventFilter.TitleKeyword(it) }
        val id = uri.getQueryParameter(EVENT_ID)?.toLongOrNull() ?: return EventFilter.AnyEvent
        return EventFilter.OneEvent(id, uri.getQueryParameter(EVENT_TITLE).orEmpty())
    }

    fun withFilter(uri: Uri, filter: EventFilter): Uri {
        // Calendar and invite-response remain owned by Android's stock controls.
        val kept = uri.queryParameterNames.filterNot { it in OWN_PARAMETERS }
        return uri.buildUpon().clearQuery().apply {
            kept.forEach { name -> uri.getQueryParameters(name).forEach { appendQueryParameter(name, it) } }
            when (filter) {
                EventFilter.AnyEvent -> Unit
                is EventFilter.OneEvent -> {
                    appendQueryParameter(EVENT_ID, filter.id.toString())
                    appendQueryParameter(EVENT_TITLE, filter.title)
                }
                is EventFilter.TitleKeyword -> appendQueryParameter(KEYWORD, filter.keyword.trim())
            }
        }.build()
    }
}
