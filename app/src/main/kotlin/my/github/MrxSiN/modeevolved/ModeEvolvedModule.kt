package my.github.MrxSiN.modeevolved

import android.content.Context

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

import my.github.MrxSiN.modeevolved.core.AndroidLogger
import my.github.MrxSiN.modeevolved.core.BufferedHealthRecorder
import my.github.MrxSiN.modeevolved.core.FeatureInstaller
import my.github.MrxSiN.modeevolved.dex.DexClassFinder
import my.github.MrxSiN.modeevolved.dex.DexSearch
import my.github.MrxSiN.modeevolved.editor.ItemFamilies
import my.github.MrxSiN.modeevolved.page.ActionSection
import my.github.MrxSiN.modeevolved.page.ApplicationStartHooks
import my.github.MrxSiN.modeevolved.page.ButtonGroupHooks
import my.github.MrxSiN.modeevolved.page.EditorLauncher
import my.github.MrxSiN.modeevolved.page.DiagnosticsLauncher
import my.github.MrxSiN.modeevolved.page.EditorResultHooks
import my.github.MrxSiN.modeevolved.page.ModePageHooks
import my.github.MrxSiN.modeevolved.page.ModePageRefresh
import my.github.MrxSiN.modeevolved.page.ModePageSection
import my.github.MrxSiN.modeevolved.page.ModesListHooks
import my.github.MrxSiN.modeevolved.page.ModesListSection
import my.github.MrxSiN.modeevolved.page.ModuleUpdateRestarter
import my.github.MrxSiN.modeevolved.page.ModuleText
import my.github.MrxSiN.modeevolved.page.PreferenceApi
import my.github.MrxSiN.modeevolved.page.ScheduleRemovalHooks
import my.github.MrxSiN.modeevolved.page.StockCalendarHooks
import my.github.MrxSiN.modeevolved.page.TriggerSection
import my.github.MrxSiN.modeevolved.store.SecureDiagnosticsStore
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
        // Names first; the dex is searched only when one this build was written against has gone.
        val classes = DexClassFinder(
            param.defaultClassLoader,
            DexSearch(moduleApplicationInfo.nativeLibraryDir, logger),
            logger,
        )
        // Hooks go in before Settings has an Application, so the self-test is held until one exists.
        val health = BufferedHealthRecorder()
        val features = FeatureInstaller(logger, health)
        val restarter = ModuleUpdateRestarter(moduleApplicationInfo.packageName, moduleApplicationInfo.sourceDir, logger)
        ApplicationStartHooks(this, features, logger) { application ->
            val store = SecureDiagnosticsStore(
                application.contentResolver,
                SecureDiagnosticsStore.SETTINGS_KEY,
                classes.contracts,
                frameworkName,
                apiVersion,
            )
            settings = store
            health.writeTo(store)
            restarter.watch(application)
        }.install()
        val refresh = ModePageRefresh()
        ModePageHooks(this, classes, features, logger, ::sections, refresh).install()
        ModesListHooks(this, classes, features, logger, ::diagnosticsRow).install()
        StockCalendarHooks(this, classes, features, logger, ::text).install()
        ScheduleRemovalHooks(this, classes, features, logger, ::text, refresh).install()
        EditorResultHooks(this, classes, features, logger).install()
        ButtonGroupHooks(this, classes, TriggerSection.KEY_PREFIX, features, logger).install()
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

    /** Where this process wrote what it found, once Settings had somewhere to write it. */
    private var settings: SecureDiagnosticsStore? = null

    /**
     * The compatibility row under "Create your own mode".
     *
     * Only the Settings process can see both halves of the report, the Android build
     * underneath and the framework that loaded this, so it gathers all of it here.
     */
    private fun diagnosticsRow(context: Context, preferences: PreferenceApi): ModesListSection {
        val store = checkNotNull(settings) { "Settings has no Application yet" }
        val launcher = DiagnosticsLauncher(store, moduleVersion(context), frameworkName, frameworkVersion, apiVersion)
        return ModesListSection(preferences, launcher, text(context))
    }

    /** This module's own version name, read from its package rather than baked into the code. */
    private fun moduleVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(moduleApplicationInfo.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

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
