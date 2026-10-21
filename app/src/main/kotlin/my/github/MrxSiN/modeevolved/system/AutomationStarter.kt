package my.github.MrxSiN.modeevolved.system

import android.app.NotificationManager
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.provider.Settings

import my.github.MrxSiN.modeevolved.action.ActionAutomation
import my.github.MrxSiN.modeevolved.action.ActionCodec
import my.github.MrxSiN.modeevolved.action.SettingClaims
import my.github.MrxSiN.modeevolved.action.SystemSetting
import my.github.MrxSiN.modeevolved.compat.SystemServerApis as SS
import my.github.MrxSiN.modeevolved.core.Feature
import my.github.MrxSiN.modeevolved.core.FeatureInstaller
import my.github.MrxSiN.modeevolved.core.Logger
import my.github.MrxSiN.modeevolved.dex.HostResolver
import my.github.MrxSiN.modeevolved.engine.ModeAutomation
import my.github.MrxSiN.modeevolved.engine.SignalHub
import my.github.MrxSiN.modeevolved.engine.DecisionCodec
import my.github.MrxSiN.modeevolved.engine.TriggerEngine
import my.github.MrxSiN.modeevolved.rule.RuleCodec
import my.github.MrxSiN.modeevolved.store.SecureSettingsStore
import my.github.MrxSiN.modeevolved.signal.AirplaneSource
import my.github.MrxSiN.modeevolved.signal.PowerSource
import my.github.MrxSiN.modeevolved.signal.AndroidMotionSensor
import my.github.MrxSiN.modeevolved.signal.BluetoothSource
import my.github.MrxSiN.modeevolved.signal.FlightSource
import my.github.MrxSiN.modeevolved.signal.LocationSource
import my.github.MrxSiN.modeevolved.signal.MovementSource
import my.github.MrxSiN.modeevolved.signal.WifiSource

/**
 * Composition root for system_server: builds the automation and starts it.
 *
 * Everything runs on one dedicated thread, so no part of it needs locking and
 * none of it runs on a thread system_server relies on.
 */
class AutomationStarter(
    private val logger: Logger,
    private val apis: HostResolver,
    private val features: FeatureInstaller,
) {

    /** Starts the automation. Closing the returned handle stops all of it and ends its thread. */
    fun start(context: Context): AutoCloseable {
        val thread = HandlerThread(THREAD_NAME).apply { start() }
        val handler = Handler(thread.looper)
        // Touched only on the automation thread.
        val running = mutableListOf<AutoCloseable>()

        handler.post {
            startGuarded(Feature.TRIGGER_AUTOMATION) { triggers(context, handler).also { it.start() }.let { AutoCloseable(it::stop) } }?.let(running::add)
            startGuarded(Feature.ACTION_AUTOMATION) { actions(context, handler).also { it.start() }.let { AutoCloseable(it::stop) } }?.let(running::add)
        }
        return AutoCloseable {
            handler.post {
                running.forEach { part -> runCatching(part::close).onFailure { logger.warn("Automation did not stop cleanly", it) } }
                logger.info("Automation stopped")
            }
            // Runs what is already queued, the stop above included, then ends the thread.
            thread.quitSafely()
        }
    }

    /** Starts one part, recording the outcome for the compatibility check, and keeps its handle when it started. */
    private fun startGuarded(feature: Feature, start: () -> AutoCloseable): AutoCloseable? {
        var started: AutoCloseable? = null
        features.install(feature) { started = start() }
        return started
    }

    private fun triggers(context: Context, handler: Handler): ModeAutomation {
        val delays = HandlerDelays(handler)
        val rules = SecureSettingsStore(context.contentResolver, SecureSettingsStore.RULES_KEY, RuleCodec.standard())
        val resolver = context.contentResolver
        val modes = NotificationModeController(
            context.getSystemService(NotificationManager::class.java),
            logger,
            Settings.Secure.getString(resolver, OWNED_KEY),
        ) { Settings.Secure.putString(resolver, OWNED_KEY, it.ifEmpty { null }) }
        // Event-driven and bounded: one entry per Mode, written only when the engine decides.
        val decisions = LinkedHashMap(DecisionCodec.decode(Settings.Secure.getString(resolver, DecisionCodec.KEY)))
        val engine = TriggerEngine(modes, delays, logger, explain = { modeId, decision ->
            decisions[modeId] = decision
            Settings.Secure.putString(resolver, DecisionCodec.KEY, DecisionCodec.encode(decisions))
        })
        val sources = listOf(
            WifiSource(context, handler),
            BluetoothSource(context, handler, logger, bluetoothConnected()),
            LocationSource(context, handler, logger),
            MovementSource(AndroidMotionSensor(context, handler), delays, logger),
            FlightSource(context, handler, logger),
            AirplaneSource(context, handler),
            PowerSource(context, handler),
        )
        val hub = SignalHub(sources, engine::onStateChanged, logger)
        return ModeAutomation(rules, engine, hub, handler::post)
    }

    private fun actions(context: Context, handler: Handler): ActionAutomation {
        val notifications = context.getSystemService(NotificationManager::class.java)
        return ActionAutomation(
            settings = settingClaims(context),
            actions = SecureSettingsStore(context.contentResolver, SecureSettingsStore.ACTIONS_KEY, ActionCodec.standard()),
            states = ZenModeStates(context, handler, notifications),
            runner = IntentActionRunner(context, notifications, apis, logger),
            thread = handler::post,
            // Monotonic, so a cooldown is not cut short or stretched by the wall clock moving.
            now = SystemClock::elapsedRealtime,
            logger = logger,
        )
    }

    /**
     * Holds the system settings Modes claim, with ownership kept in `Settings.Secure` so it
     * outlives a reboot or an update. Each setting is checked once here and reported on the
     * compatibility page; one this build cannot change fails on its own when used.
     */
    private fun settingClaims(context: Context): SettingClaims {
        val backend = SystemSettingsBackend(context, apis, logger)
        for (setting in SystemSetting.entries) features.install(setting.feature) { backend.probe(setting) }
        val resolver = context.contentResolver
        return SettingClaims(backend, logger) { Settings.Secure.putString(resolver, CLAIMS_KEY, it.ifEmpty { null }) }
            .apply { load(Settings.Secure.getString(resolver, CLAIMS_KEY)) }
    }

    /** Whether a bonded device is connected, when this build lets system_server ask. */
    private fun bluetoothConnected(): ((BluetoothDevice) -> Boolean)? {
        if (!apis.has(SS.BLUETOOTH_DEVICE_IS_CONNECTED)) return null
        val isConnected = apis.method(SS.BLUETOOTH_DEVICE_IS_CONNECTED)
        return { device -> isConnected.invoke(device) as Boolean }
    }

    private companion object {
        const val THREAD_NAME = "PixelModesEvolved"

        /** Who holds which system setting, from what baseline: `adb shell settings get secure mode_evolved_setting_claims`. */
        const val CLAIMS_KEY = "mode_evolved_setting_claims"

        /** The Modes this module switched on and has not switched off: `adb shell settings get secure mode_evolved_owned`. */
        const val OWNED_KEY = "mode_evolved_owned"
    }
}
