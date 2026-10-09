package my.github.MrxSiN.modeevolved.page

import android.app.Application

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.ExceptionMode

import my.github.MrxSiN.modeevolved.compat.SettingsApis as S
import my.github.MrxSiN.modeevolved.core.Feature
import my.github.MrxSiN.modeevolved.core.FeatureHooks
import my.github.MrxSiN.modeevolved.core.Logger
import my.github.MrxSiN.modeevolved.dex.HostResolver

/**
 * Calls [onStarted] once the hooked app's [Application] is created.
 *
 * Every app's application class calls `Application.onCreate` through `super`,
 * so the hook works whatever the app names its own subclass.
 */
class ApplicationStartHooks(
    private val xposed: XposedInterface,
    private val apis: HostResolver,
    private val logger: Logger,
    private val onStarted: (Application) -> Unit,
) : FeatureHooks {

    override val feature = Feature.SETTINGS_RESTART

    override fun install() {
        xposed.hook(apis.method(S.APPLICATION_ON_CREATE)).setExceptionMode(ExceptionMode.PROTECTIVE).intercept { chain ->
            val result = chain.proceed()
            runCatching { onStarted(chain.thisObject as Application) }
                .onFailure { logger.warn("Could not finish starting in the app", it) }
            result
        }
    }
}
