package my.github.MrxSiN.modeevolved.page

import android.annotation.SuppressLint
import android.content.Context
import java.lang.reflect.Method

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.ExceptionMode

import my.github.MrxSiN.modeevolved.core.Logger

/**
 * Adds this module's sections to each Mode's page in the Settings app.
 *
 * `ZenModeFragment` builds its screen from XML and keeps it up to date from
 * `onResume` and `onUpdatedZenModeState`. The rows are rebuilt after both, so
 * they survive the page refreshing itself and reflect the current rule.
 *
 * @param sectionsFor builds the sections, once, from the first page's context.
 */
// Hooking Settings internals is this module's purpose.
@SuppressLint("PrivateApi")
class ModePageHooks(
    private val xposed: XposedInterface,
    private val classLoader: ClassLoader,
    private val logger: Logger,
    private val sectionsFor: (Context, PreferenceApi) -> List<ModePageSection>,
    private val refresh: ModePageRefresh,
) {

    private var sections: List<ModePageSection>? = null

    fun install() {
        runCatching(::hook)
            .onSuccess { logger.info("Hooked Mode page") }
            .onFailure { logger.warn("Could not hook Mode page", it) }
    }

    private fun hook() {
        val fragment = classLoader.loadClass(MODE_FRAGMENT)
        val page = Page(fragment.getMethod("getMode"), fragment.getMethod("getPreferenceScreen"), PreferenceApi(classLoader))

        for (name in REFRESH_METHODS) {
            xposed.hook(fragment.getDeclaredMethod(name)).setExceptionMode(ExceptionMode.PROTECTIVE).intercept { chain ->
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

    /** The fragment members and preference classes every render uses, looked up once. */
    private class Page(val getMode: Method, val getScreen: Method, val preferences: PreferenceApi)

    private companion object {
        const val MODE_FRAGMENT = "com.android.settings.notification.modes.ZenModeFragment"

        val REFRESH_METHODS = listOf("onResume", "onUpdatedZenModeState")
    }
}
