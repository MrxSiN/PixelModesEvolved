package my.github.MrxSiN.modeevolved.page

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle

/** The activity behind a page's context, or null when there is none. */
internal tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** [Application.ActivityLifecycleCallbacks] that ignores every event its subclass does not override. */
internal open class ActivityLifecycleAdapter : Application.ActivityLifecycleCallbacks {
    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}

/** Finishes this activity and runs [action] once it is destroyed. */
internal fun Activity.finishThen(action: () -> Unit) {
    application.registerActivityLifecycleCallbacks(object : ActivityLifecycleAdapter() {
        override fun onActivityDestroyed(activity: Activity) {
            if (activity !== this@finishThen) return
            application.unregisterActivityLifecycleCallbacks(this)
            action()
        }
    })
    finish()
}
