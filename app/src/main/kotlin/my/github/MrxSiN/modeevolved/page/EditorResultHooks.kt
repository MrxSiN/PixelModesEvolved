package my.github.MrxSiN.modeevolved.page

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.ExceptionMode

import my.github.MrxSiN.modeevolved.calendar.CalendarConditionFilter
import my.github.MrxSiN.modeevolved.calendar.CalendarEventCodec
import my.github.MrxSiN.modeevolved.core.Logger
import my.github.MrxSiN.modeevolved.editor.EditorIntents
import my.github.MrxSiN.modeevolved.editor.EditorResult
import my.github.MrxSiN.modeevolved.editor.EditorSubject
import my.github.MrxSiN.modeevolved.editor.ItemFamilies

/**
 * Stores what the editor returns to a Settings activity.
 *
 * `SettingsActivity.onActivityResult` is where every Settings page receives
 * results. An answer carrying one of this module's request codes is consumed
 * here; every other result passes through untouched. The mode id travels
 * inside the answer, so it survives Settings being restarted meanwhile.
 */
// Hooking Settings internals is this module's purpose.
@SuppressLint("PrivateApi")
class EditorResultHooks(
    private val xposed: XposedInterface,
    private val classLoader: ClassLoader,
    private val logger: Logger,
) {

    fun install() {
        runCatching {
            val activity = classLoader.loadClass(SETTINGS_ACTIVITY)
            val onActivityResult = activity.getDeclaredMethod(
                "onActivityResult",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Intent::class.java,
            )
            xposed.hook(onActivityResult).setExceptionMode(ExceptionMode.PROTECTIVE).intercept { chain ->
                val subject = EditorSubject.ofRequest(chain.args[0] as Int) ?: return@intercept chain.proceed()
                runCatching { store(subject, chain.thisObject as Activity, chain.args[1] as Int, chain.args[2] as Intent?) }
                    .onFailure { logger.warn("Could not store the edited ${subject.name.lowercase()}", it) }
                null
            }
        }
            .onSuccess { logger.info("Hooked editor results") }
            .onFailure { logger.warn("Could not hook editor results", it) }
    }

    private fun store(subject: EditorSubject, activity: Activity, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) return
        val text = data?.getStringExtra(EditorIntents.EXTRA_RESULT) ?: return
        if (subject == EditorSubject.CALENDAR_EVENT) return storeCalendarEvent(activity, text)
        val result = ItemFamilies.of(subject).applyResult(activity.contentResolver, text) ?: return
        // Named explicitly: release builds rename classes, so a class name would read as noise.
        val change = if (result is EditorResult.Remove) "Removed" else "Saved"
        logger.info("$change ${subject.name.lowercase()} for mode ${result.modeId}")
    }

    private fun storeCalendarEvent(activity: Activity, text: String) {
        val result = CalendarEventCodec.decodeResult(text) ?: return
        val rules = ModeRuleWriter(activity)
        val rule = rules.get(result.modeId) ?: return
        val condition = rule.conditionId?.takeIf(CalendarConditionFilter::isEvent) ?: return
        rule.conditionId = CalendarConditionFilter.withFilter(condition, result.filter)
        rules.update(result.modeId, rule)
        logger.info("Saved calendar event for mode ${result.modeId}")
    }

    private companion object {
        const val SETTINGS_ACTIVITY = "com.android.settings.SettingsActivity"
    }
}
