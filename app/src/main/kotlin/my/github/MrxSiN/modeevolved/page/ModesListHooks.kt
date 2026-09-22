package my.github.MrxSiN.modeevolved.page

import android.annotation.SuppressLint
import android.content.Context
import java.lang.reflect.Modifier

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.ExceptionMode

import my.github.MrxSiN.modeevolved.R
import my.github.MrxSiN.modeevolved.core.Feature
import my.github.MrxSiN.modeevolved.core.FeatureInstaller
import my.github.MrxSiN.modeevolved.core.Logger
import my.github.MrxSiN.modeevolved.dex.ClassFinder
import my.github.MrxSiN.modeevolved.dex.SettingsClasses

/**
 * Adds this module's own row to the Modes list, under "Create your own mode".
 *
 * `ZenModesFragmentBase` is the base of every Modes page, the list included.
 * Its refreshes are hooked, and the row is drawn only on a screen that has the
 * add-a-mode button, which is what tells the list apart from a single Mode's page.
 *
 * `onStart` is the refresh to hook: `onUpdatedZenModeState` is abstract there and
 * has no body to intercept. One is enough, because the row is drawn once and then
 * left alone until Settings rebuilds the screen, and coming back to the list from
 * a Mode's page starts the fragment again.
 *
 * @param rowFor builds the row, once, from the first list's context.
 */
// Hooking Settings internals is this module's purpose.
@SuppressLint("PrivateApi")
class ModesListHooks(
    private val xposed: XposedInterface,
    private val classes: ClassFinder,
    private val features: FeatureInstaller,
    private val logger: Logger,
    private val rowFor: (Context, PreferenceApi) -> ModesListSection,
) {

    private var section: ModesListSection? = null

    fun install() {
        features.install(Feature.MODES_LIST, ::hook)
    }

    private fun hook() {
        val base = classes.find(SettingsClasses.modesFragmentBase)
        val preferences = PreferenceApi(classes)
        val getScreen = classes.member("ZenModesFragmentBase.getPreferenceScreen()") { base.getMethod("getPreferenceScreen") }

        val onStart = classes.member("ZenModesFragmentBase.onStart()") { base.getDeclaredMethod("onStart") }
        check(!Modifier.isAbstract(onStart.modifiers)) { "${base.name}.onStart has no body to hook" }

        xposed.hook(onStart).setExceptionMode(ExceptionMode.PROTECTIVE).intercept { chain ->
            val result = chain.proceed()
            runCatching { render(preferences, getScreen.invoke(chain.thisObject)) }
                .onFailure { logger.warn("Could not add to the Modes list", it) }
            result
        }
    }

    /** Draws nothing on a screen without the add-a-mode button, which is every page but the list. */
    private fun render(preferences: PreferenceApi, screen: Any?) {
        if (screen == null || preferences.find(screen, ADD_MODE) == null) return
        val section = section ?: rowFor(preferences.context(screen), preferences).also { section = it }
        section.render(screen)
    }

    private companion object {
        /** The "Create your own mode" button, and the anchor this module's row sits under. */
        const val ADD_MODE = "add_mode"
    }
}

/**
 * The "Compatibility & diagnostics" row at the bottom of the Modes list.
 *
 * One row for the whole module rather than one on every Mode's page: what it
 * reports is about the module meeting this Android build, not about any one Mode.
 */
class ModesListSection(
    private val preferences: PreferenceApi,
    private val diagnostics: DiagnosticsLauncher,
    private val text: ModuleText,
) {

    fun render(screen: Any) {
        if (preferences.find(screen, ROW_KEY) != null) return
        val context = preferences.context(screen)
        val summary = diagnostics.summary(context, text)
        preferences.putAfter(
            screen,
            ADD_MODE,
            PreferenceRow.Link(
                key = ROW_KEY,
                title = text.string(R.string.diagnostics_title),
                summary = summary.line,
                icon = text.icon(context, if (summary.ok) R.drawable.ic_check else R.drawable.ic_warning),
            ) { diagnostics.open(context) },
        )
    }

    private companion object {
        const val ADD_MODE = "add_mode"
        const val ROW_KEY = "${TriggerSection.KEY_PREFIX}diagnostics"
    }
}
