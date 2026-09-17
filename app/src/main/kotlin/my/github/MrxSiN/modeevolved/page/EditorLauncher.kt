package my.github.MrxSiN.modeevolved.page

import android.content.Context
import android.content.Intent
import java.util.concurrent.Executors

import my.github.MrxSiN.modeevolved.editor.EditorInput
import my.github.MrxSiN.modeevolved.editor.EditorIntents
import my.github.MrxSiN.modeevolved.editor.ItemFamily

/**
 * Opens the editor from a Mode page.
 *
 * Started for a result, so the answer comes back to the same Settings
 * activity, where [EditorResultHooks] stores it before the page resumes and
 * redraws its rows. The device facts the editor offers are read off the main
 * thread, so the Mode page stays responsive while they load.
 */
class EditorLauncher {

    private val reader = Executors.newSingleThreadExecutor()

    /**
     * @param context the page's context; the activity behind it starts the editor.
     * @param original the item to edit, or null to add one.
     */
    fun <I : Any> open(context: Context, family: ItemFamily<I, *>, mode: ModeRef, original: I?) {
        val activity = context.activity() ?: return
        reader.execute {
            val input = EditorInput(mode.id, mode.name, original, DeviceFacts(activity).collect(family.subject))
            val intent = Intent()
                .setClassName(EditorIntents.PACKAGE, EditorIntents.ACTIVITY)
                .putExtra(EditorIntents.EXTRA_SUBJECT, family.subject.name)
                .putExtra(EditorIntents.EXTRA_INPUT, family.codec.encodeInput(input))
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed) activity.startActivityForResult(intent, family.subject.requestCode)
            }
        }
    }
}
