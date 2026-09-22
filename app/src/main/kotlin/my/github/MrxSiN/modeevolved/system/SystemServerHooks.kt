package my.github.MrxSiN.modeevolved.system

import android.annotation.SuppressLint
import android.content.Context

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.ExceptionMode

import my.github.MrxSiN.modeevolved.core.Feature
import my.github.MrxSiN.modeevolved.core.FeatureInstaller
import my.github.MrxSiN.modeevolved.dex.ClassFinder

/**
 * Where this module enters system_server.
 *
 * The hook is protective: if it throws, the framework runs the original
 * method, so a changed Android build loses a feature instead of booting into a loop.
 *
 * @param onBootCompleted receives the system context once, when boot completes.
 */
// Hooking system_server internals is this module's purpose.
@SuppressLint("PrivateApi")
class SystemServerHooks(
    private val xposed: XposedInterface,
    private val classes: ClassFinder,
    private val features: FeatureInstaller,
    private val onBootCompleted: (Context) -> Unit,
) {

    fun install() {
        features.install(Feature.AUTOMATION_START, ::hookBootCompleted)
    }

    /**
     * Starts automation when NotificationManagerService reaches PHASE_BOOT_COMPLETED.
     *
     * By then Wi-Fi, Bluetooth, location and sensors are all up, and the
     * notification service can take mode changes.
     */
    private fun hookBootCompleted() {
        val service = classes.load(NOTIFICATION_SERVICE)
        val onBootPhase = classes.member("NotificationManagerService.onBootPhase(int)") {
            service.getDeclaredMethod("onBootPhase", Int::class.javaPrimitiveType)
        }
        var started = false

        xposed.hook(onBootPhase).setExceptionMode(ExceptionMode.PROTECTIVE).intercept { chain ->
            val result = chain.proceed()
            if (!started && chain.args[0] == PHASE_BOOT_COMPLETED) {
                started = true
                val context = classes.member("NotificationManagerService.getContext()") {
                    chain.thisObject.javaClass.getMethod("getContext")
                }.invoke(chain.thisObject) as Context
                onBootCompleted(context)
            }
            result
        }
    }

    private companion object {
        const val NOTIFICATION_SERVICE = "com.android.server.notification.NotificationManagerService"

        /** `SystemService.PHASE_BOOT_COMPLETED`. */
        const val PHASE_BOOT_COMPLETED = 1000
    }
}
