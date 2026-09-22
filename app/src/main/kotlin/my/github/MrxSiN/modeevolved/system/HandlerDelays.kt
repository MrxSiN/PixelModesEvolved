package my.github.MrxSiN.modeevolved.system

import android.os.Handler

import my.github.MrxSiN.modeevolved.engine.Delays

/** [Delays] on the automation thread's handler, so delayed work lands where every other decision runs. */
class HandlerDelays(private val handler: Handler) : Delays {

    override fun after(millis: Long, task: () -> Unit): AutoCloseable {
        val runnable = Runnable(task)
        handler.postDelayed(runnable, millis)
        return AutoCloseable { handler.removeCallbacks(runnable) }
    }
}
