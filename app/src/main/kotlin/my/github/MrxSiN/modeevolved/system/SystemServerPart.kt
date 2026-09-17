package my.github.MrxSiN.modeevolved.system

import android.content.Context

import io.github.libxposed.api.XposedInterface

import my.github.MrxSiN.modeevolved.core.Logger

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

    @Volatile private var classLoader: ClassLoader? = null
    @Volatile private var context: Context? = null
    @Volatile private var automation: AutoCloseable? = null

    /** Installs the hooks; the automation starts when boot completes. */
    fun install(classLoader: ClassLoader) {
        this.classLoader = classLoader
        CalendarEventFilterHooks(xposed, classLoader, logger).install()
        SystemServerHooks(xposed, classLoader, logger, ::start).install()
    }

    /** Stops this build's automation and returns what the next build needs to take over. */
    fun handOff(): Map<String, Any?> {
        automation?.close()
        automation = null
        logger.info("Handing system_server over to a new build")
        return mapOf(CLASS_LOADER to classLoader, CONTEXT to context)
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
        automation = AutomationStarter(logger).start(context)
    }

    private companion object {
        const val CLASS_LOADER = "classLoader"
        const val CONTEXT = "context"
    }
}
