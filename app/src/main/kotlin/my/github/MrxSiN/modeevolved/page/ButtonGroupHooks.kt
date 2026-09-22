package my.github.MrxSiN.modeevolved.page

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.ExceptionMode

import my.github.MrxSiN.modeevolved.core.Feature
import my.github.MrxSiN.modeevolved.core.FeatureInstaller
import my.github.MrxSiN.modeevolved.core.Logger
import my.github.MrxSiN.modeevolved.dex.ClassFinder
import my.github.MrxSiN.modeevolved.dex.SettingsClasses

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
// Hooking Settings internals is this module's purpose.
@SuppressLint("PrivateApi", "DiscouragedApi")
class ButtonGroupHooks(
    private val xposed: XposedInterface,
    private val classes: ClassFinder,
    private val keyPrefix: String,
    private val features: FeatureInstaller,
    private val logger: Logger,
) {

    fun install() {
        features.install(Feature.BUTTON_GROUP, ::hook)
    }

    private fun hook() {
        hookBinding()
        hookGrouping()
    }

    private fun hookGrouping() {
        val adapter = classes.load(GROUP_ADAPTER)
        val preferenceType = classes.load(PREFERENCE)
        val isGroupDivider = classes.member("PreferenceGroupAdapter.isGroupDivider(Preference)") {
            adapter.getDeclaredMethod("isGroupDivider", preferenceType)
        }
        val getKey = classes.member("Preference.getKey()") { preferenceType.getMethod("getKey") }
        val segmented = classes.find(SettingsClasses.segmentedButton)

        xposed.hook(isGroupDivider).setExceptionMode(ExceptionMode.PROTECTIVE).intercept { chain ->
            val preference = chain.args[0]
            if (segmented.isInstance(preference) && (getKey.invoke(preference) as String?)?.startsWith(keyPrefix) == true) {
                false
            } else {
                chain.proceed()
            }
        }
    }

    private fun hookBinding() {
        val preference = classes.find(SettingsClasses.segmentedButton)
        val holderType = classes.load(VIEW_HOLDER)
        val onBind = classes.member("SegmentedButtonPreference.onBindViewHolder(ViewHolder)") {
            preference.getDeclaredMethod("onBindViewHolder", holderType)
        }
        val getKey = classes.member("Preference.getKey()") { preference.getMethod("getKey") }
        val itemView = classes.member("PreferenceViewHolder.itemView") { holderType.getField("itemView") }
        val setIcon = classes.member("MaterialButton.setIcon(Drawable)") {
            classes.load(MATERIAL_BUTTON).getMethod("setIcon", Drawable::class.java)
        }

        xposed.hook(onBind).setExceptionMode(ExceptionMode.PROTECTIVE).intercept { chain ->
            val result = chain.proceed()
            if ((getKey.invoke(chain.thisObject) as String?)?.startsWith(keyPrefix) == true) {
                runCatching { labelsInside(itemView.get(chain.args[0]) as View) { setIcon.invoke(it, null) } }
                    .onFailure { logger.warn("Could not restyle a button group", it) }
            }
            result
        }
    }

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

    /** A SettingsLib view id, merged into the Settings package at build time. */
    private fun id(view: View, name: String): Int =
        view.resources.getIdentifier(name, "id", view.context.packageName)

    private companion object {
        const val GROUP_ADAPTER = "com.android.settingslib.widget.SettingsPreferenceGroupAdapter"
        const val PREFERENCE = "androidx.preference.Preference"
        const val VIEW_HOLDER = "androidx.preference.PreferenceViewHolder"
        const val MATERIAL_BUTTON = "com.google.android.material.button.MaterialButton"
        const val VERTICAL_PADDING_DP = 12
    }
}
