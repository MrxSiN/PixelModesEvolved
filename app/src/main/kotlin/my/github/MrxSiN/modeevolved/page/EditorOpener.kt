package my.github.MrxSiN.modeevolved.page

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle
import java.util.concurrent.Executors

import my.github.MrxSiN.modeevolved.core.LatestOnly

/**
 * Opens one of this module's screens from a Settings page.
 *
 * What a screen is handed (saved networks, paired devices, upcoming events) is read
 * off the main thread, so the page stays responsive while it loads. Only the newest
 * request is carried out: tapping a second row while the first is still loading
 * opens the second, once, instead of both one after the other.
 *
 * Every launch shares Settings' identity with the screen it opens, which is what
 * lets that screen tell Settings from any other caller ([my.github.MrxSiN.modeevolved.security.SettingsCaller]).
 */
class EditorOpener {

    private val reader = LatestOnly(Executors.newSingleThreadExecutor())

    /**
     * @param context the page's context; the activity behind it starts the screen.
     * @param requestCode the code its answer comes back under, or null for a screen that answers nothing.
     * @param prepare builds the intent, off the main thread.
     */
    fun open(context: Context, requestCode: Int?, prepare: (Activity) -> Intent) {
        val activity = context.activity() ?: return
        reader.submit { isLatest ->
            val intent = prepare(activity)
            activity.runOnUiThread {
                if (!isLatest() || activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                if (requestCode == null) {
                    activity.startActivity(intent, sharedIdentity())
                } else {
                    activity.startActivityForResult(intent, requestCode, sharedIdentity())
                }
            }
        }
    }

    private companion object {
        fun sharedIdentity(): Bundle = ActivityOptions.makeBasic().setShareIdentityEnabled(true).toBundle()
    }
}
