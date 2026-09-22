package my.github.MrxSiN.modeevolved.page

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import java.lang.reflect.Proxy

import my.github.MrxSiN.modeevolved.dex.ClassFinder
import my.github.MrxSiN.modeevolved.dex.SettingsClasses

/** One row this module adds to a preference group. */
sealed interface PreferenceRow {

    val key: String

    /** A tappable row with a title, summary and icon. */
    data class Link(
        override val key: String,
        val title: CharSequence,
        val summary: CharSequence?,
        val icon: Drawable?,
        val iconSpaceReserved: Boolean = icon != null,
        val onClick: () -> Unit,
    ) : PreferenceRow

    /** A row that only says something: no tap target, no ripple. */
    data class Info(
        override val key: String,
        val title: CharSequence,
        val summary: CharSequence?,
        val icon: Drawable?,
    ) : PreferenceRow

    /**
     * A connected button group with one option selected.
     *
     * @param onChoose called with the index a person selects.
     */
    data class Choice(
        override val key: String,
        val options: List<Option>,
        val selected: Int,
        val onChoose: (Int) -> Unit,
    ) : PreferenceRow

    data class Option(val label: String)
}

/**
 * The Settings app's own androidx.preference and SettingsLib classes, reached by reflection.
 *
 * This module cannot link against the copies inside the Settings APK, and a
 * copy of its own would be a different class the Settings adapter cannot draw.
 * Plain rows are required, so their lookups happen at construction and a
 * missing member fails the page feature up front. The button group is looked
 * up separately: without it, [supportsChoices] is false and callers fall back.
 */
class PreferenceApi(classes: ClassFinder) {

    private val classLoader = classes.classLoader
    private val preference = classes.load("androidx.preference.Preference")
    private val group = classes.load("androidx.preference.PreferenceGroup")
    private val clickListener = classes.load("androidx.preference.Preference\$OnPreferenceClickListener")
    private val category = classes.load("androidx.preference.PreferenceCategory")

    private val constructor = classes.member("Preference(Context)") { preference.getConstructor(Context::class.java) }
    private val categoryConstructor = classes.member("PreferenceCategory(Context)") { category.getConstructor(Context::class.java) }
    private val getKey = classes.member("Preference.getKey()") { preference.getMethod("getKey") }
    private val getContext = classes.member("Preference.getContext()") { preference.getMethod("getContext") }
    private val getOrder = classes.member("Preference.getOrder()") { preference.getMethod("getOrder") }
    private val getPreferenceManager = classes.member("Preference.getPreferenceManager()") { preference.getMethod("getPreferenceManager") }
    private val setKey = classes.member("Preference.setKey(String)") { preference.getMethod("setKey", String::class.java) }
    private val setTitle = classes.member("Preference.setTitle(CharSequence)") { preference.getMethod("setTitle", CharSequence::class.java) }
    private val setSummary = classes.member("Preference.setSummary(CharSequence)") { preference.getMethod("setSummary", CharSequence::class.java) }
    private val setIcon = classes.member("Preference.setIcon(Drawable)") { preference.getMethod("setIcon", Drawable::class.java) }
    private val setOrder = classes.member("Preference.setOrder(int)") { preference.getMethod("setOrder", Int::class.javaPrimitiveType) }
    private val setPersistent = classes.member("Preference.setPersistent(boolean)") { preference.getMethod("setPersistent", Boolean::class.javaPrimitiveType) }
    private val setIconSpaceReserved = classes.member("Preference.setIconSpaceReserved(boolean)") { preference.getMethod("setIconSpaceReserved", Boolean::class.javaPrimitiveType) }
    private val setSelectable = classes.member("Preference.setSelectable(boolean)") { preference.getMethod("setSelectable", Boolean::class.javaPrimitiveType) }
    private val setOnClick = classes.member("Preference.setOnPreferenceClickListener") { preference.getMethod("setOnPreferenceClickListener", clickListener) }
    private val notifyChanged = classes.member("Preference.notifyChanged()") { preference.getDeclaredMethod("notifyChanged").apply { isAccessible = true } }
    private val addPreference = classes.member("PreferenceGroup.addPreference(Preference)") { group.getMethod("addPreference", preference) }
    private val removePreference = classes.member("PreferenceGroup.removePreference(Preference)") { group.getMethod("removePreference", preference) }
    private val findPreference = classes.member("PreferenceGroup.findPreference(CharSequence)") { group.getMethod("findPreference", CharSequence::class.java) }
    private val getCount = classes.member("PreferenceGroup.getPreferenceCount()") { group.getMethod("getPreferenceCount") }
    private val getAt = classes.member("PreferenceGroup.getPreference(int)") { group.getMethod("getPreference", Int::class.javaPrimitiveType) }

    private val buttonGroup: ButtonGroupApi? = runCatching { ButtonGroupApi(classes) }.getOrNull()
    /** Whether this Settings build has the expressive connected button group. */
    val supportsChoices: Boolean get() = buttonGroup != null

    fun find(group: Any, key: String): Any? = findPreference.invoke(group, key)

    fun context(preference: Any): Context = getContext.invoke(preference) as Context

    /** The screen [preference] is shown on, or null while it is not attached to one. */
    fun screen(preference: Any): Any? {
        val manager = getPreferenceManager.invoke(preference) ?: return null
        return manager.javaClass.getMethod("getPreferenceScreen").invoke(manager)
    }

    /**
     * The category [key] on [screen], created right after the preference [afterKey] when missing.
     * Null when [afterKey] is not on the screen either.
     */
    fun ensureCategory(screen: Any, key: String, title: CharSequence, afterKey: String): Any? {
        find(screen, key)?.let { return it }
        val after = find(screen, afterKey) ?: return null
        val afterOrder = getOrder.invoke(after) as Int
        // Make room: everything after the anchor moves down one place. The adapter sorts by order.
        children(screen)
            .filter { (getOrder.invoke(it) as Int) > afterOrder }
            .forEach { setOrder.invoke(it, (getOrder.invoke(it) as Int) + 1) }
        return categoryConstructor.newInstance(context(screen)).also {
            setKey.invoke(it, key)
            setTitle.invoke(it, title)
            setPersistent.invoke(it, false)
            setOrder.invoke(it, afterOrder + 1)
            addPreference.invoke(screen, it)
        }
    }

    /**
     * Puts [row] on [screen] directly after the preference [afterKey], moving what follows down.
     *
     * For a single row that belongs beside one of Settings' own, rather than in a
     * category of this module's. Does nothing when [afterKey] is not on the screen.
     */
    fun putAfter(screen: Any, afterKey: String, row: PreferenceRow) {
        val after = find(screen, afterKey) ?: return
        val afterOrder = getOrder.invoke(after) as Int
        children(screen)
            .filter { (getOrder.invoke(it) as Int) > afterOrder }
            .forEach { setOrder.invoke(it, (getOrder.invoke(it) as Int) + 1) }
        addPreference.invoke(screen, build(context(screen), row, afterOrder + 1))
    }

    /** Replaces every child of [group] whose key starts with [prefix] by [rows], after the existing children. */
    fun replaceRows(group: Any, prefix: String, rows: List<PreferenceRow>) {
        val (ours, theirs) = children(group).partition { (getKey.invoke(it) as String?)?.startsWith(prefix) == true }
        ours.forEach { removePreference.invoke(group, it) }

        var order = theirs.maxOfOrNull { getOrder.invoke(it) as Int }?.plus(1) ?: 0
        for (row in rows) addPreference.invoke(group, build(context(group), row, order++))
        // The expressive theme rounds a row's corners by its place in the group. The adapter
        // recomputes places on a posted hierarchy change and does not rebind rows that stayed,
        // so existing rows are asked to redraw after that has run.
        Handler(Looper.getMainLooper()).post { theirs.forEach { notifyChanged.invoke(it) } }
    }

    private fun children(group: Any): List<Any> = (0 until getCount.invoke(group) as Int).mapNotNull { getAt.invoke(group, it) }

    private fun build(context: Context, row: PreferenceRow, order: Int): Any {
        val built = when (row) {
            is PreferenceRow.Link -> constructor.newInstance(context).also {
                setTitle.invoke(it, row.title)
                setSummary.invoke(it, row.summary)
                setIcon.invoke(it, row.icon)
                setIconSpaceReserved.invoke(it, row.iconSpaceReserved)
                setOnClick.invoke(it, listener(clickListener, "onPreferenceClick") { true.also { row.onClick() } })
            }
            is PreferenceRow.Info -> constructor.newInstance(context).also {
                setTitle.invoke(it, row.title)
                setSummary.invoke(it, row.summary)
                setIcon.invoke(it, row.icon)
                setIconSpaceReserved.invoke(it, row.icon != null)
                setSelectable.invoke(it, false)
            }
            is PreferenceRow.Choice -> checkNotNull(buttonGroup) { "No button group in this Settings build" }.build(context, row)
        }
        setKey.invoke(built, row.key)
        setPersistent.invoke(built, false)
        setOrder.invoke(built, order)
        return built
    }

    /** `com.android.settingslib.widget.SegmentedButtonPreference`, a Material 3 Expressive connected button group. */
    private class ButtonGroupApi(classes: ClassFinder) {
        private val type = classes.find(SettingsClasses.segmentedButton)
        private val checkedListener =
            classes.load("com.google.android.material.button.MaterialButtonToggleGroup\$OnButtonCheckedListener")

        private val constructor = classes.member("SegmentedButtonPreference(Context)") {
            type.getConstructor(Context::class.java)
        }
        private val setUpButton = classes.member("SegmentedButtonPreference.setUpButton(int, String, Drawable)") {
            type.getMethod("setUpButton", Int::class.javaPrimitiveType, String::class.java, Drawable::class.java)
        }
        private val setButtonVisibility = classes.member("SegmentedButtonPreference.setButtonVisibility(int, boolean)") {
            type.getMethod("setButtonVisibility", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
        }
        private val setCheckedIndex = classes.member("SegmentedButtonPreference.setCheckedIndex(int)") {
            type.getMethod("setCheckedIndex", Int::class.javaPrimitiveType)
        }
        private val setOnButtonClickListener = classes.member("SegmentedButtonPreference.setOnButtonClickListener") {
            type.getMethod("setOnButtonClickListener", checkedListener)
        }

        fun build(context: Context, row: PreferenceRow.Choice): Any = constructor.newInstance(context).also { built ->
            // The layout holds four buttons, all hidden until set up. SettingsLib requires an icon;
            // ButtonGroupHooks replaces it with the label when the row is drawn.
            row.options.forEachIndexed { index, option ->
                setUpButton.invoke(built, index, option.label, ColorDrawable(Color.TRANSPARENT))
                setButtonVisibility.invoke(built, index, true)
            }
            for (hidden in row.options.size until MAX_BUTTONS) setButtonVisibility.invoke(built, hidden, false)
            setCheckedIndex.invoke(built, row.selected)

            // onButtonChecked(group, checkedId, isChecked) also fires for the button losing its check.
            val onChecked = listener(checkedListener, "onButtonChecked") { args ->
                if (args[2] == true) {
                    val group = args[0] as ViewGroup
                    val index = group.indexOfChild(group.findViewById(args[1] as Int))
                    if (index >= 0) row.onChoose(index)
                }
                null
            }
            setOnButtonClickListener.invoke(built, onChecked)
        }

        private companion object {
            const val MAX_BUTTONS = 4
        }
    }

    private companion object {

        /** An implementation of the one-method interface [type] that runs [action] when [method] is called. */
        fun listener(type: Class<*>, method: String, action: (Array<Any?>) -> Any?): Any =
            Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { self, called, args ->
                when (called.name) {
                    method -> action(args ?: emptyArray())
                    "equals" -> self === args?.get(0)
                    "hashCode" -> System.identityHashCode(self)
                    else -> type.simpleName
                }
            }
    }
}
