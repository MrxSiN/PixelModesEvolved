package my.github.MrxSiN.modeevolved.diagnostics

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.io.File
import kotlin.concurrent.thread

import my.github.MrxSiN.modeevolved.compat.FeatureContracts
import my.github.MrxSiN.modeevolved.compat.Host
import my.github.MrxSiN.modeevolved.compat.HostDescriptor
import my.github.MrxSiN.modeevolved.compat.ReflectedHostTypes
import my.github.MrxSiN.modeevolved.core.Logger
import my.github.MrxSiN.modeevolved.dex.ClassFinder

/**
 * Writes down the running host ([HostDescriptor]) when a developer asks for it, so the
 * build can join the recorded builds CI checks every contract against.
 *
 * Asked for with `adb shell settings put global pixel_modes_evolved_capture 1`;
 * `scripts/capture-host-fixture.sh` asks, reloads both hosts and pulls what they wrote.
 * The description is read through the same class loader, and the same structural
 * search, the resolver uses, so it is the host exactly as the module sees it.
 * Nothing is read or written unless asked.
 */
class HostDescriptorCapture(
    private val host: Host,
    private val finder: ClassFinder,
    private val logger: Logger,
) {

    fun captureIfRequested(context: Context) {
        val requested = runCatching { Settings.Global.getInt(context.contentResolver, REQUEST, 0) == 1 }.getOrDefault(false)
        if (!requested) return
        // Off the host's start: a description reads a few hundred classes, and Settings' may search its dex.
        thread(name = "PixelModesEvolved-capture") {
            runCatching {
                val scope = HostDescriptor.Scope.of(FeatureContracts.apisOf(host))
                val locate = scope.structural.mapNotNull { api -> finder.candidates(api)?.let { api.knownName to it } }.toMap()
                val types = ReflectedHostTypes(finder::byName)
                val file = fileFor(context)
                // Written aside and moved in whole, so a reader never sees half of it.
                val written = File(file.path + ".tmp")
                written.writeText(HostDescriptor.write(Build.ID, host, types, scope, locate))
                check(written.renameTo(file)) { "Could not move $written to $file" }
                logger.info("Described ${host.processName} on ${Build.ID} in $file")
            }.onFailure { logger.warn("Could not describe ${host.processName}", it) }
        }
    }

    companion object {
        /** The `Settings.Global` flag that asks for a description. */
        const val REQUEST = "pixel_modes_evolved_capture"

        const val FILE = "pixel_modes_evolved_host.txt"
    }

    /**
     * Where this host writes its description, as the script expects it: Settings in its own
     * device-protected files, system_server in `/data/system`, the one place each may write.
     */
    private fun fileFor(context: Context): File = when (host) {
        Host.SETTINGS -> File(context.createDeviceProtectedStorageContext().filesDir, FILE)
        Host.SYSTEM_SERVER -> File("/data/system", FILE)
    }
}
