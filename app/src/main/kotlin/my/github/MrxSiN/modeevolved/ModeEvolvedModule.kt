package my.github.MrxSiN.modeevolved

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

import my.github.MrxSiN.modeevolved.compat.Host
import my.github.MrxSiN.modeevolved.core.AndroidLogger
import my.github.MrxSiN.modeevolved.page.SettingsPart
import my.github.MrxSiN.modeevolved.system.SystemServerPart

/**
 * Module entry point. It only routes each scoped process to its part, and decides nothing:
 *
 * - system_server runs the automation ([SystemServerPart]), and takes a new build by hot reload;
 * - the Settings app shows triggers and actions on each Mode's page and stores what the
 *   editor returns ([SettingsPart]), and restarts itself once hidden to take a new build.
 */
class ModeEvolvedModule : XposedModule() {

    private val logger = AndroidLogger

    /** Set only in system_server. */
    private var system: SystemServerPart? = null

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        logger.info("Loading in system_server")
        system = SystemServerPart(this, logger).also { it.install(param.classLoader) }
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean {
        val part = system ?: return false
        param.setSavedInstanceState(part.handOff())
        return true
    }

    override fun onHotReloaded(param: HotReloadedParam) {
        // Removes the previous build's hooks before this build installs its own.
        super.onHotReloaded(param)
        if (param.isSystemServer) system = SystemServerPart(this, logger).also { it.takeOver(param.savedInstanceState) }
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (param.packageName != Host.SETTINGS.processName || !param.isFirstPackage) return
        SettingsPart(this, moduleApplicationInfo, logger).install(param.defaultClassLoader)
    }
}
