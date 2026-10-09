package my.github.MrxSiN.modeevolved.page

import android.app.Activity
import android.view.Menu
import android.view.MenuItem
import java.lang.reflect.Proxy

import my.github.MrxSiN.modeevolved.compat.SettingsApis as S
import my.github.MrxSiN.modeevolved.dex.HostResolver

/**
 * Adds items to the app bar of a Settings page, through the Settings app's own androidx classes.
 *
 * Items are registered as a `MenuProvider` bound to the page's fragment, so they show while it is
 * resumed and go away with it, as the stock Mode page's own menu does.
 */
class MenuApi(apis: HostResolver) {

    private val classLoader = apis.classLoader
    private val provider = apis.type(S.MENU_PROVIDER)
    private val onCreateMenu = apis.method(S.MENU_PROVIDER_ON_CREATE).name
    private val onMenuItemSelected = apis.method(S.MENU_PROVIDER_ON_SELECT).name
    private val requireActivity = apis.method(S.FRAGMENT_REQUIRE_ACTIVITY)
    private val addProvider = apis.method(S.ADD_MENU_PROVIDER)
    private val resumed = apis.field(S.LIFECYCLE_RESUMED).get(null)

    /** The activity showing [page], a fragment. */
    fun activity(page: Any): Activity = requireActivity.invoke(page) as Activity

    /**
     * Shows a menu on [page], a fragment, while it is resumed.
     *
     * @param onCreate adds the items each time the menu is built.
     * @param onSelect answers whether it handled the item.
     */
    fun add(page: Any, onCreate: (Menu) -> Unit, onSelect: (MenuItem) -> Boolean) {
        val menu = Proxy.newProxyInstance(classLoader, arrayOf(provider)) { proxy, method, args ->
            when (method.name) {
                onCreateMenu -> onCreate(args[0] as Menu)
                onMenuItemSelected -> onSelect(args[0] as MenuItem)
                "equals" -> proxy === args[0]
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "Pixel Modes Evolved menu"
                else -> null
            }
        }
        addProvider.invoke(activity(page), menu, page, resumed)
    }
}
