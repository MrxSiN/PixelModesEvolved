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

import my.github.MrxSiN.modeevolved.R
import my.github.MrxSiN.modeevolved.editor.EditorFacts
import my.github.MrxSiN.modeevolved.presentation.MovementPresentation
import my.github.MrxSiN.modeevolved.trigger.Movement
import my.github.MrxSiN.modeevolved.trigger.MovementTrigger
import my.github.MrxSiN.modeevolved.trigger.Trigger

/** Choose between the phone being on the move, left still or flying. */
object MovementEditor : KindEditor<Trigger> {

    override val presentation = MovementPresentation

    @Composable
    override fun Content(facts: EditorFacts, initial: Trigger?, onChange: (Trigger?) -> Unit) {
        var movement by rememberSaveable { mutableStateOf((initial as? MovementTrigger)?.movement) }
        LaunchedEffect(movement) { onChange(movement?.let(::MovementTrigger)) }

        Intro(stringResource(R.string.movement_intro))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (choice in Movement.entries) {
                ChoiceCard(
                    title = stringResource(presentation.titleOf(choice)),
                    detail = stringResource(presentation.detailOf(choice)),
                    icon = presentation.iconOf(choice),
                    selected = movement == choice,
                    onClick = { movement = choice },
                )
            }
        }
    }
}
