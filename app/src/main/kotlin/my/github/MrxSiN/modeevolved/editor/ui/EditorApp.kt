package my.github.MrxSiN.modeevolved.editor.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

import my.github.MrxSiN.modeevolved.R
import my.github.MrxSiN.modeevolved.editor.EditorInput
import my.github.MrxSiN.modeevolved.editor.EditorResult

/** The words that differ between editing triggers and editing actions. */
data class EditorTexts(val addTitle: Int, val addIntro: Int, val removeTitle: Int, val removeText: Int)

/**
 * The editor's two steps: choose a kind (only when adding), then describe the item.
 *
 * @param onResult ends the editor with an answer for the Settings page.
 * @param onClose ends the editor without one.
 */
@Composable
fun <I : Any> EditorApp(
    input: EditorInput<I>,
    editors: List<KindEditor<I>>,
    texts: EditorTexts,
    onResult: (EditorResult<I>) -> Unit,
    onClose: () -> Unit,
) {
    val original = input.original
    var kindId by rememberSaveable {
        mutableStateOf(original?.let { item -> editors.firstOrNull { it.presentation.kind.cast(item) != null }?.presentation?.kind?.id })
    }
    val editor = editors.firstOrNull { it.presentation.kind.id == kindId }

    val motion = MaterialTheme.motionScheme

    // Choosing a kind moves forward, going back to the choice moves back: a shared-axis
    // slide and fade on the expressive spatial spring.
    AnimatedContent(
        targetState = editor,
        transitionSpec = {
            val forward = targetState != null
            val slide = motion.defaultSpatialSpec<IntOffset>()
            val fade = motion.defaultEffectsSpec<Float>()
            (slideInHorizontally(slide) { width -> if (forward) width / 4 else -width / 4 } + fadeIn(fade)) togetherWith
                (slideOutHorizontally(slide) { width -> if (forward) -width / 4 else width / 4 } + fadeOut(fade))
        },
        label = "editor step",
    ) { shown ->
        if (shown == null) {
            KindPicker(input, editors, texts, onPick = { kindId = it.presentation.kind.id }, onClose = onClose)
        } else {
            // Going back from a kind chosen here returns to the choice; editing an existing item closes.
            val back = if (original == null) ({ kindId = null }) else onClose
            BackHandler(onBack = back)
            KindScreen(input, shown, texts, onBack = back, onResult = onResult)
        }
    }
}

@Composable
private fun <I : Any> KindPicker(
    input: EditorInput<I>,
    editors: List<KindEditor<I>>,
    texts: EditorTexts,
    onPick: (KindEditor<I>) -> Unit,
    onClose: () -> Unit,
) {
    EditorScaffold(
        title = stringResource(texts.addTitle),
        subtitle = input.modeName,
        onBack = onClose,
    ) {
        Intro(stringResource(texts.addIntro))
        Section(title = null) {
            editors.forEachIndexed { index, editor ->
                SectionRow(
                    title = stringResource(editor.presentation.label),
                    supporting = stringResource(editor.presentation.hint),
                    icon = editor.presentation.icon,
                    position = positionAt(index, editors.size),
                    onClick = { onPick(editor) },
                )
            }
        }
    }
}

@Composable
private fun <I : Any> KindScreen(
    input: EditorInput<I>,
    editor: KindEditor<I>,
    texts: EditorTexts,
    onBack: () -> Unit,
    onResult: (EditorResult<I>) -> Unit,
) {
    val original = input.original
    val initial = original?.takeIf { editor.presentation.kind.cast(it) != null }
    var item by remember(editor) { mutableStateOf(initial) }
    var confirmRemove by remember { mutableStateOf(false) }

    EditorScaffold(
        title = stringResource(editor.presentation.label),
        subtitle = input.modeName,
        onBack = onBack,
        fillsScreen = editor.fillsScreen,
        actions = {
            if (original != null) {
                IconButton(onClick = { confirmRemove = true }, shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.action_remove))
                }
            }
        },
        bottomBar = {
            SaveBar(enabled = item != null) {
                item?.let { onResult(EditorResult.Save(input.modeId, original, it)) }
            }
        },
    ) {
        editor.Content(input.facts, initial) { item = it }
    }

    if (confirmRemove && original != null) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            icon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
            title = { Text(stringResource(texts.removeTitle)) },
            text = { Text(stringResource(texts.removeText, input.modeName)) },
            confirmButton = {
                Button(onClick = { onResult(EditorResult.Remove(input.modeId, original)) }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.action_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

/**
 * The shared page: a collapsing expressive app bar over grouped content, or a
 * fixed bar over content that fills the screen, such as a map.
 */
@Composable
internal fun EditorScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    fillsScreen: Boolean = false,
    actions: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val backButton = @Composable {
        IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
        }
    }

    Scaffold(
        modifier = if (fillsScreen) Modifier.fillMaxSize() else Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            if (fillsScreen) {
                TopAppBar(
                    title = { Text(title) },
                    subtitle = { Text(subtitle) },
                    navigationIcon = backButton,
                    actions = { actions() },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                )
            } else {
                LargeFlexibleTopAppBar(
                    title = { Text(title) },
                    subtitle = { Text(subtitle) },
                    navigationIcon = backButton,
                    actions = { actions() },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
            }
        },
        bottomBar = bottomBar,
    ) { insets ->
        val layout = Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp)
        Column(
            modifier = if (fillsScreen) layout else layout.verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()
        }
    }
}

@Composable
internal fun SaveBar(enabled: Boolean, onSave: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Box(modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Button(
                onClick = onSave,
                enabled = enabled,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
            ) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null)
                Text(
                    stringResource(R.string.action_save),
                    style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight),
                    modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                )
            }
        }
    }
}
