package my.github.MrxSiN.modeevolved.editor.ui

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

import my.github.MrxSiN.modeevolved.R
import my.github.MrxSiN.modeevolved.calendar.CalendarEventEditorInput
import my.github.MrxSiN.modeevolved.calendar.CalendarEventOption
import my.github.MrxSiN.modeevolved.calendar.EventFilter

/** The three ways to narrow Android's stock calendar schedule, in the order they are offered. */
private enum class FilterKind(val label: Int) {
    ANY(R.string.calendar_filter_any),
    ONE(R.string.calendar_filter_one),
    KEYWORD(R.string.calendar_filter_keyword),
}

/** Material 3 Expressive editor for the event filter inside Android's stock calendar schedule. */
@Composable
fun CalendarEventEditor(
    input: CalendarEventEditorInput,
    onSave: (EventFilter) -> Unit,
    onClose: () -> Unit,
) {
    val initial = input.filter
    var kind by rememberSaveable {
        mutableStateOf(
            when (initial) {
                EventFilter.AnyEvent -> FilterKind.ANY
                is EventFilter.OneEvent -> FilterKind.ONE
                is EventFilter.TitleKeyword -> FilterKind.KEYWORD
            },
        )
    }
    var selectedId by rememberSaveable { mutableStateOf((initial as? EventFilter.OneEvent)?.id) }
    var keyword by rememberSaveable { mutableStateOf((initial as? EventFilter.TitleKeyword)?.keyword.orEmpty()) }

    val filter: EventFilter? = when (kind) {
        FilterKind.ANY -> EventFilter.AnyEvent
        FilterKind.ONE -> input.events.firstOrNull { it.id == selectedId }?.let { EventFilter.OneEvent(it.id, it.title) }
        FilterKind.KEYWORD -> keyword.trim().takeIf { it.isNotEmpty() }?.let(EventFilter::TitleKeyword)
    }

    EditorScaffold(
        title = stringResource(R.string.calendar_event_choose),
        subtitle = input.modeName,
        onBack = onClose,
        bottomBar = { SaveBar(enabled = filter != null) { filter?.let(onSave) } },
    ) {
        Intro(stringResource(R.string.calendar_event_intro))
        ConnectedChoice(FilterKind.entries, kind, { kind = it }) { stringResource(it.label) }

        when (kind) {
            FilterKind.ANY -> Note(stringResource(R.string.calendar_event_any_hint))
            FilterKind.ONE -> OneEventChoice(input.events, selectedId) { selectedId = it }
            FilterKind.KEYWORD -> KeywordChoice(input.events, keyword) { keyword = it }
        }
    }
}

@Composable
private fun OneEventChoice(events: List<CalendarEventOption>, selectedId: Long?, onSelect: (Long) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = events.filter { it.title.contains(query.trim(), ignoreCase = true) }

    Section(stringResource(R.string.calendar_event_upcoming)) {
        SearchField(query, { query = it }, stringResource(R.string.calendar_event_search))
    }
    when {
        events.isEmpty() -> Intro(stringResource(R.string.calendar_event_none))
        shown.isEmpty() -> Intro(stringResource(R.string.search_no_results, query.trim()))
        else -> EventRows(shown, selectedId, onSelect)
    }
}

@Composable
private fun KeywordChoice(events: List<CalendarEventOption>, keyword: String, onKeywordChange: (String) -> Unit) {
    Section(stringResource(R.string.calendar_keyword_section)) {
        OutlinedTextField(
            value = keyword,
            onValueChange = onKeywordChange,
            label = { Text(stringResource(R.string.calendar_keyword_label)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Note(stringResource(R.string.calendar_keyword_hint))

    val typed = keyword.trim()
    if (typed.isEmpty()) return
    // The same test system_server applies, so the preview never disagrees with what triggers.
    val preview = EventFilter.TitleKeyword(typed)
    val matches = events.filter { event -> preview.matches(event.id) { event.title } }
    Section(stringResource(R.string.calendar_keyword_matches)) {
        if (matches.isEmpty()) Intro(stringResource(R.string.calendar_keyword_no_matches))
    }
    if (matches.isNotEmpty()) EventRows(matches, selectedId = null, onSelect = null)
}

/** Events as grouped rows; selectable with a radio button only when [onSelect] is given. */
@Composable
private fun EventRows(events: List<CalendarEventOption>, selectedId: Long?, onSelect: ((Long) -> Unit)?) {
    val context = LocalContext.current
    Section(title = null) {
        events.forEachIndexed { index, event ->
            SectionRow(
                title = event.title,
                supporting = event.supportingText(context),
                icon = R.drawable.ic_trigger_calendar,
                position = positionAt(index, events.size),
                selected = if (onSelect == null) null else selectedId == event.id,
                onClick = { onSelect?.invoke(event.id) },
            )
        }
    }
}

private fun CalendarEventOption.supportingText(context: Context): String {
    if (begin <= 0) return calendarName.ifBlank { context.getString(R.string.calendar_event_saved) }
    val date = DateUtils.formatDateTime(
        context,
        begin,
        DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL,
    )
    return if (calendarName.isBlank()) date else "$date · $calendarName"
}
