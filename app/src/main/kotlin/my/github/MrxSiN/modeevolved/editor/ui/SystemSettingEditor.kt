package my.github.MrxSiN.modeevolved.editor.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

import my.github.MrxSiN.modeevolved.R
import my.github.MrxSiN.modeevolved.action.ModeAction
import my.github.MrxSiN.modeevolved.action.SettingGroup
import my.github.MrxSiN.modeevolved.action.SettingValue
import my.github.MrxSiN.modeevolved.action.SystemSetting
import my.github.MrxSiN.modeevolved.action.SystemSettingAction
import my.github.MrxSiN.modeevolved.editor.EditorFacts
import my.github.MrxSiN.modeevolved.presentation.SettingTexts
import my.github.MrxSiN.modeevolved.presentation.SystemSettingPresentation

/**
 * Choose a device setting, then what it is while the Mode is on.
 *
 * Two steps on one screen, as Settings' own pickers go: a searchable list grouped the way
 * Settings groups them, then the value with the control that setting uses everywhere else.
 */
object SystemSettingEditor : KindEditor<ModeAction> {

    override val presentation = SystemSettingPresentation

    @Composable
    override fun Content(facts: EditorFacts, initial: ModeAction?, onChange: (ModeAction?) -> Unit) {
        val original = initial as? SystemSettingAction
        var settingId by rememberSaveable { mutableStateOf(original?.setting?.id) }
        var value by rememberSaveable { mutableIntStateOf(original?.value ?: 0) }
        var restore by rememberSaveable { mutableStateOf(original?.restore ?: true) }
        val setting = settingId?.let(SystemSetting::of)
        LaunchedEffect(setting, value, restore) {
            onChange(setting?.takeIf { it.value.isValid(value) }?.let { SystemSettingAction(it, value, restore) })
        }

        val motion = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = setting,
            transitionSpec = {
                val forward = targetState != null
                val slide = motion.defaultSpatialSpec<IntOffset>()
                val fade = motion.defaultEffectsSpec<Float>()
                (slideInHorizontally(slide) { width -> if (forward) width / 4 else -width / 4 } + fadeIn(fade)) togetherWith
                    (slideOutHorizontally(slide) { width -> if (forward) -width / 4 else width / 4 } + fadeOut(fade))
            },
            label = "setting step",
        ) { shown ->
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (shown == null) {
                    SettingPicker(facts) { picked ->
                        settingId = picked.id
                        value = picked.defaultValue
                    }
                } else {
                    // A setting chosen here goes back to the list; one being edited is the action itself.
                    val canChange = original == null
                    BackHandler(enabled = canChange) { settingId = null }
                    ValueEditor(shown, value, { value = it }, restore, { restore = it }, onChangeSetting = if (canChange) ({ settingId = null }) else null)
                }
            }
        }
    }

    @Composable
    private fun SettingPicker(facts: EditorFacts, onPick: (SystemSetting) -> Unit) {
        var query by rememberSaveable { mutableStateOf("") }
        Section(title = null) {
            SearchField(query, { query = it }, stringResource(R.string.settings_search))
        }
        val labels = SystemSetting.entries.associateWith { stringResource(SettingTexts.labelOf(it)) }
        val wanted = query.trim()
        var any = false
        for (group in SettingGroup.entries) {
            val shown = SystemSetting.entries.filter { it.group == group && labels.getValue(it).contains(wanted, ignoreCase = true) }
            if (shown.isEmpty()) continue
            any = true
            Section(stringResource(SettingTexts.groupLabelOf(group))) {
                shown.forEachIndexed { index, setting ->
                    val reason = facts.unavailableSettings[setting.id]
                    SectionRow(
                        title = labels.getValue(setting),
                        supporting = reason?.let {
                            if (it.isEmpty()) stringResource(R.string.setting_unavailable) else stringResource(R.string.setting_unavailable_reason, it)
                        },
                        icon = SettingTexts.iconOf(setting),
                        position = positionAt(index, shown.size),
                        enabled = reason == null,
                        onClick = { onPick(setting) },
                    )
                }
            }
        }
        if (!any) Intro(stringResource(R.string.settings_none_found))
    }

    @Composable
    private fun ValueEditor(
        setting: SystemSetting,
        value: Int,
        onValue: (Int) -> Unit,
        restore: Boolean,
        onRestore: (Boolean) -> Unit,
        onChangeSetting: (() -> Unit)?,
    ) {
        val label = stringResource(SettingTexts.labelOf(setting))
        Section(title = null) {
            SectionRow(
                title = label,
                supporting = onChangeSetting?.let { stringResource(R.string.setting_change) },
                icon = SettingTexts.iconOf(setting),
                position = positionAt(0, 1),
                onClick = { onChangeSetting?.invoke() },
            )
        }
        Section(stringResource(R.string.setting_value_title)) {
            when (setting.value) {
                SettingValue.SWITCH -> ConnectedChoice(listOf(1, 0), value, onValue) {
                    stringResource(if (it == 1) R.string.setting_on else R.string.setting_off)
                }
                SettingValue.RINGER -> ConnectedChoice(
                    listOf(SystemSetting.RINGER_NORMAL, SystemSetting.RINGER_VIBRATE, SystemSetting.RINGER_SILENT),
                    value,
                    onValue,
                ) { stringResource(SettingTexts.ringerLabelOf(it)) }
                SettingValue.PERCENT -> PercentSlider(label, value, onValue)
                SettingValue.TIMEOUT -> {
                    val resources = LocalContext.current.resources
                    SystemSetting.TIMEOUTS.forEachIndexed { index, millis ->
                        SectionRow(
                            title = SettingTexts.timeoutLabel(resources, millis),
                            position = positionAt(index, SystemSetting.TIMEOUTS.size),
                            selected = value == millis,
                            onClick = { onValue(millis) },
                        )
                    }
                }
            }
        }
        Section(title = null) {
            SectionRow(
                title = stringResource(R.string.setting_restore),
                supporting = stringResource(R.string.setting_restore_summary),
                position = positionAt(0, 1),
                onClick = { onRestore(!restore) },
                trailing = { Switch(checked = restore, onCheckedChange = null) },
            )
        }
        SettingTexts.noteOf(setting)?.let { Note(stringResource(it)) }
        Note(stringResource(R.string.setting_note))
    }

    /** A slider and the exact value beside it, read out together as the setting and its percentage. */
    @Composable
    private fun PercentSlider(label: String, value: Int, onValue: (Int) -> Unit) {
        val shown = stringResource(R.string.setting_percent, value)
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceBright) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Slider(
                    value = value.toFloat(),
                    onValueChange = { onValue(it.roundToInt()) },
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f).semantics {
                        contentDescription = label
                        stateDescription = shown
                    },
                )
                // Wide enough for "100%", so the slider does not move as the number grows.
                Text(
                    shown,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(min = 56.dp),
                )
            }
        }
    }
}
