package my.github.MrxSiN.modeevolved.page

import android.content.Context
import java.lang.reflect.Method

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.ExceptionMode

import my.github.MrxSiN.modeevolved.compat.SettingsApis as S
import my.github.MrxSiN.modeevolved.core.Feature
import my.github.MrxSiN.modeevolved.core.FeatureHooks
import my.github.MrxSiN.modeevolved.core.Logger
import my.github.MrxSiN.modeevolved.dex.HostResolver

/**
 * Adds this module's sections to each Mode's page in the Settings app.
 *
 * `ZenModeFragment` builds its screen from XML and keeps it up to date from
 * `onResume` and `onUpdatedZenModeState`. The rows are rebuilt after both, so
 * they survive the page refreshing itself and reflect the current rule. The
 * fragment is resolved from its contract, which still finds it when a
 * quarterly release has moved or renamed it.
 *
 * @param sectionsFor builds the sections, once, from the first page's context.
 */
class ModePageHooks(
    private val xposed: XposedInterface,
    private val apis: HostResolver,
    private val logger: Logger,
    private val sectionsFor: (Context, PreferenceApi) -> List<ModePageSection>,
    private val refresh: ModePageRefresh,
) : FeatureHooks {

    override val feature = Feature.MODE_PAGE

    private var sections: List<ModePageSection>? = null

    override fun install() {
        val page = Page(
            apis.method(S.MODE_FRAGMENT_GET_MODE),
            apis.method(S.MODE_FRAGMENT_GET_SCREEN),
            PreferenceApi(apis),
            ZenModeApi(apis),
        )

        for (refresh in listOf(apis.method(S.MODE_FRAGMENT_ON_RESUME), apis.method(S.MODE_FRAGMENT_ON_UPDATED))) {
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
        // Only the schedule warning needs the rule, so losing it must not cost the rows themselves.
        val ref = page.modes.ref(mode).copy(hasSchedule = runCatching { page.modes.hasSchedule(mode) }.getOrDefault(false))
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
        val modes: ZenModeApi,
    )
}
