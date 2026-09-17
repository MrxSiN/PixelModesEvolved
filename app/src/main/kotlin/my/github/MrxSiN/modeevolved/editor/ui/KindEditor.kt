package my.github.MrxSiN.modeevolved.editor.ui

import androidx.compose.runtime.Composable

import my.github.MrxSiN.modeevolved.action.ModeAction
import my.github.MrxSiN.modeevolved.editor.EditorFacts
import my.github.MrxSiN.modeevolved.presentation.Presentation
import my.github.MrxSiN.modeevolved.trigger.Trigger

/**
 * The editor screen for one kind of item.
 *
 * A new kind adds one implementation and registers it in [TriggerEditors] or
 * [ActionEditors]; the shared scaffold, saving and removing stay untouched.
 */
interface KindEditor<I : Any> {

    val presentation: Presentation<I, *>

    /** Whether the content is a map, which must not sit inside a scrolling page. */
    val fillsScreen: Boolean get() = false

    /**
     * @param initial the item being edited when it is of this kind, else null.
     * @param onChange the item the screen currently describes, or null while it is incomplete.
     */
    @Composable
    fun Content(facts: EditorFacts, initial: I?, onChange: (I?) -> Unit)
}

object TriggerEditors {
    val all: List<KindEditor<Trigger>> =
        listOf(WifiEditor, BluetoothEditor, AreaEditor, MovementEditor, AirplaneEditor)
}

object ActionEditors {
    val all: List<KindEditor<ModeAction>> = listOf(LaunchAppEditor, BroadcastEditor, TaskerTaskEditor)
}
