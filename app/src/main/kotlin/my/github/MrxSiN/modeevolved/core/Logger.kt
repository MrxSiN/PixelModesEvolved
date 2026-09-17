package my.github.MrxSiN.modeevolved.core

import android.util.Log

/**
 * Sink for diagnostics.
 *
 * Everything else depends on this contract rather than on logcat, so the
 * deciding code stays testable on a plain JVM.
 */
interface Logger {
    fun info(message: String)
    fun warn(message: String, error: Throwable? = null)
}

/** [Logger] backed by logcat, readable with `adb logcat -s PixelModesEvolved`. */
object AndroidLogger : Logger {

    private const val TAG = "PixelModesEvolved"

    override fun info(message: String) {
        Log.i(TAG, message)
    }

    override fun warn(message: String, error: Throwable?) {
        if (error == null) Log.w(TAG, message) else Log.w(TAG, message, error)
    }
}
