package my.github.MrxSiN.modeevolved.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

import my.github.MrxSiN.modeevolved.R
import my.github.MrxSiN.modeevolved.core.Resolution
import my.github.MrxSiN.modeevolved.editor.ui.EditorTheme
import my.github.MrxSiN.modeevolved.security.SettingsCaller

/**
 * Shows what this module found when it met this Android build.
 *
 * Answers only the Settings app, which is the one process that can see both
 * halves of the report and hands the whole thing over in the intent. Opened any
 * other way, it closes ([SettingsCaller]).
 */
class DiagnosticsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (!SettingsCaller.isSettings(callingPackage, launchedFromPackage)) return finish()
        val report = intent.getStringExtra(DiagnosticsIntents.EXTRA_REPORT)
            ?.let(DiagnosticsReportCodec::decode)
            ?: return finish()

        setContent {
            EditorTheme {
                DiagnosticsScreen(report, onBack = ::finish, onCopy = { copy(report) })
            }
        }
    }

    private fun copy(report: DiagnosticsReport) {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.diagnostics_title), asText(report)))
    }

    /** The report as the plain text a bug report can hold. */
    private fun asText(report: DiagnosticsReport): String = buildString {
        appendLine("${getString(R.string.app_name)} ${report.moduleVersion}")
        appendLine("Android ${report.androidRelease} (SDK ${report.sdkInt}), build ${report.buildId}")
        if (report.settingsVersion.isNotEmpty()) appendLine("Settings ${report.settingsVersion}")
        appendLine("${report.frameworkName} ${report.frameworkVersion}, libxposed API ${report.apiVersion}")
        appendLine()
        appendLine("Features")
        for (health in report.features) {
            append(if (health.ok) "  OK   " else "  FAIL ")
            append(health.feature.id)
            health.detail?.let { append(" - ").append(it) }
            appendLine()
        }
        appendLine()
        // Causes and signal kinds only: no names, networks, devices or places (ROADMAP I16).
        appendLine("Mode decisions")
        for ((modeId, decision) in report.decisions.entries.sortedByDescending { it.value.atMillis }) {
            append("  ").append(shortId(modeId)).append(if (decision.active) " on  " else " off ")
            append(decision.cause.name.lowercase())
            if (decision.signals.isNotEmpty()) append(" (").append(decision.signals.joinToString(",") { it.name.lowercase() }).append(')')
            appendLine()
        }
        appendLine()
        appendLine(
            "Hooks: ${report.resolved}/${report.contracts.size} resolved, ${report.bySignature} by signature, " +
                "${report.fallbacks} by fallback, ${report.failed} failed",
        )
        for (contract in report.contracts) {
            append(
                when (contract.resolution) {
                    Resolution.BY_NAME -> "  OK   "
                    Resolution.BY_SIGNATURE -> "  MOVED"
                    Resolution.FALLBACK -> "  ALT  "
                    Resolution.MISSING -> if (report.isOptional(contract)) "  OPT  " else "  FAIL "
                },
            )
            appendLine(contract.name)
        }
    }
}
