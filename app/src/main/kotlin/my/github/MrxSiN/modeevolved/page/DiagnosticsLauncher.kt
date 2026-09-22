package my.github.MrxSiN.modeevolved.page

import android.content.Context
import android.content.Intent
import android.os.Build

import my.github.MrxSiN.modeevolved.R
import my.github.MrxSiN.modeevolved.diagnostics.DiagnosticsIntents
import my.github.MrxSiN.modeevolved.diagnostics.DiagnosticsReport
import my.github.MrxSiN.modeevolved.diagnostics.DiagnosticsReportCodec
import my.github.MrxSiN.modeevolved.store.SecureDiagnosticsStore

/** What the Modes list row says, and whether it says it with a tick or a warning. */
data class DiagnosticsSummary(val line: String, val ok: Boolean)

/**
 * Gathers the compatibility report and opens the page that shows it.
 *
 * Everything on that page is visible from the Settings app and from nowhere
 * else: this process's own hooks, system_server's half through `Settings.Secure`,
 * and the Android build underneath. The page runs in this module's own app, so
 * the whole report is handed over in the intent rather than looked up again.
 *
 * @param settings what this process found, written afresh before each read so members
 * looked up after the hooks went in are counted too.
 */
class DiagnosticsLauncher(
    private val settings: SecureDiagnosticsStore,
    private val moduleVersion: String,
    private val frameworkName: String,
    private val frameworkVersion: String,
    private val apiVersion: Int,
) {

    fun open(context: Context) {
        val activity = context.activity() ?: return
        val intent = Intent()
            .setClassName(DiagnosticsIntents.PACKAGE, DiagnosticsIntents.ACTIVITY)
            .putExtra(DiagnosticsIntents.EXTRA_REPORT, DiagnosticsReportCodec.encode(report(context)))
        activity.startActivity(intent)
    }

    /** The one line the row shows: everything passed, or how much did not. */
    fun summary(context: Context, text: ModuleText): DiagnosticsSummary {
        val report = report(context)
        val failed = report.featuresFailed + report.failed
        return when {
            report.contracts.isEmpty() -> DiagnosticsSummary(text.string(R.string.diagnostics_pending), ok = false)
            failed == 0 -> DiagnosticsSummary(
                text.raw.getString(R.string.diagnostics_all_ok, report.features.size, report.contracts.size),
                ok = true,
            )
            else -> DiagnosticsSummary(text.raw.getString(R.string.diagnostics_some_failed, failed), ok = false)
        }
    }

    private fun report(context: Context): DiagnosticsReport {
        settings.write()
        val found = settings.snapshot() + SecureDiagnosticsStore.read(context.contentResolver, SecureDiagnosticsStore.SYSTEM_KEY)
        return DiagnosticsReport(
            moduleVersion = moduleVersion,
            moduleActive = found.contracts.isNotEmpty(),
            androidRelease = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            buildId = Build.ID,
            settingsVersion = settingsVersion(context),
            frameworkName = frameworkName,
            frameworkVersion = frameworkVersion,
            apiVersion = apiVersion,
            features = found.features,
            contracts = found.contracts,
        )
    }

    /** The version of the Settings build these hooks were measured against. */
    private fun settingsVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(DiagnosticsIntents.SETTINGS_PACKAGE, 0).longVersionCode.toString()
    }.getOrDefault("")
}
