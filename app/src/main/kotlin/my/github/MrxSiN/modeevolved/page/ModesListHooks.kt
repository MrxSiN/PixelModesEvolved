package my.github.MrxSiN.modeevolved.page

import android.content.Context

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.ExceptionMode

import my.github.MrxSiN.modeevolved.R
import my.github.MrxSiN.modeevolved.compat.SettingsApis as S
import my.github.MrxSiN.modeevolved.core.Feature
import my.github.MrxSiN.modeevolved.core.FeatureHooks
import my.github.MrxSiN.modeevolved.core.Logger
import my.github.MrxSiN.modeevolved.dex.HostResolver

/**
 * Adds this module's own row to the Modes list, under "Create your own mode".
 *
 * `ZenModesFragmentBase` is the base of every Modes page, the list included.
 * Its refreshes are hooked, and the row is drawn only on a screen that has the
 * add-a-mode button, which is what tells the list apart from a single Mode's page.
 *
 * `onStart` is the refresh to hook: `onUpdatedZenModeState` is abstract there and
 * has no body to intercept, which the contract's hook scope checks. One is enough,
 * because the row is drawn once and then left alone until Settings rebuilds the
 * screen, and coming back to the list from a Mode's page starts the fragment again.
 *
 * @param rowFor builds the row, once, from the first list's context.
 */
class ModesListHooks(
    private val xposed: XposedInterface,
    private val apis: HostResolver,
    private val logger: Logger,
    private val rowFor: (Context, PreferenceApi) -> ModesListSection,
) : FeatureHooks {

    override val feature = Feature.MODES_LIST

    private var section: ModesListSection? = null

    override fun install() {
        val preferences = PreferenceApi(apis)
        val getScreen = apis.method(S.MODES_FRAGMENT_BASE_GET_SCREEN)

        xposed.hook(apis.method(S.MODES_FRAGMENT_BASE_ON_START)).setExceptionMode(ExceptionMode.PROTECTIVE).intercept { chain ->
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
