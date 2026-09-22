package my.github.MrxSiN.modeevolved.page

import android.annotation.SuppressLint
import android.content.Context
import java.lang.reflect.Method

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.ExceptionMode

import my.github.MrxSiN.modeevolved.core.Feature
import my.github.MrxSiN.modeevolved.core.FeatureInstaller
import my.github.MrxSiN.modeevolved.core.Logger
import my.github.MrxSiN.modeevolved.dex.ClassFinder
import my.github.MrxSiN.modeevolved.dex.SettingsClasses

/**
 * Adds this module's sections to each Mode's page in the Settings app.
 *
 * `ZenModeFragment` builds its screen from XML and keeps it up to date from
 * `onResume` and `onUpdatedZenModeState`. The rows are rebuilt after both, so
 * they survive the page refreshing itself and reflect the current rule. The
 * fragment is reached through [ClassFinder], which still finds it when a
 * quarterly release has moved or renamed it.
 *
 * @param sectionsFor builds the sections, once, from the first page's context.
 */
// Hooking Settings internals is this module's purpose.
@SuppressLint("PrivateApi")
class ModePageHooks(
    private val xposed: XposedInterface,
    private val classes: ClassFinder,
    private val features: FeatureInstaller,
    private val logger: Logger,
    private val sectionsFor: (Context, PreferenceApi) -> List<ModePageSection>,
    private val refresh: ModePageRefresh,
) {

    private var sections: List<ModePageSection>? = null

    fun install() {
        features.install(Feature.MODE_PAGE, ::hook)
    }

    private fun hook() {
        val fragment = classes.find(SettingsClasses.modeFragment)
        val page = Page(
            classes.member("ZenModeFragment.getMode()") { fragment.getMethod("getMode") },
            classes.member("ZenModeFragment.getPreferenceScreen()") { fragment.getMethod("getPreferenceScreen") },
            PreferenceApi(classes),
            // Only the schedule warning needs this, so losing it must not cost the rows themselves.
            runCatching { ZenModeApi(classes) }.getOrNull(),
        )

        for (name in REFRESH_METHODS) {
            val refresh = classes.member("ZenModeFragment.$name()") { fragment.getDeclaredMethod(name) }
            xposed.hook(refresh).setExceptionMode(ExceptionMode.PROTECTIVE).intercept { chain ->
                val result = chain.proceed()
                runCatching { render(page, chain.thisObject) }
                    .onFailure { logger.warn("Could not add to the Mode page", it) }
                result
            }
        }
    }

    private fun render(page: Page, fragment: Any) {
        val mode = page.getMode.invoke(fragment) ?: return
        val screen = page.getScreen.invoke(fragment) ?: return
        val ref = ModeRef(
            id = mode.javaClass.getMethod("getId").invoke(mode) as String,
            name = mode.javaClass.getMethod("getName").invoke(mode) as String,
            hasSchedule = page.modes?.let { runCatching { it.hasSchedule(mode) }.getOrDefault(false) } == true,
        )
        val context = page.preferences.context(screen)
        refresh.consume(ref.id, context)
        val sections = sections ?: sectionsFor(context, page.preferences).also { sections = it }
        // Each section fails alone, so a problem in one never hides the other.
        for (section in sections) {
            runCatching { section.render(screen, ref) }
                .onFailure { logger.warn("Could not draw ${section::class.java.simpleName}", it) }
        }
    }

    /** The fragment members and reflection boundaries every render uses, looked up once. */
    private class Page(
        val getMode: Method,
        val getScreen: Method,
        val preferences: PreferenceApi,
        val modes: ZenModeApi?,
    )

    private companion object {
        val REFRESH_METHODS = listOf("onResume", "onUpdatedZenModeState")
    }
}
