package my.github.MrxSiN.modeevolved.page

import android.content.Context
import android.content.pm.ApplicationInfo

import io.github.libxposed.api.XposedInterface

import my.github.MrxSiN.modeevolved.compat.FeatureContracts
import my.github.MrxSiN.modeevolved.compat.Host
import my.github.MrxSiN.modeevolved.core.BufferedHealthRecorder
import my.github.MrxSiN.modeevolved.core.FeatureInstaller
import my.github.MrxSiN.modeevolved.core.Logger
import my.github.MrxSiN.modeevolved.diagnostics.HostDescriptorCapture
import my.github.MrxSiN.modeevolved.dex.DexClassFinder
import my.github.MrxSiN.modeevolved.dex.DexSearch
import my.github.MrxSiN.modeevolved.dex.HostResolver
import my.github.MrxSiN.modeevolved.editor.ItemFamilies
import my.github.MrxSiN.modeevolved.store.SecureDiagnosticsStore

/**
 * This module's part in the Settings app: the rows on each Mode's page, the compatibility
 * row on the Modes list, and storing what the editor returns.
 *
 * Settings owns the UI and the stored rules; nothing here decides when a Mode switches.
 * A new build does not hot reload into Settings: it restarts once hidden instead
 * ([ModuleUpdateRestarter]), because its pages hold views and callbacks of the build that
 * drew them.
 *
 * @param module this module's own package, as the framework reported it.
 */
class SettingsPart(
    private val xposed: XposedInterface,
    private val module: ApplicationInfo,
    private val logger: Logger,
) {

    private var text: ModuleText? = null

    /** Where this process wrote what it found, once Settings had somewhere to write it. */
    private var diagnostics: SecureDiagnosticsStore? = null

    private val opener = EditorOpener()

    fun install(classLoader: ClassLoader) {
        logger.info("Loading in ${Host.SETTINGS.processName} from ${module.sourceDir}")
        // Names first; the dex is searched only when one this build was written against has gone.
        val finder = DexClassFinder(classLoader, DexSearch(module.nativeLibraryDir, logger), logger)
        val apis = HostResolver(finder)
        // Hooks go in before Settings has an Application, so the self-test is held until one exists.
        val health = BufferedHealthRecorder()
        val features = FeatureInstaller(logger, health) { feature -> apis.missing(FeatureContracts.of(feature)).map { it.id } }
        val restarter = ModuleUpdateRestarter(module.packageName, module.sourceDir, logger)
        val refresh = ModePageRefresh()

        features.install(
            ApplicationStartHooks(xposed, apis, logger) { application ->
                val store = SecureDiagnosticsStore(
                    application.contentResolver,
                    SecureDiagnosticsStore.SETTINGS_KEY,
                    apis.log,
                    xposed.frameworkName,
                    xposed.apiVersion,
                )
                diagnostics = store
                health.writeTo(store)
                restarter.watch(application)
                HostDescriptorCapture(Host.SETTINGS, finder, logger).captureIfRequested(application)
            },
        )
        features.install(ModePageHooks(xposed, apis, logger, ::sections, refresh))
        features.install(ModesListHooks(xposed, apis, logger, ::diagnosticsRow))
        features.install(StockCalendarHooks(xposed, apis, logger, ::text, opener))
        features.install(ScheduleRemovalHooks(xposed, apis, logger, ::text, refresh))
        features.install(EditorResultHooks(xposed, apis, logger))
        features.install(ButtonGroupHooks(xposed, apis, TriggerSection.KEY_PREFIX, logger))
    }

    private fun sections(context: Context, preferences: PreferenceApi): List<ModePageSection> {
        val text = text(context)
        val editor = EditorLauncher(opener)
        val resolver = context.contentResolver
        return listOf(
            TriggerSection(preferences, ItemFamilies.triggers.store(resolver), editor, text),
            ActionSection(preferences, ItemFamilies.actions.store(resolver), editor, text),
        )
    }

    /**
     * The compatibility row under "Create your own mode".
     *
     * Only the Settings process can see both halves of the report, the Android build
     * underneath and the framework that loaded this, so it gathers all of it here.
     */
    private fun diagnosticsRow(context: Context, preferences: PreferenceApi): ModesListSection {
        val store = checkNotNull(diagnostics) { "Settings has no Application yet" }
        val launcher = DiagnosticsLauncher(store, moduleVersion(context), xposed.frameworkName, xposed.frameworkVersion, xposed.apiVersion, opener)
        return ModesListSection(preferences, launcher, text(context))
    }

    /** This module's own version name, read from its package rather than baked into the code. */
    private fun moduleVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(module.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    /**
     * This module's resources, read once per process.
     *
     * Looked up by package name, because the framework's copy of the module's application info can
     * still name the APK a reinstall has just deleted. Resource IDs are pinned in `resource-ids.txt`,
     * so hook code from the replaced build still finds the right entries until Settings restarts.
     */
    private fun text(context: Context): ModuleText = text ?: ModuleText(
        context.packageManager.getResourcesForApplication(module.packageName),
    ).also { text = it }
}
