package my.github.MrxSiN.modeevolved.signal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler

import my.github.MrxSiN.modeevolved.engine.SignalSource
import my.github.MrxSiN.modeevolved.trigger.Signal
import my.github.MrxSiN.modeevolved.trigger.StateUpdate

/**
 * Whether the device is connected to external power.
 *
 * Woken only on the edges: Android sends power connected and disconnected once each, and the
 * level-by-level battery broadcast is read once at start (it is sticky) and never registered
 * for, so a charging phone costs nothing per percent. Started only while a rule reads it.
 */
class PowerSource(private val context: Context, private val handler: Handler) : SignalSource {

    override val signal: Signal = Signal.POWER

    private var publish: ((StateUpdate) -> Unit)? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(received: Context?, intent: Intent?) {
            val connected = intent?.action == Intent.ACTION_POWER_CONNECTED
            publish?.invoke { it.copy(powerConnected = connected) }
        }
    }

    override fun start(publish: (StateUpdate) -> Unit) {
        this.publish = publish
        val edges = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        context.registerReceiver(receiver, edges, null, handler, Context.RECEIVER_NOT_EXPORTED)
        // The sticky battery state, read without a receiver, says where things stand now.
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED)
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        if (plugged >= 0) publish { it.copy(powerConnected = plugged != 0) }
    }

    override fun stop() {
        runCatching { context.unregisterReceiver(receiver) }
        publish?.invoke { it.copy(powerConnected = null) }
        publish = null
    }
}
