package my.github.MrxSiN.modeevolved.system

import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.HandlerThread

import my.github.MrxSiN.modeevolved.action.ActionAutomation
import my.github.MrxSiN.modeevolved.action.ActionCodec
import my.github.MrxSiN.modeevolved.core.Logger
import my.github.MrxSiN.modeevolved.engine.ModeAutomation
import my.github.MrxSiN.modeevolved.engine.SignalHub
import my.github.MrxSiN.modeevolved.engine.TriggerEngine
import my.github.MrxSiN.modeevolved.rule.RuleCodec
import my.github.MrxSiN.modeevolved.store.SecureSettingsStore
import my.github.MrxSiN.modeevolved.signal.AirplaneSource
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
class AutomationStarter(private val logger: Logger) {

    /** Starts the automation. Closing the returned handle stops all of it and ends its thread. */
    fun start(context: Context): AutoCloseable {
        val thread = HandlerThread(THREAD_NAME).apply { start() }
        val handler = Handler(thread.looper)
        // Touched only on the automation thread.
        val running = mutableListOf<AutoCloseable>()

        handler.post {
            startGuarded("Trigger automation") { triggers(context, handler).also { it.start() }.let { AutoCloseable(it::stop) } }?.let(running::add)
            startGuarded("Action automation") { actions(context, handler).also { it.start() }.let { AutoCloseable(it::stop) } }?.let(running::add)
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

    private fun startGuarded(name: String, start: () -> AutoCloseable): AutoCloseable? = runCatching(start)
        .onSuccess { logger.info("$name started") }
        .onFailure { logger.warn("$name could not start", it) }
        .getOrNull()

    private fun triggers(context: Context, handler: Handler): ModeAutomation {
        val rules = SecureSettingsStore(context.contentResolver, SecureSettingsStore.RULES_KEY, RuleCodec.standard())
        val modes = NotificationModeController(context.getSystemService(NotificationManager::class.java), logger)
        val engine = TriggerEngine(modes, logger)
        val sources = listOf(
            WifiSource(context, handler),
            BluetoothSource(context, handler, logger),
            LocationSource(context, handler),
            MovementSource(context, handler),
            FlightSource(context, handler),
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
            runner = IntentActionRunner(context, notifications, logger),
            thread = handler::post,
            logger = logger,
        )
    }

    private companion object {
        const val THREAD_NAME = "PixelModesEvolved"
    }
}
