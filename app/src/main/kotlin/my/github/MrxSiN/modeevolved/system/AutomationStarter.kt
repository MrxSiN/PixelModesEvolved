package my.github.MrxSiN.modeevolved.system

import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock

import my.github.MrxSiN.modeevolved.action.ActionAutomation
import my.github.MrxSiN.modeevolved.action.ActionCodec
import my.github.MrxSiN.modeevolved.core.Feature
import my.github.MrxSiN.modeevolved.core.FeatureInstaller
import my.github.MrxSiN.modeevolved.core.Logger
import my.github.MrxSiN.modeevolved.dex.ClassFinder
import my.github.MrxSiN.modeevolved.engine.ModeAutomation
import my.github.MrxSiN.modeevolved.engine.SignalHub
import my.github.MrxSiN.modeevolved.engine.TriggerEngine
import my.github.MrxSiN.modeevolved.rule.RuleCodec
import my.github.MrxSiN.modeevolved.store.SecureSettingsStore
import my.github.MrxSiN.modeevolved.signal.AirplaneSource
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
    private val classes: ClassFinder,
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
        val modes = NotificationModeController(context.getSystemService(NotificationManager::class.java), logger)
        val engine = TriggerEngine(modes, delays, logger)
        val sources = listOf(
            WifiSource(context, handler),
            BluetoothSource(context, handler, logger),
            LocationSource(context, handler, logger),
            MovementSource(AndroidMotionSensor(context, handler), delays, logger),
            FlightSource(context, handler, logger),
            AirplaneSource(context, handler),
        )
        val hub = SignalHub(sources, engine::onStateChanged, logger)
        return ModeAutomation(rules, engine, hub, handler::post)
    }

    private fun actions(context: Context, handler: Handler): ActionAutomation {
        val notifications = context.getSystemService(NotificationManager::class.java)
        return ActionAutomation(
            actions = SecureSettingsStore(context.contentResolver, SecureSettingsStore.ACTIONS_KEY, ActionCodec.standard()),
            states = ZenModeStates(context, handler, notifications),
            runner = IntentActionRunner(context, notifications, classes, logger),
            thread = handler::post,
            // Monotonic, so a cooldown is not cut short or stretched by the wall clock moving.
            now = SystemClock::elapsedRealtime,
            logger = logger,
        )
    }

    private companion object {
        const val THREAD_NAME = "PixelModesEvolved"
    }
}
