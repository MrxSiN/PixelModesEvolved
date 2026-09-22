package my.github.MrxSiN.modeevolved.store

import android.content.ContentResolver
import android.provider.Settings

import org.json.JSONArray
import org.json.JSONObject

import my.github.MrxSiN.modeevolved.core.Contract
import my.github.MrxSiN.modeevolved.core.ContractLog
import my.github.MrxSiN.modeevolved.core.Feature
import my.github.MrxSiN.modeevolved.core.FeatureHealth
import my.github.MrxSiN.modeevolved.core.HealthRecorder
import my.github.MrxSiN.modeevolved.core.Resolution

/**
 * What one process found when it installed itself.
 *
 * Settings and system_server hook different things and answer separately, so
 * each keeps its own and the compatibility page adds the two together.
 *
 * @property framework the Xposed framework's name and version, which only hook code can read.
 */
data class Diagnostics(
    val features: List<FeatureHealth> = emptyList(),
    val contracts: List<Contract> = emptyList(),
    val framework: String = "",
    val apiVersion: Int = 0,
) {

    operator fun plus(other: Diagnostics): Diagnostics = Diagnostics(
        features = (features + other.features).sortedBy { it.feature.ordinal },
        contracts = contracts + other.contracts,
        framework = framework.ifEmpty { other.framework },
        apiVersion = maxOf(apiVersion, other.apiVersion),
    )
}

/** [Diagnostics] as text, so it can be read and written without Android. */
object DiagnosticsCodec {

    fun encode(diagnostics: Diagnostics): String {
        val features = JSONArray()
        for (health in diagnostics.features) {
            features.put(JSONObject().put(FEATURE, health.feature.id).put(OK, health.ok).putOpt(DETAIL, health.detail))
        }
        val contracts = JSONArray()
        for (contract in diagnostics.contracts) {
            contracts.put(JSONObject().put(NAME, contract.name).put(RESOLUTION, contract.resolution.name))
        }
        return JSONObject()
            .put(FEATURES, features)
            .put(CONTRACTS, contracts)
            .put(FRAMEWORK, diagnostics.framework)
            .put(API, diagnostics.apiVersion)
            .toString()
    }

    /** What [text] holds. Anything another build knows and this one does not is dropped. */
    fun decode(text: String?): Diagnostics {
        if (text.isNullOrBlank()) return Diagnostics()
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return Diagnostics()
        return Diagnostics(
            features = json.optJSONArray(FEATURES).objects().mapNotNull { entry ->
                val feature = Feature.of(entry.optString(FEATURE)) ?: return@mapNotNull null
                FeatureHealth(feature, entry.optBoolean(OK), entry.optString(DETAIL).ifEmpty { null })
            },
            contracts = json.optJSONArray(CONTRACTS).objects().mapNotNull { entry ->
                val resolution = Resolution.of(entry.optString(RESOLUTION)) ?: return@mapNotNull null
                Contract(entry.optString(NAME), resolution)
            },
            framework = json.optString(FRAMEWORK),
            apiVersion = json.optInt(API),
        )
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull(::optJSONObject)

    private const val FEATURES = "features"
    private const val CONTRACTS = "contracts"
    private const val FRAMEWORK = "framework"
    private const val API = "api"
    private const val FEATURE = "feature"
    private const val OK = "ok"
    private const val DETAIL = "detail"
    private const val NAME = "name"
    private const val RESOLUTION = "resolution"
}

/**
 * What this process managed to hook, where the other one can read it.
 *
 * Both processes run as the system uid, so a secure setting is a place they
 * already share. Each writes its own entry, so neither waits for the other and
 * neither overwrites the other's answer. It is also what `adb shell settings
 * get secure mode_evolved_diagnostics_*` prints, for a bug report.
 *
 * A write that fails is swallowed: a self-test that cannot be stored must not
 * be the thing that stops a feature from installing.
 */
class SecureDiagnosticsStore(
    private val resolver: ContentResolver,
    private val key: String,
    private val contracts: ContractLog,
    private val framework: String = "",
    private val apiVersion: Int = 0,
) : HealthRecorder {

    private val features = linkedMapOf<Feature, FeatureHealth>()

    override fun record(health: FeatureHealth) {
        features[health.feature] = health
        write()
    }

    /** What this process has found so far. */
    fun snapshot(): Diagnostics = Diagnostics(features.values.toList(), contracts.contracts, framework, apiVersion)

    /**
     * Stores the snapshot.
     *
     * Called after every feature, and again by anything that looks up members later
     * than the hooks did, so those reach the stored copy too.
     */
    fun write() {
        runCatching { Settings.Secure.putString(resolver, key, DiagnosticsCodec.encode(snapshot())) }
    }

    companion object {
        /** Written by the Settings app. */
        const val SETTINGS_KEY = "mode_evolved_diagnostics_settings"

        /** Written by system_server. */
        const val SYSTEM_KEY = "mode_evolved_diagnostics_system"

        /** What both parts last recorded, added together. */
        fun readAll(resolver: ContentResolver): Diagnostics =
            read(resolver, SETTINGS_KEY) + read(resolver, SYSTEM_KEY)

        fun read(resolver: ContentResolver, key: String): Diagnostics =
            DiagnosticsCodec.decode(runCatching { Settings.Secure.getString(resolver, key) }.getOrNull())
    }
}
