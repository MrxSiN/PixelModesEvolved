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
import my.github.MrxSiN.modeevolved.presentation.BluetoothPresentation
import my.github.MrxSiN.modeevolved.trigger.BluetoothTrigger
import my.github.MrxSiN.modeevolved.trigger.Trigger

/**
 * Pick one paired device, narrowed by name or address.
 *
 * A device since unpaired stays listed while it is the one being edited.
 */
object BluetoothEditor : KindEditor<Trigger> {

    override val presentation = BluetoothPresentation

    @Composable
    override fun Content(facts: EditorFacts, initial: Trigger?, onChange: (Trigger?) -> Unit) {
        val original = initial as? BluetoothTrigger
        val devices = (listOfNotNull(original) + facts.pairedDevices).distinctBy { it.address }
        var address by rememberSaveable { mutableStateOf(original?.address) }
        var query by rememberSaveable { mutableStateOf("") }

        LaunchedEffect(address) { onChange(devices.firstOrNull { it.address == address }) }

        if (devices.isEmpty()) {
            Intro(stringResource(R.string.bluetooth_none))
            return
        }

        val typed = query.trim()
        val shown = devices.filter { it.name.contains(typed, ignoreCase = true) || it.address.contains(typed, ignoreCase = true) }

        Section(stringResource(R.string.bluetooth_paired)) {
            SearchField(query, { query = it }, stringResource(R.string.bluetooth_search))
        }
        if (shown.isEmpty()) {
            Intro(stringResource(R.string.search_no_results, typed))
            return
        }
        Section(title = null) {
            shown.forEachIndexed { index, device ->
                SectionRow(
                    title = device.name.ifEmpty { device.address },
                    supporting = device.address,
                    icon = R.drawable.ic_trigger_bluetooth,
                    position = positionAt(index, shown.size),
                    selected = address == device.address,
                    onClick = { address = device.address },
                )
            }
        }
    }
}
