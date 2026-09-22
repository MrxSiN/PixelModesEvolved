package my.github.MrxSiN.modeevolved.editor.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import my.github.MrxSiN.modeevolved.R
import my.github.MrxSiN.modeevolved.action.BroadcastAction
import my.github.MrxSiN.modeevolved.action.IntentActions
import my.github.MrxSiN.modeevolved.action.LaunchAppAction
import my.github.MrxSiN.modeevolved.action.ModeAction
import my.github.MrxSiN.modeevolved.action.TaskerTaskAction
import my.github.MrxSiN.modeevolved.action.Timing
import my.github.MrxSiN.modeevolved.editor.EditorFacts
import my.github.MrxSiN.modeevolved.presentation.BroadcastPresentation
import my.github.MrxSiN.modeevolved.presentation.Cooldowns
import my.github.MrxSiN.modeevolved.presentation.Durations
import my.github.MrxSiN.modeevolved.presentation.LaunchAppPresentation
import my.github.MrxSiN.modeevolved.presentation.TaskerTaskPresentation
import my.github.MrxSiN.modeevolved.presentation.Timings

/** Pick an installed app to open. */
object LaunchAppEditor : KindEditor<ModeAction> {

    override val presentation = LaunchAppPresentation

    private class App(val packageName: String, val label: String, val icon: ImageBitmap?)

    @Composable
    override fun Content(facts: EditorFacts, initial: ModeAction?, onChange: (ModeAction?) -> Unit) {
        val original = initial as? LaunchAppAction
        var timing by rememberSaveable { mutableStateOf(original?.timing ?: Timing.START) }
        var cooldown by rememberSaveable { mutableStateOf(original?.cooldownMillis ?: 0L) }
        var packageName by rememberSaveable { mutableStateOf(original?.packageName) }
        var label by rememberSaveable { mutableStateOf(original?.label.orEmpty()) }
        var query by rememberSaveable { mutableStateOf("") }
        LaunchedEffect(timing, cooldown, packageName, label) {
            onChange(packageName?.let { LaunchAppAction(timing, it, label, cooldown) })
        }

        TimingChoice(timing) { timing = it }

        val context = LocalContext.current
        val apps by produceState<List<App>?>(initialValue = null) {
            value = withContext(Dispatchers.IO) { launchableApps(context.packageManager) }
        }

        Section(stringResource(R.string.action_apps)) {
            SearchField(query, { query = it }, stringResource(R.string.action_apps_search))
        }

        val loaded = apps
        if (loaded == null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { LoadingIndicator() }
            return
        }
        val shown = loaded.filter { it.label.contains(query.trim(), ignoreCase = true) }
        Section(title = null) {
            shown.forEachIndexed { index, app ->
                AppRow(app, positionAt(index, shown.size), selected = app.packageName == packageName) {
                    packageName = app.packageName
                    label = app.label
                }
            }
        }
        CooldownChoice(cooldown) { cooldown = it }
    }

    @Composable
    private fun AppRow(app: App, position: RowPosition, selected: Boolean, onClick: () -> Unit) {
        SectionRow(
            title = app.label,
            supporting = app.packageName,
            position = position,
            selected = selected,
            onClick = onClick,
            leading = app.icon?.let { icon -> { Image(icon, contentDescription = null, modifier = Modifier.size(40.dp)) } },
        )
    }

    /** Apps with a launcher entry, found through the MAIN/LAUNCHER query this app declares. */
    private fun launchableApps(packages: PackageManager): List<App> {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packages.queryIntentActivities(launcher, 0)
            .distinctBy { it.activityInfo.packageName }
            .map { info ->
                App(info.activityInfo.packageName, info.loadLabel(packages).toString(), runCatching { bitmapOf(info.loadIcon(packages)) }.getOrNull())
            }
            .sortedBy { it.label.lowercase() }
    }

    private fun bitmapOf(drawable: android.graphics.drawable.Drawable): ImageBitmap {
        val size = ICON_PIXELS
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bitmap))
        return bitmap.asImageBitmap()
    }

    private const val ICON_PIXELS = 120
}

/**
 * Name an intent action to broadcast, for Tasker's "Intent Received" or any other listener.
 *
 * The broadcast leaves system_server, so receivers see the system process as its
 * sender. Two things follow, and both are enforced here and again before sending:
 * the action name must be one of your own rather than one the platform defines,
 * and it goes to one named package unless the advanced switch widens it.
 */
object BroadcastEditor : KindEditor<ModeAction> {

    override val presentation = BroadcastPresentation

    @Composable
    override fun Content(facts: EditorFacts, initial: ModeAction?, onChange: (ModeAction?) -> Unit) {
        val original = initial as? BroadcastAction
        var timing by rememberSaveable { mutableStateOf(original?.timing ?: Timing.START) }
        var cooldown by rememberSaveable { mutableStateOf(original?.cooldownMillis ?: 0L) }
        var action by rememberSaveable { mutableStateOf(original?.intentAction ?: DEFAULT_ACTION) }
        var packageName by rememberSaveable { mutableStateOf(original?.packageName.orEmpty()) }
        // An action stored without a package was saved with this switch on, so it comes back on.
        var anyApp by rememberSaveable { mutableStateOf(original != null && original.packageName == null) }

        val trimmedAction = action.trim()
        val trimmedPackage = packageName.trim()
        val actionIsUsable = IntentActions.isSendable(trimmedAction)
        LaunchedEffect(timing, cooldown, trimmedAction, trimmedPackage, anyApp, actionIsUsable) {
            val target = trimmedPackage.takeIf { it.isNotEmpty() && !anyApp }
            val complete = actionIsUsable && (anyApp || target != null)
            onChange(if (complete) BroadcastAction(timing, trimmedAction, target, cooldown) else null)
        }

        TimingChoice(timing) { timing = it }

        Section(stringResource(R.string.action_broadcast_intent)) {
            OutlinedTextField(
                value = action,
                onValueChange = { action = it },
                label = { Text(stringResource(R.string.action_broadcast_action)) },
                isError = trimmedAction.isNotEmpty() && !actionIsUsable,
                supportingText = if (trimmedAction.isEmpty() || actionIsUsable) null else {
                    { Text(stringResource(R.string.action_broadcast_invalid)) }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            if (!anyApp) {
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text(stringResource(R.string.action_broadcast_package)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
        Section(stringResource(R.string.action_broadcast_advanced)) {
            SwitchRow(
                title = stringResource(R.string.action_broadcast_any_app),
                supporting = stringResource(R.string.action_broadcast_any_app_warning),
                checked = anyApp,
                onCheckedChange = { anyApp = it },
            )
        }
        CooldownChoice(cooldown) { cooldown = it }
        Note(stringResource(R.string.action_broadcast_note))
    }

    private const val DEFAULT_ACTION = "my.github.MrxSiN.modeevolved.MODE_CHANGED"
}

/** Pick a Tasker task by name. */
object TaskerTaskEditor : KindEditor<ModeAction> {

    override val presentation = TaskerTaskPresentation

    @Composable
    override fun Content(facts: EditorFacts, initial: ModeAction?, onChange: (ModeAction?) -> Unit) {
        val original = initial as? TaskerTaskAction
        var timing by rememberSaveable { mutableStateOf(original?.timing ?: Timing.START) }
        var cooldown by rememberSaveable { mutableStateOf(original?.cooldownMillis ?: 0L) }
        var task by rememberSaveable { mutableStateOf(original?.taskName.orEmpty()) }
        LaunchedEffect(timing, cooldown, task) {
            onChange(task.trim().takeIf { it.isNotEmpty() }?.let { TaskerTaskAction(timing, it, cooldown) })
        }

        TimingChoice(timing) { timing = it }

        if (facts.taskerTasks.isNotEmpty()) {
            Section(stringResource(R.string.action_tasker_tasks)) {
                facts.taskerTasks.forEachIndexed { index, name ->
                    SectionRow(
                        title = name,
                        icon = R.drawable.ic_action_tasker,
                        position = positionAt(index, facts.taskerTasks.size),
                        selected = task == name,
                        onClick = { task = name },
                    )
                }
            }
        }
        Section(stringResource(R.string.action_tasker_name_section)) {
            OutlinedTextField(
                value = task,
                onValueChange = { task = it },
                label = { Text(stringResource(R.string.action_tasker_name)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        CooldownChoice(cooldown) { cooldown = it }
        Note(stringResource(R.string.action_tasker_note))
    }
}

/**
 * When the action runs, as a Material 3 Expressive connected button group:
 * the same control the Mode page uses for AND and OR.
 */
@Composable
fun TimingChoice(timing: Timing, onChoose: (Timing) -> Unit) {
    ConnectedChoice(Timing.entries, timing, onChoose) { stringResource(Timings.labelOf(it)) }
}

/**
 * The least time between two runs of this action, shared by every kind of action.
 *
 * A Mode changes for reasons this module never sees — a schedule boundary,
 * Quick Settings, another app — and without a cooldown each of those opens the
 * app or runs the task again.
 */
@Composable
fun CooldownChoice(cooldownMillis: Long, onChoose: (Long) -> Unit) {
    Section(stringResource(R.string.action_cooldown)) {
        Cooldowns.offered.forEachIndexed { index, millis ->
            SectionRow(
                title = cooldownLabel(millis),
                position = positionAt(index, Cooldowns.offered.size),
                selected = cooldownMillis == millis,
                onClick = { onChoose(millis) },
            )
        }
    }
    Note(stringResource(R.string.action_cooldown_note))
}

@Composable
private fun cooldownLabel(millis: Long): String {
    if (millis <= 0L) return stringResource(R.string.cooldown_none)
    val label = Durations.label(millis)
    val wording = if (label.seconds) R.plurals.cooldown_seconds else R.plurals.cooldown_minutes
    return pluralStringResource(wording, label.amount, label.amount)
}

/** A row with a switch on its trailing edge, drawn like the other rows in a section. */
@Composable
private fun SectionScope.SwitchRow(title: String, supporting: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SectionRow(
        title = title,
        supporting = supporting,
        position = positionAt(0, 1),
        onClick = { onCheckedChange(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = null) },
    )
}
