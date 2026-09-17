package my.github.MrxSiN.modeevolved.page

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.location.LocationManager
import android.net.Uri
import android.net.wifi.WifiManager

import my.github.MrxSiN.modeevolved.editor.EditorFacts
import my.github.MrxSiN.modeevolved.editor.EditorSubject
import my.github.MrxSiN.modeevolved.trigger.BluetoothTrigger
import my.github.MrxSiN.modeevolved.trigger.GeoPoint

/**
 * What the device knows that the editor offers as choices.
 *
 * Read in the Settings app, which runs as the system uid and may read saved
 * networks, paired devices, the last position and Tasker's task
 * list. The editor app may not. Each read fails alone: a missing answer only
 * leaves that choice empty. Only what the subject's editor shows is read.
 */
// Runs in the Settings app, which holds every permission these calls check.
@SuppressLint("MissingPermission")
class DeviceFacts(private val context: Context) {

    fun collect(subject: EditorSubject): EditorFacts = when (subject) {
        EditorSubject.TRIGGER -> EditorFacts(
            currentSsid = currentSsid(),
            savedSsids = savedSsids(),
            pairedDevices = pairedDevices(),
            lastLocation = lastLocation(),
        )
        EditorSubject.ACTION -> EditorFacts(taskerTasks = taskerTasks())
        EditorSubject.CALENDAR_EVENT -> EditorFacts()
    }

    @Suppress("DEPRECATION")
    private fun currentSsid(): String? = read {
        context.getSystemService(WifiManager::class.java).connectionInfo.ssid?.let(::unquote)
    }

    @Suppress("DEPRECATION")
    private fun savedSsids(): List<String> = read {
        context.getSystemService(WifiManager::class.java).configuredNetworks
            .mapNotNull { it.SSID?.let(::unquote) }
            .distinct()
            .sortedBy { it.lowercase() }
    }.orEmpty()

    private fun pairedDevices(): List<BluetoothTrigger> = read {
        context.getSystemService(BluetoothManager::class.java).adapter.bondedDevices
            .map { BluetoothTrigger(it.address.uppercase(), it.name.orEmpty()) }
            .sortedBy { it.name.lowercase() }
    }.orEmpty()

    private fun lastLocation(): GeoPoint? = read {
        val locations = context.getSystemService(LocationManager::class.java)
        listOf(LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { runCatching { locations.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.elapsedRealtimeNanos }
            ?.let { GeoPoint(it.latitude, it.longitude) }
    }

    /** Tasker's task names, from the provider its own TaskerIntent helper reads. Empty unless external access is allowed. */
    private fun taskerTasks(): List<String> = read {
        context.contentResolver.query(TASKER_TASKS, null, null, null, null)?.use { rows ->
            val name = rows.getColumnIndex("name").takeIf { it >= 0 } ?: 0
            buildList { while (rows.moveToNext()) rows.getString(name)?.let(::add) }
        }?.distinct()?.sortedBy { it.lowercase() }
    }.orEmpty()

    private fun <T> read(block: () -> T): T? = runCatching(block).getOrNull()

    /** `WifiInfo` and `WifiConfiguration` wrap a UTF-8 SSID in quotes and name an unknown one. */
    private fun unquote(ssid: String): String? =
        if (ssid == WifiManager.UNKNOWN_SSID) null else ssid.removeSurrounding("\"").ifEmpty { null }

    private companion object {
        val TASKER_TASKS: Uri = Uri.parse("content://net.dinglisch.android.tasker/tasks")
    }
}
