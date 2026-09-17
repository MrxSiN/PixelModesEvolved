package my.github.MrxSiN.modeevolved.page

import android.app.Application

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.ExceptionMode

import my.github.MrxSiN.modeevolved.core.Logger

/**
 * Calls [onStarted] once the hooked app's [Application] is created.
 *
 * Every app's application class calls `Application.onCreate` through `super`,
 * so the hook works whatever the app names its own subclass.
 */
class ApplicationStartHooks(
    private val xposed: XposedInterface,
    private val logger: Logger,
    private val onStarted: (Application) -> Unit,
) {

    fun install() {
        runCatching {
            xposed.hook(Application::class.java.getDeclaredMethod("onCreate")).setExceptionMode(ExceptionMode.PROTECTIVE).intercept { chain ->
                val result = chain.proceed()
                runCatching { onStarted(chain.thisObject as Application) }
                    .onFailure { logger.warn("Could not finish starting in the app", it) }
                result
            }
        }
            .onSuccess { logger.info("Hooked app start") }
            .onFailure { logger.warn("Could not hook app start", it) }
    }
}
