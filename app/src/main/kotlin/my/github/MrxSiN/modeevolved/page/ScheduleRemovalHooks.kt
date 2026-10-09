package my.github.MrxSiN.modeevolved.page

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.view.Menu
import android.view.MenuItem

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.ExceptionMode

import my.github.MrxSiN.modeevolved.R
import my.github.MrxSiN.modeevolved.compat.SettingsApis as S
import my.github.MrxSiN.modeevolved.core.Feature
import my.github.MrxSiN.modeevolved.core.FeatureHooks
import my.github.MrxSiN.modeevolved.core.Logger
import my.github.MrxSiN.modeevolved.dex.HostResolver

/**
 * Adds a "Remove schedule" trash button to the app bar of the stock Day and time and Calendar
 * events pages, where the trigger editor keeps its own remove button.
 *
 * Android lets a person give a Mode a schedule but offers no way back to a Mode without one.
 * Both pages are `ZenModeFragmentBase` subclasses, so the button is added when one is created.
 * That base is found from the Mode page itself when a release renames it (its contract's locator).
 */
class ScheduleRemovalHooks(
    private val xposed: XposedInterface,
    private val apis: HostResolver,
    private val logger: Logger,
    private val textFor: (Context) -> ModuleText,
    private val refresh: ModePageRefresh,
) : FeatureHooks {

    override val feature = Feature.SCHEDULE_REMOVAL

    override fun install() {
        val schedulePages = setOf(apis.type(S.SCHEDULE_TIME_FRAGMENT), apis.type(S.SCHEDULE_CALENDAR_FRAGMENT))
        val getMode = apis.method(S.MODE_FRAGMENT_BASE_GET_MODE)
        val button = ScheduleRemovalButton(MenuApi(apis), ZenModeApi(apis), ModeRuleWriter(apis), textFor, refresh, logger)

        xposed.hook(apis.method(S.MODE_FRAGMENT_BASE_ON_CREATE)).setExceptionMode(ExceptionMode.PROTECTIVE).intercept { chain ->
            val result = chain.proceed()
            val page = chain.thisObject
            if (page.javaClass in schedulePages) {
                runCatching { button.addTo(page) { getMode.invoke(page) } }
                    .onFailure { logger.warn("Could not add Remove schedule", it) }
            }
            result
        }
    }
}

/** The trash button, its confirmation, and the change it stores. */
private class ScheduleRemovalButton(
    private val menus: MenuApi,
    private val modes: ZenModeApi,
    private val rules: ModeRuleWriter,
    private val textFor: (Context) -> ModuleText,
    private val refresh: ModePageRefresh,
    private val logger: Logger,
) {

    /** @param mode reads the page's Mode when the menu is built or tapped, since it can change meanwhile. */
    fun addTo(page: Any, mode: () -> Any?) {
        val activity = menus.activity(page)
        menus.add(
            page,
            onCreate = { menu -> mode()?.takeIf(modes::hasSchedule)?.let { menu.addButton(activity) } },
            onSelect = { item ->
                if (item.itemId == ITEM_ID) mode()?.let { confirm(activity, it) }
                item.itemId == ITEM_ID
            },
        )
    }

    private fun Menu.addButton(activity: Activity) {
        val text = textFor(activity)
        add(Menu.NONE, ITEM_ID, Menu.NONE, text.string(R.string.schedule_remove))
            .setIcon(text.icon(activity, R.drawable.ic_delete))
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
    }

    private fun confirm(activity: Activity, mode: Any) {
        val text = textFor(activity)
        AlertDialog.Builder(activity)
            .setTitle(text.string(R.string.schedule_remove_title))
            .setMessage(text.raw.getString(R.string.schedule_remove_text, modes.ref(mode).name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(text.string(R.string.schedule_remove_confirm)) { _, _ -> remove(activity, mode) }
            .show()
    }

    /**
     * Closes the schedule page, then stores the Mode without its schedule and has its page rebuilt.
     *
     * Stock Settings never removes a schedule, so a schedule page still open when the change
     * lands would crash refreshing controllers that read it. The change waits until that page is gone.
     */
    private fun remove(activity: Activity, mode: Any) {
        val ref = modes.ref(mode)
        activity.finishThen {
            runCatching {
                modes.clearSchedule(activity, mode)
                rules.update(activity, ref.id, modes.rule(mode))
            }
                .onSuccess {
                    logger.info("Removed the schedule of mode ${ref.id}")
                    refresh.request(ref.id)
                }
                .onFailure { logger.warn("Could not remove the schedule of mode ${ref.id}", it) }
        }
    }

    private companion object {
        /** Unlikely to meet a stock menu item, which Settings numbers from 1. */
        const val ITEM_ID = 0x4d45
    }
}
