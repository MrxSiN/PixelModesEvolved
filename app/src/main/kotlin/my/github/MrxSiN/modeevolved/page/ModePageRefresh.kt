package my.github.MrxSiN.modeevolved.page

import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Asks a Mode's page to rebuild itself the next time it is shown.
 *
 * Settings decides once, when a Mode page is built, which schedule rows it
 * shows. A change it never makes itself, such as removing a schedule, leaves
 * the returning page showing the old rows until it is rebuilt.
 *
 * Used on the main thread only.
 */
class ModePageRefresh {

    private val pending = mutableSetOf<String>()

    fun request(modeId: String) {
        pending += modeId
    }

    /** Rebuilds the page behind [page] when [modeId] asked for it. */
    fun consume(modeId: String, page: Context) {
        if (!pending.remove(modeId)) return
        val activity = page.activity() ?: return
        // Posted, so the activity finishes resuming before it is recreated.
        Handler(Looper.getMainLooper()).post { if (!activity.isFinishing && !activity.isDestroyed) activity.recreate() }
    }
}
