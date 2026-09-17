package my.github.MrxSiN.modeevolved.signal

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Handler

import my.github.MrxSiN.modeevolved.engine.SignalSource
import my.github.MrxSiN.modeevolved.trigger.Signal
import my.github.MrxSiN.modeevolved.trigger.StateUpdate

/**
 * Connected Wi-Fi networks by SSID.
 *
 * The SSID is location information, so the callback asks for it explicitly.
 * system_server holds the permissions that make the answer unredacted.
 */
// Runs inside system_server, which holds every permission these calls check.
@SuppressLint("MissingPermission")
class WifiSource(context: Context, private val handler: Handler) : SignalSource {

    override val signal: Signal = Signal.WIFI

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val ssids = mutableMapOf<Network, String>()
    private var publish: ((StateUpdate) -> Unit)? = null

    private val callback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val ssid = (capabilities.transportInfo as? WifiInfo)?.ssid?.let(::unquote)
            if (ssid == null) ssids.remove(network) else ssids[network] = ssid
            emit()
        }

        override fun onLost(network: Network) {
            ssids.remove(network)
            emit()
        }
    }

    override fun start(publish: (StateUpdate) -> Unit) {
        this.publish = publish
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        connectivity.registerNetworkCallback(request, callback, handler)
    }

    override fun stop() {
        connectivity.unregisterNetworkCallback(callback)
        ssids.clear()
        emit()
        publish = null
    }

    private fun emit() {
        val connected = ssids.values.toSet()
        publish?.invoke { it.copy(wifiNetworks = connected) }
    }

    /** `WifiInfo` wraps a UTF-8 SSID in quotes and reports a hidden one as a placeholder. */
    private fun unquote(ssid: String): String? =
        if (ssid == WifiManager.UNKNOWN_SSID) null else ssid.removeSurrounding("\"")
}
