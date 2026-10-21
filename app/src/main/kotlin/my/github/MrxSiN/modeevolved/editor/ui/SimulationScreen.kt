package my.github.MrxSiN.modeevolved.editor.ui

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

import my.github.MrxSiN.modeevolved.R
import my.github.MrxSiN.modeevolved.editor.ItemFamilies
import my.github.MrxSiN.modeevolved.presentation.Durations
import my.github.MrxSiN.modeevolved.trigger.ModeRule
import my.github.MrxSiN.modeevolved.trigger.RuleSimulation

/**
 * Try a Mode's triggers with made-up answers and see what the Mode would do.
 *
 * Read-only: each switch is a pretend answer for one trigger, kept on this
 * screen alone. Nothing is read from the device, no Mode is switched and no
 * rule is saved; leaving forgets the answers.
 */
@Composable
fun SimulationScreen(modeName: String, rule: ModeRule, onBack: () -> Unit) {
    val resources = LocalContext.current.resources
    val met = remember(rule) { mutableStateListOf(*Array(rule.triggers.size) { false }) }
    val groups = RuleSimulation.groups(rule)
    val winner = RuleSimulation.metGroup(rule, met)

    EditorScaffold(title = stringResource(R.string.simulate_title), subtitle = modeName, onBack = onBack) {
        Note(stringResource(R.string.simulate_intro))

        groups.forEachIndexed { number, group ->
            val title = if (groups.size == 1) null else stringResource(R.string.simulate_group, number + 1)
            Section(title) {
                group.forEachIndexed { row, index ->
                    val trigger = rule.triggers[index]
                    SectionRow(
                        title = ItemFamilies.triggers.presentations.describe(resources, trigger),
                        position = RowPosition(isFirst = row == 0, isLast = row == group.count() - 1),
                        onClick = { met[index] = !met[index] },
                        supporting = stringResource(if (met[index]) R.string.simulate_met else R.string.simulate_not_met),
                        icon = ItemFamilies.triggers.presentations.of(trigger)?.icon,
                        trailing = { Switch(checked = met[index], onCheckedChange = { met[index] = it }) },
                    )
                }
            }
        }

        Section(stringResource(R.string.simulate_result)) {
            val wait = rule.stabilityMillis.takeIf { it > 0 }?.let { millis ->
                val label = Durations.label(millis)
                val wording = if (label.seconds) R.plurals.duration_seconds else R.plurals.duration_minutes
                resources.getQuantityString(wording, label.amount, label.amount)
            }
            SectionRow(
                title = stringResource(if (winner != null) R.string.simulate_on else R.string.simulate_off),
                position = RowPosition(isFirst = true, isLast = true),
                onClick = {},
                supporting = when {
                    winner != null && groups.size > 1 -> stringResource(R.string.simulate_on_because, winner + 1)
                    winner != null -> stringResource(R.string.simulate_on_all)
                    else -> stringResource(R.string.simulate_off_because)
                } + (wait?.let { "\n" + stringResource(R.string.simulate_wait, it) } ?: ""),
            )
        }
    }
}
