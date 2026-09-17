package my.github.MrxSiN.modeevolved.page

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.Process

import my.github.MrxSiN.modeevolved.core.Logger

/**
 * Brings a new build of this module into the Settings app without a reboot.
 *
 * Hook code loads once, when the Settings process starts, and cannot be
 * swapped inside a running process. So once the module is replaced, or when
 * this process loaded a build that was already replaced, Settings is stopped
 * as soon as none of its screens is visible. The next time it opens, it loads
 * the installed build. Nothing is stopped while a person is looking at Settings.
 *
 * @param loadedApk the module APK this process loaded its hook code from.
 */
class ModuleUpdateRestarter(
    private val packageName: String,
    private val loadedApk: String,
    private val logger: Logger,
) {

    private val handler = Handler(Looper.getMainLooper())
    private var watching = false
    private var pending = false

    /** Starts watching, once per process. Call from the main thread. */
    fun watch(application: Application) {
        if (watching) return
        watching = true

        val filter = IntentFilter(Intent.ACTION_PACKAGE_REPLACED).apply {
            addDataScheme("package")
            addDataSchemeSpecificPart(packageName, 0)
        }
        application.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = restartWhenHidden("module replaced")
            },
            filter,
            Context.RECEIVER_EXPORTED,
        )
        application.registerActivityLifecycleCallbacks(object : ActivityLifecycleAdapter() {
            override fun onActivityStopped(activity: Activity) = checkLater()
        })

        val installed = runCatching { application.packageManager.getApplicationInfo(packageName, 0).sourceDir }.getOrNull()
        if (installed != null && installed != loadedApk) restartWhenHidden("loaded $loadedApk, installed is $installed")
    }

    private fun restartWhenHidden(reason: String) {
        logger.info("Settings restarts once hidden: $reason")
        pending = true
        checkLater()
    }

    /** Waits a moment, so a screen that is only switching to another Settings screen counts as visible. */
    private fun checkLater() {
        if (!pending) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ if (pending && !isVisible()) Process.killProcess(Process.myPid()) }, SETTLE_MILLIS)
    }

    private fun isVisible(): Boolean {
        val state = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(state)
        return state.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
    }

    private companion object {
        const val SETTLE_MILLIS = 1_500L
    }
}
