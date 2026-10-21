package my.github.MrxSiN.modeevolved.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

import my.github.MrxSiN.modeevolved.editor.EditorFacts
import my.github.MrxSiN.modeevolved.presentation.PowerPresentation
import my.github.MrxSiN.modeevolved.trigger.PowerTrigger
import my.github.MrxSiN.modeevolved.trigger.Trigger

/** Choose whether the Mode follows the device being on external power or off it. */
object PowerEditor : KindEditor<Trigger> {

    override val presentation = PowerPresentation

    @Composable
    override fun Content(facts: EditorFacts, initial: Trigger?, onChange: (Trigger?) -> Unit) {
        var on by rememberSaveable { mutableStateOf((initial as? PowerTrigger)?.connected) }
        LaunchedEffect(on) { onChange(on?.let(::PowerTrigger)) }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (choice in listOf(true, false)) {
                ChoiceCard(
                    title = stringResource(presentation.titleOf(choice)),
                    detail = stringResource(presentation.detailOf(choice)),
                    icon = presentation.iconOf(choice),
                    selected = on == choice,
                    onClick = { on = choice },
                )
            }
        }
    }
}
