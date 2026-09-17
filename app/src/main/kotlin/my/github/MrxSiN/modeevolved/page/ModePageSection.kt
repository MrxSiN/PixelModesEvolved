package my.github.MrxSiN.modeevolved.page

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable

/** The Mode a page shows. */
data class ModeRef(val id: String, val name: String)

/** One part this module adds to a Mode's page. */
interface ModePageSection {

    /** Brings this section on [screen] up to date for [mode]. Called on every page refresh. */
    fun render(screen: Any, mode: ModeRef)
}

/**
 * This module's strings and icons, for pages drawn in the Settings app.
 *
 * The page's own context resolves Settings resources, so everything this
 * module shows there is read through its own [Resources].
 */
class ModuleText(private val resources: Resources) {

    val raw: Resources get() = resources

    fun string(id: Int): String = resources.getString(id)

    fun icon(context: Context, id: Int?): Drawable? =
        id?.let { runCatching { resources.getDrawable(it, context.theme) }.getOrNull() }
}
