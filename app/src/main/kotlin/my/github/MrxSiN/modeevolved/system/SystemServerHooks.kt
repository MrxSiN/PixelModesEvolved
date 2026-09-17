package my.github.MrxSiN.modeevolved.system

import android.annotation.SuppressLint
import android.content.Context

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.ExceptionMode

import my.github.MrxSiN.modeevolved.core.Logger

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
    private val classLoader: ClassLoader,
    private val logger: Logger,
    private val onBootCompleted: (Context) -> Unit,
) {

    fun install() {
        guarded("boot completed", ::hookBootCompleted)
    }

    /**
     * Starts automation when NotificationManagerService reaches PHASE_BOOT_COMPLETED.
     *
     * By then Wi-Fi, Bluetooth, location and sensors are all up, and the
     * notification service can take mode changes.
     */
    private fun hookBootCompleted() {
        val service = classLoader.loadClass(NOTIFICATION_SERVICE)
        val onBootPhase = service.getDeclaredMethod("onBootPhase", Int::class.javaPrimitiveType)
        var started = false

        xposed.hook(onBootPhase).setExceptionMode(ExceptionMode.PROTECTIVE).intercept { chain ->
            val result = chain.proceed()
            if (!started && chain.args[0] == PHASE_BOOT_COMPLETED) {
                started = true
                val context = chain.thisObject.javaClass.getMethod("getContext").invoke(chain.thisObject) as Context
                onBootCompleted(context)
            }
            result
        }
    }

    private fun guarded(name: String, install: () -> Unit) {
        runCatching(install)
            .onSuccess { logger.info("Hooked $name") }
            .onFailure { logger.warn("Could not hook $name", it) }
    }

    private companion object {
        const val NOTIFICATION_SERVICE = "com.android.server.notification.NotificationManagerService"

        /** `SystemService.PHASE_BOOT_COMPLETED`. */
        const val PHASE_BOOT_COMPLETED = 1000
    }
}
