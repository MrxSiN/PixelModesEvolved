package my.github.MrxSiN.modeevolved.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource

import my.github.MrxSiN.modeevolved.R
import my.github.MrxSiN.modeevolved.editor.EditorFacts
import my.github.MrxSiN.modeevolved.presentation.WifiPresentation
import my.github.MrxSiN.modeevolved.trigger.Trigger
import my.github.MrxSiN.modeevolved.trigger.WifiTrigger

/**
 * Pick the connected network or a saved one, or name another.
 *
 * One field both narrows the saved list and names a network the phone has
 * never joined, since a phone can hold dozens of saved networks.
 */
object WifiEditor : KindEditor<Trigger> {

    override val presentation = WifiPresentation

    @Composable
    override fun Content(facts: EditorFacts, initial: Trigger?, onChange: (Trigger?) -> Unit) {
        var selected by rememberSaveable { mutableStateOf((initial as? WifiTrigger)?.ssid) }
        var query by rememberSaveable { mutableStateOf("") }
        LaunchedEffect(selected) { onChange(selected?.let(::WifiTrigger)) }

        facts.currentSsid?.let { current ->
            Section(stringResource(R.string.wifi_current)) {
                SectionRow(
                    title = current,
                    supporting = stringResource(R.string.wifi_current_hint),
                    icon = R.drawable.ic_trigger_wifi,
                    position = positionAt(0, 1),
                    selected = selected == current,
                    onClick = { selected = current },
                )
            }
        }

        val typed = query.trim()
        val known = listOfNotNull(facts.currentSsid) + facts.savedSsids
        val offerTyped = typed.isNotEmpty() && typed !in known
        // A network being edited that is no longer saved stays choosable.
        val saved = (listOfNotNull(selected?.takeIf { it !in known }) + facts.savedSsids)
            .filterNot { it == facts.currentSsid || (offerTyped && it == typed) }
            .filter { it.contains(typed, ignoreCase = true) }

        Section(stringResource(R.string.wifi_saved)) {
            SearchField(query, { query = it }, stringResource(R.string.wifi_search))
        }

        val rows = (if (offerTyped) 1 else 0) + saved.size
        if (rows > 0) {
            Section(title = null) {
                if (offerTyped) {
                    SectionRow(
                        title = stringResource(R.string.wifi_typed, typed),
                        supporting = stringResource(R.string.wifi_typed_hint),
                        position = positionAt(0, rows),
                        selected = selected == typed,
                        onClick = { selected = typed },
                    )
                }
                val offset = rows - saved.size
                saved.forEachIndexed { index, name ->
                    SectionRow(
                        title = name,
                        position = positionAt(index + offset, rows),
                        selected = selected == name,
                        onClick = { selected = name },
                    )
                }
            }
        }
    }
}
