package my.github.MrxSiN.modeevolved.diagnostics

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

import my.github.MrxSiN.modeevolved.R
import my.github.MrxSiN.modeevolved.core.Contract
import my.github.MrxSiN.modeevolved.core.FeatureHealth
import my.github.MrxSiN.modeevolved.core.Resolution
import my.github.MrxSiN.modeevolved.engine.Decision
import my.github.MrxSiN.modeevolved.editor.ui.EditorScaffold
import my.github.MrxSiN.modeevolved.editor.ui.RowPosition
import my.github.MrxSiN.modeevolved.editor.ui.Section
import my.github.MrxSiN.modeevolved.editor.ui.SectionRow
import my.github.MrxSiN.modeevolved.editor.ui.SectionScope
import my.github.MrxSiN.modeevolved.presentation.FeatureLabels

/**
 * What this module found when it met this Android build.
 *
 * Every hook it installs reaches into a private part of Settings or
 * system_server, and a quarterly release moves those. This page answers the two
 * questions that follow: which features still work, and exactly which member
 * went when one does not.
 *
 * @param onCopy puts the whole report on the clipboard, for a bug report.
 */
@Composable
fun DiagnosticsScreen(report: DiagnosticsReport, onBack: () -> Unit, onCopy: () -> Unit) {
    EditorScaffold(title = stringResource(R.string.diagnostics_title), subtitle = null, onBack = onBack) {
        Section(stringResource(R.string.app_name)) {
            val module = listOf(
                stringResource(R.string.diagnostics_module) to
                    stringResource(if (report.moduleActive) R.string.diagnostics_active else R.string.diagnostics_inactive),
                stringResource(R.string.diagnostics_version) to report.moduleVersion,
            )
            module.forEachIndexed { index, (label, value) -> ValueRow(label, value, positionOf(index, module.size)) }
        }

        Section(stringResource(R.string.diagnostics_system)) {
            val system = listOfNotNull(
                stringResource(R.string.diagnostics_android) to
                    stringResource(R.string.diagnostics_android_value, report.androidRelease, report.sdkInt),
                stringResource(R.string.diagnostics_build) to report.buildId,
                (stringResource(R.string.diagnostics_settings) to report.settingsVersion)
                    .takeIf { report.settingsVersion.isNotEmpty() },
                stringResource(R.string.diagnostics_api) to report.apiVersion.toString(),
                (stringResource(R.string.diagnostics_framework) to "${report.frameworkName} ${report.frameworkVersion}".trim())
                    .takeIf { report.frameworkName.isNotEmpty() },
                stringResource(R.string.diagnostics_root) to
                    stringResource(if (report.rooted) R.string.diagnostics_granted else R.string.diagnostics_denied),
            )
            system.forEachIndexed { index, (label, value) -> ValueRow(label, value, positionOf(index, system.size)) }
        }

        Section(stringResource(R.string.diagnostics_features)) {
            if (report.features.isEmpty()) {
                ValueRow(stringResource(R.string.diagnostics_pending), "", positionOf(0, 1))
            }
            report.features.forEachIndexed { index, health ->
                FeatureRow(health, positionOf(index, report.features.size))
            }
        }

        Section(stringResource(R.string.diagnostics_hooks)) {
            val counts = listOf(
                stringResource(R.string.diagnostics_resolved) to "${report.resolved} / ${report.contracts.size}",
                stringResource(R.string.diagnostics_by_signature) to report.bySignature.toString(),
                stringResource(R.string.diagnostics_fallbacks) to report.fallbacks.toString(),
                stringResource(R.string.diagnostics_failed) to report.failed.toString(),
            )
            counts.forEachIndexed { index, (label, value) -> ValueRow(label, value, positionOf(index, counts.size)) }
        }

        if (report.contracts.isNotEmpty()) {
            Section(stringResource(R.string.diagnostics_contracts)) {
                report.contracts.forEachIndexed { index, contract ->
                    ContractRow(contract, report.isOptional(contract), positionOf(index, report.contracts.size))
                }
            }
        }

        Section(stringResource(R.string.diagnostics_decisions)) {
            if (report.decisions.isEmpty()) {
                ValueRow(stringResource(R.string.diagnostics_decisions_none), "", positionOf(0, 1))
            }
            val entries = report.decisions.entries.sortedByDescending { it.value.atMillis }
            entries.forEachIndexed { index, (modeId, decision) ->
                DecisionRow(modeId, decision, positionOf(index, entries.size))
            }
        }

        Section(stringResource(R.string.diagnostics_report)) {
            SectionRow(
                title = stringResource(R.string.diagnostics_copy),
                supporting = stringResource(R.string.diagnostics_copy_hint),
                position = positionOf(0, 1),
                onClick = onCopy,
            )
        }
    }
}

private fun positionOf(index: Int, count: Int) = RowPosition(isFirst = index == 0, isLast = index == count - 1)

/** A label on the left and its value on the right, the way a Settings info row reads. */
@Composable
private fun SectionScope.ValueRow(label: String, value: String, position: RowPosition) {
    InfoRow(position) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = 200.dp),
            )
        }
    }
}

/** One feature, with what stopped it underneath when something did. */
@Composable
private fun SectionScope.FeatureRow(health: FeatureHealth, position: RowPosition) {
    InfoRow(position) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(FeatureLabels.of(health.feature)), style = MaterialTheme.typography.titleMedium)
            Text(
                health.detail ?: stringResource(R.string.diagnostics_feature_ok),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Verdict(
            ok = health.ok,
            label = stringResource(if (health.ok) R.string.diagnostics_compatible else R.string.diagnostics_unavailable),
        )
    }
}

/** Why one Mode last changed, or is about to: on or off, the cause, and the kinds of signal read. */
@Composable
private fun SectionScope.DecisionRow(modeId: String, decision: Decision, position: RowPosition) {
    InfoRow(position) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(if (decision.active) R.string.diagnostics_decision_on else R.string.diagnostics_decision_off, shortId(modeId)),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                decisionText(decision) + " \u00b7 " + DateUtils.getRelativeTimeSpanString(decision.atMillis),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun decisionText(decision: Decision): String {
    val signals = decision.signals.joinToString(", ") { it.name.lowercase().replaceFirstChar(Char::uppercase) }
    return when (decision.cause) {
        Decision.Cause.SIGNALS -> stringResource(R.string.diagnostics_cause_signals, signals)
        Decision.Cause.WAITING -> stringResource(R.string.diagnostics_cause_waiting, signals)
        Decision.Cause.SAVED -> stringResource(R.string.diagnostics_cause_saved)
        Decision.Cause.LOADED -> stringResource(R.string.diagnostics_cause_loaded)
        Decision.Cause.REMOVED -> stringResource(R.string.diagnostics_cause_removed)
    }
}

/** A Mode id is a long random string; its start is enough to tell Modes apart, and names nothing. */
fun shortId(modeId: String): String = modeId.take(8)

/** One member this module depends on, and what became of it. A missing optional one costs no feature. */
@Composable
private fun SectionScope.ContractRow(contract: Contract, optional: Boolean, position: RowPosition) {
    InfoRow(position) {
        Text(contract.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        val missing = contract.resolution == Resolution.MISSING
        Verdict(
            ok = !missing || optional,
            label = when {
                contract.resolution == Resolution.BY_SIGNATURE -> stringResource(R.string.diagnostics_renamed)
                contract.resolution == Resolution.FALLBACK -> stringResource(R.string.diagnostics_fallback)
                missing && optional -> stringResource(R.string.diagnostics_optional)
                else -> null
            },
        )
    }
}

/** A tick or a cross, with an optional word beside it. */
@Composable
private fun Verdict(ok: Boolean, label: String?) {
    val colors = MaterialTheme.colorScheme
    val tint = if (ok) colors.onSurfaceVariant else colors.error
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(if (ok) R.drawable.ic_check else R.drawable.ic_close),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = tint, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

/** A row that only reports something: a [SectionRow]'s shape without its tap target. */
@Composable
private fun SectionScope.InfoRow(position: RowPosition, content: @Composable RowScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(
            topStart = if (position.isFirst) OUTER else INNER,
            topEnd = if (position.isFirst) OUTER else INNER,
            bottomStart = if (position.isLast) OUTER else INNER,
            bottomEnd = if (position.isLast) OUTER else INNER,
        ),
        color = MaterialTheme.colorScheme.surfaceBright,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

private val OUTER = 20.dp
private val INNER = 4.dp
