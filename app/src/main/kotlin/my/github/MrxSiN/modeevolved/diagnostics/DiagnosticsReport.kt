package my.github.MrxSiN.modeevolved.diagnostics

import org.json.JSONObject

import my.github.MrxSiN.modeevolved.core.Contract
import my.github.MrxSiN.modeevolved.core.FeatureHealth
import my.github.MrxSiN.modeevolved.core.Resolution
import my.github.MrxSiN.modeevolved.store.Diagnostics
import my.github.MrxSiN.modeevolved.store.DiagnosticsCodec

/**
 * Everything the compatibility page shows, as one value.
 *
 * Gathered in the Settings app, which can see all of it: its own hooks in
 * memory, system_server's half in `Settings.Secure`, and the build it is
 * running on. The page itself runs in this module's own app, which can see
 * none of that, so the whole report travels there in the intent.
 *
 * @property moduleActive whether hook code is running at all, which is what having any result means.
 */
data class DiagnosticsReport(
    val moduleVersion: String = "",
    val moduleActive: Boolean = false,
    val androidRelease: String = "",
    val sdkInt: Int = 0,
    val buildId: String = "",
    val settingsVersion: String = "",
    val frameworkName: String = "",
    val frameworkVersion: String = "",
    val apiVersion: Int = 0,
    val features: List<FeatureHealth> = emptyList(),
    val contracts: List<Contract> = emptyList(),
) {

    val resolved: Int get() = contracts.count { it.resolution != Resolution.MISSING }

    val bySignature: Int get() = contracts.count { it.resolution == Resolution.BY_SIGNATURE }

    val failed: Int get() = contracts.count { it.resolution == Resolution.MISSING }

    val featuresFailed: Int get() = features.count { !it.ok }

    /**
     * Whether the framework granted the root privileges this module needs.
     *
     * The module only reaches Settings at all because a root framework injected
     * it, so a report that exists at all was made with them.
     */
    val rooted: Boolean get() = moduleActive
}

/** The report as text, since it crosses from Settings into this module's own app. */
object DiagnosticsReportCodec {

    fun encode(report: DiagnosticsReport): String = JSONObject()
        .put(MODULE_VERSION, report.moduleVersion)
        .put(MODULE_ACTIVE, report.moduleActive)
        .put(ANDROID_RELEASE, report.androidRelease)
        .put(SDK, report.sdkInt)
        .put(BUILD_ID, report.buildId)
        .put(SETTINGS_VERSION, report.settingsVersion)
        .put(FRAMEWORK_NAME, report.frameworkName)
        .put(FRAMEWORK_VERSION, report.frameworkVersion)
        .put(API, report.apiVersion)
        .put(FOUND, JSONObject(DiagnosticsCodec.encode(Diagnostics(report.features, report.contracts))))
        .toString()

    /** The report in [text], or null when it cannot be read. */
    fun decode(text: String): DiagnosticsReport? = runCatching {
        val json = JSONObject(text)
        val found = DiagnosticsCodec.decode(json.optJSONObject(FOUND)?.toString())
        DiagnosticsReport(
            moduleVersion = json.optString(MODULE_VERSION),
            moduleActive = json.optBoolean(MODULE_ACTIVE),
            androidRelease = json.optString(ANDROID_RELEASE),
            sdkInt = json.optInt(SDK),
            buildId = json.optString(BUILD_ID),
            settingsVersion = json.optString(SETTINGS_VERSION),
            frameworkName = json.optString(FRAMEWORK_NAME),
            frameworkVersion = json.optString(FRAMEWORK_VERSION),
            apiVersion = json.optInt(API),
            features = found.features,
            contracts = found.contracts,
        )
    }.getOrNull()

    private const val MODULE_VERSION = "moduleVersion"
    private const val MODULE_ACTIVE = "moduleActive"
    private const val ANDROID_RELEASE = "androidRelease"
    private const val SDK = "sdk"
    private const val BUILD_ID = "buildId"
    private const val SETTINGS_VERSION = "settingsVersion"
    private const val FRAMEWORK_NAME = "frameworkName"
    private const val FRAMEWORK_VERSION = "frameworkVersion"
    private const val API = "api"
    private const val FOUND = "found"
}

/** Names both processes use to reach the compatibility page. */
object DiagnosticsIntents {
    const val PACKAGE = "my.github.MrxSiN.modeevolved"
    const val ACTIVITY = "$PACKAGE.diagnostics.DiagnosticsActivity"
    const val EXTRA_REPORT = "$PACKAGE.extra.REPORT"

    /** The only caller the page answers. */
    const val SETTINGS_PACKAGE = "com.android.settings"
}
