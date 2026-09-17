package my.github.MrxSiN.modeevolved

import android.content.Context

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

import my.github.MrxSiN.modeevolved.core.AndroidLogger
import my.github.MrxSiN.modeevolved.editor.ItemFamilies
import my.github.MrxSiN.modeevolved.page.ActionSection
import my.github.MrxSiN.modeevolved.page.ApplicationStartHooks
import my.github.MrxSiN.modeevolved.page.ButtonGroupHooks
import my.github.MrxSiN.modeevolved.page.EditorLauncher
import my.github.MrxSiN.modeevolved.page.EditorResultHooks
import my.github.MrxSiN.modeevolved.page.ModePageHooks
import my.github.MrxSiN.modeevolved.page.ModePageRefresh
import my.github.MrxSiN.modeevolved.page.ModePageSection
import my.github.MrxSiN.modeevolved.page.ModuleUpdateRestarter
import my.github.MrxSiN.modeevolved.page.ModuleText
import my.github.MrxSiN.modeevolved.page.PreferenceApi
import my.github.MrxSiN.modeevolved.page.ScheduleRemovalHooks
import my.github.MrxSiN.modeevolved.page.StockCalendarHooks
import my.github.MrxSiN.modeevolved.page.TriggerSection
import my.github.MrxSiN.modeevolved.system.SystemServerPart

/**
 * Module entry point.
 *
 * Routes each scoped process to its part: system_server runs the automation,
 * the Settings app shows triggers and actions on each Mode's page and stores
 * what the editor returns. Neither decides anything here.
 *
 * A new build reaches system_server by hot reload. The Settings app declines one: it restarts
 * itself once hidden instead ([ModuleUpdateRestarter]), because its pages hold views and
 * callbacks of the build that drew them.
 */
class ModeEvolvedModule : XposedModule() {

    private val logger = AndroidLogger
    private var text: ModuleText? = null

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
        if (param.packageName != SETTINGS_PACKAGE || !param.isFirstPackage) return
        logger.info("Loading in ${param.packageName} from ${moduleApplicationInfo.sourceDir}")
        val classLoader = param.defaultClassLoader
        val restarter = ModuleUpdateRestarter(moduleApplicationInfo.packageName, moduleApplicationInfo.sourceDir, logger)
        ApplicationStartHooks(this, logger, restarter::watch).install()
        val refresh = ModePageRefresh()
        ModePageHooks(this, classLoader, logger, ::sections, refresh).install()
        StockCalendarHooks(this, classLoader, logger, ::text).install()
        ScheduleRemovalHooks(this, classLoader, logger, ::text, refresh).install()
        EditorResultHooks(this, classLoader, logger).install()
        ButtonGroupHooks(this, classLoader, TriggerSection.KEY_PREFIX, logger).install()
    }

    private fun sections(context: Context, preferences: PreferenceApi): List<ModePageSection> {
        val text = text(context)
        val editor = EditorLauncher()
        val resolver = context.contentResolver
        return listOf(
            TriggerSection(preferences, ItemFamilies.triggers.store(resolver), editor, text),
            ActionSection(preferences, ItemFamilies.actions.store(resolver), editor, text),
        )
    }

    /**
     * This module's resources, read once per process.
     *
     * Looked up by package name, because the framework's copy of the module's application info can
     * still name the APK a reinstall has just deleted. Resource IDs are pinned in `resource-ids.txt`,
     * so hook code from the replaced build still finds the right entries until Settings restarts.
     */
    private fun text(context: Context): ModuleText = text ?: ModuleText(
        context.packageManager.getResourcesForApplication(moduleApplicationInfo.packageName),
    ).also { text = it }

    private companion object {
        const val SETTINGS_PACKAGE = "com.android.settings"
    }
}
