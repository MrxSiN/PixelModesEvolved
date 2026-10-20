package my.github.MrxSiN.modeevolved.compat

import my.github.MrxSiN.modeevolved.compat.Sdk.AUTOMATIC_ZEN_RULE
import my.github.MrxSiN.modeevolved.compat.Sdk.BOOLEAN
import my.github.MrxSiN.modeevolved.compat.Sdk.BUNDLE
import my.github.MrxSiN.modeevolved.compat.Sdk.CHAR_SEQUENCE
import my.github.MrxSiN.modeevolved.compat.Sdk.COLLECTION
import my.github.MrxSiN.modeevolved.compat.Sdk.CONTEXT
import my.github.MrxSiN.modeevolved.compat.Sdk.DRAWABLE
import my.github.MrxSiN.modeevolved.compat.Sdk.INT
import my.github.MrxSiN.modeevolved.compat.Sdk.INTENT
import my.github.MrxSiN.modeevolved.compat.Sdk.MENU
import my.github.MrxSiN.modeevolved.compat.Sdk.MENU_INFLATER
import my.github.MrxSiN.modeevolved.compat.Sdk.MENU_ITEM
import my.github.MrxSiN.modeevolved.compat.Sdk.STRING
import my.github.MrxSiN.modeevolved.compat.Sdk.URI

/**
 * Every private member of the Settings process this module reaches for, as read off
 * `CP3A.260905.009`.
 *
 * This is the only place their names are written. Hook code names these values and
 * the resolver (`HostResolver`) looks them up, so a signature changes here once and
 * the fixtures under `src/test/resources/hosts/` say at once which build it breaks.
 * Which feature needs which is [FeatureContracts].
 *
 * Declared in dependency order: an object's properties initialise top to bottom, so a
 * value may only name the values above it.
 */
object SettingsApis {

    private const val MODES = "com.android.settings.notification.modes"
    private const val SETTINGSLIB_MODES = "com.android.settingslib.notification.modes"
    private const val SETTINGSLIB_WIDGET = "com.android.settingslib.widget"
    private const val PREFERENCE_PACKAGE = "androidx.preference"

    private fun type(name: String, locator: Locator = Locator.ByName) = ClassApi(name, Host.SETTINGS, locator)

    // androidx.preference inside Settings: its own copy, which rows must be built from.

    val PREFERENCE = type("$PREFERENCE_PACKAGE.Preference")
    val PREFERENCE_GROUP = type("$PREFERENCE_PACKAGE.PreferenceGroup")
    val PREFERENCE_CATEGORY = type("$PREFERENCE_PACKAGE.PreferenceCategory")
    val PREFERENCE_MANAGER = type("$PREFERENCE_PACKAGE.PreferenceManager")
    val PREFERENCE_VIEW_HOLDER = type("$PREFERENCE_PACKAGE.PreferenceViewHolder")
    val CLICK_LISTENER = type("$PREFERENCE_PACKAGE.Preference\$OnPreferenceClickListener")

    val PREFERENCE_NEW = ConstructorApi(PREFERENCE, listOf(CONTEXT))
    val CATEGORY_NEW = ConstructorApi(PREFERENCE_CATEGORY, listOf(CONTEXT))
    val PREFERENCE_GET_KEY = MethodApi(PREFERENCE, "getKey")
    val PREFERENCE_GET_CONTEXT = MethodApi(PREFERENCE, "getContext")
    val PREFERENCE_GET_ORDER = MethodApi(PREFERENCE, "getOrder")
    val PREFERENCE_GET_MANAGER = MethodApi(PREFERENCE, "getPreferenceManager")
    val PREFERENCE_SET_KEY = MethodApi(PREFERENCE, "setKey", listOf(STRING))
    val PREFERENCE_SET_TITLE = MethodApi(PREFERENCE, "setTitle", listOf(CHAR_SEQUENCE))
    val PREFERENCE_SET_SUMMARY = MethodApi(PREFERENCE, "setSummary", listOf(CHAR_SEQUENCE))
    val PREFERENCE_SET_ICON = MethodApi(PREFERENCE, "setIcon", listOf(DRAWABLE))
    val PREFERENCE_SET_ORDER = MethodApi(PREFERENCE, "setOrder", listOf(INT))
    val PREFERENCE_SET_PERSISTENT = MethodApi(PREFERENCE, "setPersistent", listOf(BOOLEAN))
    val PREFERENCE_SET_ICON_SPACE = MethodApi(PREFERENCE, "setIconSpaceReserved", listOf(BOOLEAN))
    val PREFERENCE_SET_SELECTABLE = MethodApi(PREFERENCE, "setSelectable", listOf(BOOLEAN))
    val PREFERENCE_SET_ON_CLICK = MethodApi(PREFERENCE, "setOnPreferenceClickListener", listOf(CLICK_LISTENER))
    val PREFERENCE_NOTIFY_CHANGED = MethodApi(PREFERENCE, "notifyChanged")
    val CLICK_LISTENER_ON_CLICK = MethodApi(CLICK_LISTENER, "onPreferenceClick", listOf(PREFERENCE))
    val GROUP_ADD = MethodApi(PREFERENCE_GROUP, "addPreference", listOf(PREFERENCE))
    val GROUP_REMOVE = MethodApi(PREFERENCE_GROUP, "removePreference", listOf(PREFERENCE))
    val GROUP_FIND = MethodApi(PREFERENCE_GROUP, "findPreference", listOf(CHAR_SEQUENCE))
    val GROUP_COUNT = MethodApi(PREFERENCE_GROUP, "getPreferenceCount")
    val GROUP_GET = MethodApi(PREFERENCE_GROUP, "getPreference", listOf(INT))
    val MANAGER_GET_SCREEN = MethodApi(PREFERENCE_MANAGER, "getPreferenceScreen")
    val VIEW_HOLDER_ITEM_VIEW = FieldApi(PREFERENCE_VIEW_HOLDER, "itemView")

    // The Modes pages.

    /** The page each Mode opens, whose refreshes this module draws its rows after. */
    // Its shape is what the hooks use of it; its own base declares the first two as well.
    val MODE_FRAGMENT = type("$MODES.ZenModeFragment", Locator.DexSearch(MODES, listOf("onUpdatedZenModeState", "getMode", "onResume")))
    val MODE_FRAGMENT_ON_RESUME = MethodApi(MODE_FRAGMENT, "onResume", scope = Scope.HOOK)
    val MODE_FRAGMENT_ON_UPDATED = MethodApi(MODE_FRAGMENT, "onUpdatedZenModeState", scope = Scope.HOOK)
    val MODE_FRAGMENT_GET_MODE = MethodApi(MODE_FRAGMENT, "getMode")
    val MODE_FRAGMENT_GET_SCREEN = MethodApi(MODE_FRAGMENT, "getPreferenceScreen")

    /** The base every single Mode's page shares, the schedule pages included. Found from the Mode page when renamed. */
    val MODE_FRAGMENT_BASE = type(
        "$MODES.ZenModeFragmentBase",
        Locator.Ancestor(MODE_FRAGMENT, declaring = "onCreate", parameters = listOf(BUNDLE), sharing = "getMode"),
    )
    val MODE_FRAGMENT_BASE_ON_CREATE = MethodApi(MODE_FRAGMENT_BASE, "onCreate", listOf(BUNDLE), Scope.HOOK)
    val MODE_FRAGMENT_BASE_GET_MODE = MethodApi(MODE_FRAGMENT_BASE, "getMode")

    /**
     * The base every Modes page shares, including the list. Every Modes page declares
     * the refreshes; only the base declares `setBackend`, which keeps the search to one class.
     */
    val MODES_FRAGMENT_BASE = type("$MODES.ZenModesFragmentBase", Locator.DexSearch(MODES, listOf("onUpdatedZenModeState", "onStart", "setBackend")))
    val MODES_FRAGMENT_BASE_ON_START = MethodApi(MODES_FRAGMENT_BASE, "onStart", scope = Scope.HOOK)
    val MODES_FRAGMENT_BASE_GET_SCREEN = MethodApi(MODES_FRAGMENT_BASE, "getPreferenceScreen")

    /** The stock schedule pages that get a Remove schedule button. */
    val SCHEDULE_TIME_FRAGMENT = type("$MODES.ZenModeSetScheduleFragment")
    val SCHEDULE_CALENDAR_FRAGMENT = type("$MODES.ZenModeSetCalendarFragment")

    val CALENDAR_CONTROLLER = type("$MODES.ZenModeSetCalendarPreferenceController")

    /** The icons offered when a Mode's icon is changed: framework drawables, read from a Settings array. */
    val ICON_OPTIONS = type("$MODES.IconOptionsProviderImpl")
    val ICON_OPTIONS_CONTEXT = FieldApi(ICON_OPTIONS, "mContext")
    val ICON_OPTIONS_GET_ICONS = MethodApi(ICON_OPTIONS, "getIcons", scope = Scope.HOOK)
    val ICON_INFO = type("$MODES.IconOptionsProvider\$IconInfo")
    val ICON_INFO_NEW = ConstructorApi(ICON_INFO, listOf(INT, STRING))
    val ICON_INFO_RES_ID = MethodApi(ICON_INFO, "resId")
    val IMMUTABLE_LIST = type("com.google.common.collect.ImmutableList")
    val IMMUTABLE_LIST_COPY_OF = MethodApi(IMMUTABLE_LIST, "copyOf", listOf(COLLECTION))

    // SettingsLib's Mode, the value every page hands around.

    val ZEN_MODE = type("$SETTINGSLIB_MODES.ZenMode", Locator.DexSearch(SETTINGSLIB_MODES, listOf("setCustomModeConditionId", "getRule")))
    val ZEN_MODE_GET_ID = MethodApi(ZEN_MODE, "getId")
    val ZEN_MODE_GET_NAME = MethodApi(ZEN_MODE, "getName")
    val ZEN_MODE_GET_RULE = MethodApi(ZEN_MODE, "getRule")
    val ZEN_MODE_SET_CONDITION = MethodApi(ZEN_MODE, "setCustomModeConditionId", listOf(CONTEXT, URI))

    val CALENDAR_CONTROLLER_UPDATE_STATE = MethodApi(
        CALENDAR_CONTROLLER,
        "updateState",
        listOf(PREFERENCE, ZEN_MODE),
        Scope.HOOK,
    )

    // The framework, as Settings (the system uid) sees it.

    val AUTOMATIC_ZEN_RULE_TYPE = type(AUTOMATIC_ZEN_RULE.name)

    /** Hidden: the app that owns the rule. */
    val AUTOMATIC_ZEN_RULE_GET_PACKAGE = MethodApi(AUTOMATIC_ZEN_RULE_TYPE, "getPackageName")

    val ZEN_MODE_CONFIG = type("android.service.notification.ZenModeConfig")
    val ZEN_MODE_CONFIG_MANUAL_CONDITION = MethodApi(ZEN_MODE_CONFIG, "toCustomManualConditionId")

    val NOTIFICATION_MANAGER = type("android.app.NotificationManager")

    /** The hidden overload that marks a change as the person's own; the public one is its fallback. */
    val NOTIFICATION_MANAGER_UPDATE_RULE = MethodApi(
        NOTIFICATION_MANAGER,
        "updateAutomaticZenRule",
        listOf(STRING, AUTOMATIC_ZEN_RULE, BOOLEAN),
        fallbacks = listOf(MethodApi(NOTIFICATION_MANAGER, "updateAutomaticZenRule", listOf(STRING, AUTOMATIC_ZEN_RULE))),
    )

    val APPLICATION = type("android.app.Application")
    val APPLICATION_ON_CREATE = MethodApi(APPLICATION, "onCreate", scope = Scope.HOOK)

    val SETTINGS_ACTIVITY = type("com.android.settings.SettingsActivity")
    val SETTINGS_ACTIVITY_ON_RESULT = MethodApi(SETTINGS_ACTIVITY, "onActivityResult", listOf(INT, INT, INTENT), Scope.HOOK)

    // SettingsLib's Material 3 Expressive connected button group, which draws AND and OR.

    val SEGMENTED_BUTTON = type(
        "$SETTINGSLIB_WIDGET.SegmentedButtonPreference",
        Locator.DexSearch(SETTINGSLIB_WIDGET, listOf("setUpButton", "setCheckedIndex")),
    )
    val BUTTON_CHECKED_LISTENER = type("com.google.android.material.button.MaterialButtonToggleGroup\$OnButtonCheckedListener")
    val MATERIAL_BUTTON = type("com.google.android.material.button.MaterialButton")
    val TOGGLE_GROUP = type("com.google.android.material.button.MaterialButtonToggleGroup")

    val SEGMENTED_BUTTON_NEW = ConstructorApi(SEGMENTED_BUTTON, listOf(CONTEXT))
    val SEGMENTED_BUTTON_SET_UP = MethodApi(SEGMENTED_BUTTON, "setUpButton", listOf(INT, STRING, DRAWABLE))
    val SEGMENTED_BUTTON_SET_VISIBILITY = MethodApi(SEGMENTED_BUTTON, "setButtonVisibility", listOf(INT, BOOLEAN))
    val SEGMENTED_BUTTON_SET_CHECKED = MethodApi(SEGMENTED_BUTTON, "setCheckedIndex", listOf(INT))
    val SEGMENTED_BUTTON_SET_LISTENER = MethodApi(SEGMENTED_BUTTON, "setOnButtonClickListener", listOf(BUTTON_CHECKED_LISTENER))
    val SEGMENTED_BUTTON_ON_BIND = MethodApi(SEGMENTED_BUTTON, "onBindViewHolder", listOf(PREFERENCE_VIEW_HOLDER), Scope.HOOK)
    val BUTTON_CHECKED = MethodApi(BUTTON_CHECKED_LISTENER, "onButtonChecked", listOf(TOGGLE_GROUP, INT, BOOLEAN))
    val MATERIAL_BUTTON_SET_ICON = MethodApi(MATERIAL_BUTTON, "setIcon", listOf(DRAWABLE))

    val GROUP_ADAPTER = type("$SETTINGSLIB_WIDGET.SettingsPreferenceGroupAdapter")
    val GROUP_ADAPTER_IS_DIVIDER = MethodApi(GROUP_ADAPTER, "isGroupDivider", listOf(PREFERENCE), Scope.HOOK)

    // androidx menus and lifecycles inside Settings, for the app bar of a page.

    val FRAGMENT = type("androidx.fragment.app.Fragment")
    val COMPONENT_ACTIVITY = type("androidx.activity.ComponentActivity")
    val MENU_PROVIDER = type("androidx.core.view.MenuProvider")
    val LIFECYCLE_OWNER = type("androidx.lifecycle.LifecycleOwner")
    val LIFECYCLE_STATE = type("androidx.lifecycle.Lifecycle\$State")

    val FRAGMENT_REQUIRE_ACTIVITY = MethodApi(FRAGMENT, "requireActivity")
    val ADD_MENU_PROVIDER = MethodApi(COMPONENT_ACTIVITY, "addMenuProvider", listOf(MENU_PROVIDER, LIFECYCLE_OWNER, LIFECYCLE_STATE))
    val MENU_PROVIDER_ON_CREATE = MethodApi(MENU_PROVIDER, "onCreateMenu", listOf(MENU, MENU_INFLATER))
    val MENU_PROVIDER_ON_SELECT = MethodApi(MENU_PROVIDER, "onMenuItemSelected", listOf(MENU_ITEM))
    val LIFECYCLE_RESUMED = FieldApi(LIFECYCLE_STATE, "RESUMED")
}
