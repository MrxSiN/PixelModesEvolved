package my.github.MrxSiN.modeevolved.page

import android.app.Activity
import android.view.Menu
import android.view.MenuItem
import java.lang.reflect.Proxy

/**
 * Adds items to the app bar of a Settings page, through the Settings app's own androidx classes.
 *
 * Items are registered as a `MenuProvider` bound to the page's fragment, so they show while it is
 * resumed and go away with it, as the stock Mode page's own menu does.
 */
class MenuApi(private val classLoader: ClassLoader) {

    private val fragment = classLoader.loadClass("androidx.fragment.app.Fragment")
    private val provider = classLoader.loadClass("androidx.core.view.MenuProvider")
    private val lifecycleOwner = classLoader.loadClass("androidx.lifecycle.LifecycleOwner")
    private val state = classLoader.loadClass("androidx.lifecycle.Lifecycle\$State")
    private val requireActivity = fragment.getMethod("requireActivity")
    private val addProvider = classLoader.loadClass("androidx.activity.ComponentActivity")
        .getMethod("addMenuProvider", provider, lifecycleOwner, state)
    private val resumed = state.getField("RESUMED").get(null)

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
                "onCreateMenu" -> onCreate(args[0] as Menu)
                "onMenuItemSelected" -> onSelect(args[0] as MenuItem)
                "equals" -> proxy === args[0]
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "Pixel Modes Evolved menu"
                else -> null
            }
        }
        addProvider.invoke(activity(page), menu, page, resumed)
    }
}
