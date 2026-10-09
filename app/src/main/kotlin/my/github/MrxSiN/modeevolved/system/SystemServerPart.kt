package my.github.MrxSiN.modeevolved.system

import android.content.Context

import io.github.libxposed.api.XposedInterface

import my.github.MrxSiN.modeevolved.compat.FeatureContracts
import my.github.MrxSiN.modeevolved.compat.Host
import my.github.MrxSiN.modeevolved.core.BufferedHealthRecorder
import my.github.MrxSiN.modeevolved.core.FeatureInstaller
import my.github.MrxSiN.modeevolved.core.Logger
import my.github.MrxSiN.modeevolved.diagnostics.HostDescriptorCapture
import my.github.MrxSiN.modeevolved.dex.HostResolver
import my.github.MrxSiN.modeevolved.dex.NamedClassFinder
import my.github.MrxSiN.modeevolved.store.SecureDiagnosticsStore

/**
 * This module's part in system_server: its hooks and the automation, loaded at boot or
 * handed over from the previous build by a hot reload.
 *
 * Vector hot reloads the module into system_server when it is updated (`autoHotReload` in
 * module.prop). The old build stops its automation in [handOff]; the new build installs its
 * hooks again and, when boot has already completed, starts the automation at once in [takeOver].
 * What passes between the two builds holds only framework objects, so no class of the old build
 * stays loaded.
 */
class SystemServerPart(private val xposed: XposedInterface, private val logger: Logger) {

    @Volatile private var apis: HostResolver? = null
    @Volatile private var features: FeatureInstaller? = null
    @Volatile private var context: Context? = null
    @Volatile private var automation: AutoCloseable? = null

    /**
     * What this part managed to hook.
     *
     * Held rather than stored until boot completes: the hooks go in before system_server
     * has a context, and there is no content resolver to write through before then.
     */
    private val health = BufferedHealthRecorder()

    /** Installs the hooks; the automation starts when boot completes. */
    fun install(classLoader: ClassLoader) {
        // Names only: searching the framework's dex would mean parsing every boot jar at boot.
        val apis = HostResolver(NamedClassFinder(classLoader)).also { apis = it }
        val features = FeatureInstaller(logger, health) { feature -> apis.missing(FeatureContracts.of(feature)).map { it.id } }
            .also { features = it }
        features.install(CalendarEventFilterHooks(xposed, apis, logger))
        features.install(SystemServerHooks(xposed, apis, ::start))
    }

    /** Stops this build's automation and returns what the next build needs to take over. */
    fun handOff(): Map<String, Any?> {
        automation?.close()
        automation = null
        logger.info("Handing system_server over to a new build")
        return mapOf(CLASS_LOADER to apis?.classLoader, CONTEXT to context)
    }

    /** Continues from [state], what the previous build's [handOff] returned. */
    fun takeOver(state: Any?) {
        val saved = state as? Map<*, *>
        val loader = saved?.get(CLASS_LOADER) as? ClassLoader
            ?: return logger.warn("Hot reloaded without state from the previous build; reboot to start this one")
        install(loader)
        (saved[CONTEXT] as? Context)?.let(::start)
        logger.info("Hot reloaded in system_server")
    }

    private fun start(context: Context) {
        this.context = context
        val apis = checkNotNull(apis)
        val features = checkNotNull(features)
        val store = SecureDiagnosticsStore(
            context.contentResolver,
            SecureDiagnosticsStore.SYSTEM_KEY,
            apis.log,
            "${xposed.frameworkName} ${xposed.frameworkVersion}",
            xposed.apiVersion,
        )
        health.writeTo(store)
        // Each part records its outcome as it starts, on the automation thread, which also writes
        // the members it looked up after the hooks did.
        automation = AutomationStarter(logger, apis, features).start(context)
        HostDescriptorCapture(Host.SYSTEM_SERVER, NamedClassFinder(apis.classLoader), logger).captureIfRequested(context)
    }

    private companion object {
        const val CLASS_LOADER = "classLoader"
        const val CONTEXT = "context"
    }
}
