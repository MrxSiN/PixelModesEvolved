package my.github.MrxSiN.modeevolved.system

import android.content.Context

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.ExceptionMode

import my.github.MrxSiN.modeevolved.compat.SystemServerApis as SS
import my.github.MrxSiN.modeevolved.core.Feature
import my.github.MrxSiN.modeevolved.core.FeatureHooks
import my.github.MrxSiN.modeevolved.dex.HostResolver

/**
 * Where this module enters system_server: starts automation when NotificationManagerService
 * reaches PHASE_BOOT_COMPLETED. By then Wi-Fi, Bluetooth, location and sensors are all up,
 * and the notification service can take mode changes.
 *
 * The hook is protective: if it throws, the framework runs the original
 * method, so a changed Android build loses a feature instead of booting into a loop.
 *
 * @param onBootCompleted receives the system context once, when boot completes.
 */
class SystemServerHooks(
    private val xposed: XposedInterface,
    private val apis: HostResolver,
    private val onBootCompleted: (Context) -> Unit,
) : FeatureHooks {

    override val feature = Feature.AUTOMATION_START

    override fun install() {
        val getContext = apis.method(SS.NOTIFICATION_SERVICE_GET_CONTEXT)
        var started = false

        xposed.hook(apis.method(SS.NOTIFICATION_SERVICE_ON_BOOT_PHASE)).setExceptionMode(ExceptionMode.PROTECTIVE).intercept { chain ->
            val result = chain.proceed()
            if (!started && chain.args[0] == PHASE_BOOT_COMPLETED) {
                started = true
                onBootCompleted(getContext.invoke(chain.thisObject) as Context)
            }
            result
        }
    }

    private companion object {
        /** `SystemService.PHASE_BOOT_COMPLETED`. */
        const val PHASE_BOOT_COMPLETED = 1000
    }
}
