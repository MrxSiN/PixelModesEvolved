package my.github.MrxSiN.modeevolved.page

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import java.lang.reflect.Method

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.ExceptionMode

import my.github.MrxSiN.modeevolved.compat.SettingsApis as S
import my.github.MrxSiN.modeevolved.core.Feature
import my.github.MrxSiN.modeevolved.core.FeatureHooks
import my.github.MrxSiN.modeevolved.core.Logger
import my.github.MrxSiN.modeevolved.dex.HostResolver

/**
 * Draws this module's connected button groups as text buttons inside the card.
 *
 * SettingsLib's `SegmentedButtonPreference` shows icon buttons with their
 * labels in a row underneath. For a short word such as AND or OR the label
 * belongs inside the button, so after each bind of one of this module's rows
 * the label moves in, the icon goes and the row of labels is hidden.
 *
 * The preference also marks itself a section divider, which makes the
 * expressive list adapter end the rounded card above it and start a new one
 * below. Between two triggers of one rule it is part of that rule, so for these
 * rows the adapter is told it is not a divider, and it draws the row as a
 * middle piece of the same card.
 *
 * Rows belonging to Settings itself are left as they are.
 *
 * @param keyPrefix keys of the rows this module owns.
 */
// SettingsLib's view ids are merged into the Settings package and only reachable by name.
@SuppressLint("DiscouragedApi")
class ButtonGroupHooks(
    private val xposed: XposedInterface,
    private val apis: HostResolver,
    private val keyPrefix: String,
    private val logger: Logger,
) : FeatureHooks {

    override val feature = Feature.BUTTON_GROUP

    private lateinit var getKey: Method

    override fun install() {
        getKey = apis.method(S.PREFERENCE_GET_KEY)
        hookBinding()
        hookGrouping()
    }

    private fun hookGrouping() {
        val segmented = apis.type(S.SEGMENTED_BUTTON)

        xposed.hook(apis.method(S.GROUP_ADAPTER_IS_DIVIDER)).setExceptionMode(ExceptionMode.PROTECTIVE).intercept { chain ->
            val preference = chain.args[0]
            if (segmented.isInstance(preference) && isOurs(preference)) false else chain.proceed()
        }
    }

    private fun hookBinding() {
        val itemView = apis.field(S.VIEW_HOLDER_ITEM_VIEW)
        val setIcon = apis.method(S.MATERIAL_BUTTON_SET_ICON)

        xposed.hook(apis.method(S.SEGMENTED_BUTTON_ON_BIND)).setExceptionMode(ExceptionMode.PROTECTIVE).intercept { chain ->
            val result = chain.proceed()
            if (isOurs(chain.thisObject)) {
                runCatching { labelsInside(itemView.get(chain.args[0]) as View) { setIcon.invoke(it, null) } }
                    .onFailure { logger.warn("Could not restyle a button group", it) }
            }
            result
        }
    }

    private fun isOurs(preference: Any?): Boolean = (getKey.invoke(preference) as String?)?.startsWith(keyPrefix) == true

    private fun labelsInside(row: View, removeIcon: (Button) -> Unit) {
        val group = row.findViewById<ViewGroup>(id(row, "button_group")) ?: return
        for (index in 0 until group.childCount) {
            val button = group.getChildAt(index) as? Button ?: continue
            // SettingsLib sets each button's content description to its label.
            button.text = button.contentDescription
            removeIcon(button)
        }
        row.findViewById<View>(id(row, "button_group_text"))?.visibility = View.GONE

        // The hidden labels took the space under the buttons; as a card row it needs room on both sides.
        // The adapter keeps vertical padding when it sets the card background afterwards.
        val vertical = (VERTICAL_PADDING_DP * row.resources.displayMetrics.density).toInt()
        row.setPaddingRelative(row.paddingStart, vertical, row.paddingEnd, vertical)
    }

    /** SettingsLib view ids by name, looked up once: they cannot change while the process runs. */
    private val ids = HashMap<String, Int>()

    /** A SettingsLib view id, merged into the Settings package at build time. */
    private fun id(view: View, name: String): Int =
        ids.getOrPut(name) { view.resources.getIdentifier(name, "id", view.context.packageName) }

    private companion object {
        const val VERTICAL_PADDING_DP = 12
    }
}
