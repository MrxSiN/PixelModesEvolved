package my.github.MrxSiN.modeevolved.editor

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

import my.github.MrxSiN.modeevolved.R
import my.github.MrxSiN.modeevolved.calendar.CalendarEventCodec
import my.github.MrxSiN.modeevolved.calendar.CalendarEventEditorResult
import my.github.MrxSiN.modeevolved.editor.ui.ActionEditors
import my.github.MrxSiN.modeevolved.editor.ui.CalendarEventEditor
import my.github.MrxSiN.modeevolved.editor.ui.EditorApp
import my.github.MrxSiN.modeevolved.editor.ui.EditorTexts
import my.github.MrxSiN.modeevolved.editor.ui.EditorTheme
import my.github.MrxSiN.modeevolved.editor.ui.KindEditor
import my.github.MrxSiN.modeevolved.editor.ui.TriggerEditors

/**
 * Adds or edits one trigger or action, for the Settings Mode page that started it.
 *
 * Answers only the Settings app: it starts this for a result and is the
 * process that stores what comes back. Opened any other way, it closes.
 */
class EditorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val subject = intent.getStringExtra(EditorIntents.EXTRA_SUBJECT)
            ?.takeIf { callingPackage == EditorIntents.SETTINGS_PACKAGE }
            ?.let { runCatching { EditorSubject.valueOf(it) }.getOrNull() }
            ?: return finish()

        when (subject) {
            EditorSubject.TRIGGER -> show(ItemFamilies.triggers, TriggerEditors.all, TRIGGER_TEXTS)
            EditorSubject.ACTION -> show(ItemFamilies.actions, ActionEditors.all, ACTION_TEXTS)
            EditorSubject.CALENDAR_EVENT -> showCalendarEvent()
        }
    }

    private fun showCalendarEvent() {
        val input = intent.getStringExtra(EditorIntents.EXTRA_INPUT)
            ?.let { runCatching { CalendarEventCodec.decodeInput(it) }.getOrNull() }
            ?: return finish()
        setContent {
            EditorTheme {
                CalendarEventEditor(
                    input = input,
                    onSave = { filter ->
                        val result = CalendarEventEditorResult(input.modeId, filter)
                        setResult(RESULT_OK, Intent().putExtra(EditorIntents.EXTRA_RESULT, CalendarEventCodec.encodeResult(result)))
                        finish()
                    },
                    onClose = ::finish,
                )
            }
        }
    }

    private fun <I : Any> show(family: ItemFamily<I, *>, editors: List<KindEditor<I>>, texts: EditorTexts) {
        val input = intent.getStringExtra(EditorIntents.EXTRA_INPUT)
            ?.let { runCatching { family.codec.decodeInput(it) }.getOrNull() }
            ?: return finish()

        setContent {
            EditorTheme {
                EditorApp(
                    input = input,
                    editors = editors,
                    texts = texts,
                    onResult = { result ->
                        setResult(RESULT_OK, Intent().putExtra(EditorIntents.EXTRA_RESULT, family.codec.encodeResult(result)))
                        finish()
                    },
                    onClose = ::finish,
                )
            }
        }
    }

    private companion object {
        val TRIGGER_TEXTS = EditorTexts(R.string.editor_add_title, R.string.editor_add_intro, R.string.remove_title, R.string.remove_text)
        val ACTION_TEXTS = EditorTexts(R.string.actions_add_title, R.string.actions_add_intro, R.string.actions_remove_title, R.string.actions_remove_text)
    }
}
