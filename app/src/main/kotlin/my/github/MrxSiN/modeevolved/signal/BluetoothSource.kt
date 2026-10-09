package my.github.MrxSiN.modeevolved.signal

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler

import my.github.MrxSiN.modeevolved.core.Logger
import my.github.MrxSiN.modeevolved.engine.SignalSource
import my.github.MrxSiN.modeevolved.trigger.Signal
import my.github.MrxSiN.modeevolved.trigger.StateUpdate

/**
 * Connected Bluetooth devices by hardware address.
 *
 * @param isConnected whether a bonded device is connected now, or null when this build
 * cannot say; then only devices that connect after the source starts are seen.
 */
// Runs inside system_server, which holds every permission these calls check.
@SuppressLint("MissingPermission")
class BluetoothSource(
    private val context: Context,
    private val handler: Handler,
    private val logger: Logger,
    private val isConnected: ((BluetoothDevice) -> Boolean)?,
) : SignalSource {

    override val signal: Signal = Signal.BLUETOOTH

    private val connected = mutableSetOf<String>()
    private var publish: ((StateUpdate) -> Unit)? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val changed = when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> intent.device()?.let(connected::add) == true
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> intent.device()?.let(connected::remove) == true
                BluetoothAdapter.ACTION_STATE_CHANGED -> connected.isNotEmpty().also { connected.clear() }
                else -> false
            }
            if (changed) emit()
        }
    }

    override fun start(publish: (StateUpdate) -> Unit) {
        this.publish = publish
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        // Sent by the Bluetooth stack, which runs under its own uid, so the receiver must be exported.
        context.registerReceiver(receiver, filter, null, handler, Context.RECEIVER_EXPORTED)
        connected += alreadyConnected()
        emit()
    }

    override fun stop() {
        context.unregisterReceiver(receiver)
        connected.clear()
        emit()
        publish = null
    }

    private fun emit() {
        val snapshot = connected.toSet()
        publish?.invoke { it.copy(bluetoothDevices = snapshot) }
    }

    /** Devices connected before the receiver existed, such as a car at boot. */
    private fun alreadyConnected(): Set<String> = runCatching {
        val isConnected = isConnected ?: return emptySet()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return emptySet()
        adapter.bondedDevices.orEmpty()
            .filter(isConnected)
            .mapTo(mutableSetOf()) { it.address.uppercase() }
    }.getOrElse {
        logger.warn("Could not read connected Bluetooth devices", it)
        emptySet()
    }

    private fun Intent.device(): String? =
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)?.address?.uppercase()
}
