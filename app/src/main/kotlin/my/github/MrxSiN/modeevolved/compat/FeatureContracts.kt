package my.github.MrxSiN.modeevolved.compat

import my.github.MrxSiN.modeevolved.action.SystemSetting
import my.github.MrxSiN.modeevolved.compat.SettingsApis as S
import my.github.MrxSiN.modeevolved.compat.SystemServerApis as SS
import my.github.MrxSiN.modeevolved.core.Feature

/**
 * What one feature needs from its host.
 *
 * [required] members must all resolve, or the feature is not installed and says
 * which member went. [optional] ones are reported when missing, and the feature
 * works without what they add.
 */
data class FeatureContract(
    val feature: Feature,
    val required: List<HostApi>,
    val optional: List<HostApi> = emptyList(),
) {
    val host: Host get() = feature.host

    val all: List<HostApi> get() = required + optional
}

/**
 * Every feature's contract with its host: the declarative answer to "what does this
 * module rely on, where, and what breaks without it".
 *
 * The runtime installs a feature only when its required members resolve
 * (`FeatureInstaller`), the compatibility page lists these per feature, and
 * `HostCompatibilityTest` checks every one against each recorded Pixel build.
 */
object FeatureContracts {

    // Shared building blocks, each named once.

    /** Rows built from Settings' own androidx.preference classes. */
    private val preferenceRows = listOf(
        S.PREFERENCE, S.PREFERENCE_GROUP, S.PREFERENCE_CATEGORY, S.PREFERENCE_MANAGER, S.CLICK_LISTENER,
        S.PREFERENCE_NEW, S.CATEGORY_NEW,
        S.PREFERENCE_GET_KEY, S.PREFERENCE_GET_CONTEXT, S.PREFERENCE_GET_ORDER, S.PREFERENCE_GET_MANAGER,
        S.PREFERENCE_SET_KEY, S.PREFERENCE_SET_TITLE, S.PREFERENCE_SET_SUMMARY, S.PREFERENCE_SET_ICON,
        S.PREFERENCE_SET_ORDER, S.PREFERENCE_SET_PERSISTENT, S.PREFERENCE_SET_ICON_SPACE, S.PREFERENCE_SET_SELECTABLE,
        S.PREFERENCE_SET_ON_CLICK, S.PREFERENCE_NOTIFY_CHANGED, S.CLICK_LISTENER_ON_CLICK,
        S.GROUP_ADD, S.GROUP_REMOVE, S.GROUP_FIND, S.GROUP_COUNT, S.GROUP_GET, S.MANAGER_GET_SCREEN,
    )

    /** The connected button group for AND and OR. Without it the page offers AND only. */
    private val buttonGroupRows = listOf(
        S.SEGMENTED_BUTTON, S.BUTTON_CHECKED_LISTENER, S.TOGGLE_GROUP,
        S.SEGMENTED_BUTTON_NEW, S.SEGMENTED_BUTTON_SET_UP, S.SEGMENTED_BUTTON_SET_VISIBILITY,
        S.SEGMENTED_BUTTON_SET_CHECKED, S.SEGMENTED_BUTTON_SET_LISTENER, S.BUTTON_CHECKED,
    )

    /** Who a Mode is. */
    private val modeIdentity = listOf(S.ZEN_MODE, S.ZEN_MODE_GET_ID, S.ZEN_MODE_GET_NAME)

    /** Its rule and schedule, for the schedule warning, the calendar row and removing a schedule. */
    private val modeSchedule = listOf(
        S.ZEN_MODE_GET_RULE, S.ZEN_MODE_SET_CONDITION,
        S.AUTOMATIC_ZEN_RULE_TYPE, S.AUTOMATIC_ZEN_RULE_GET_PACKAGE,
        S.ZEN_MODE_CONFIG, S.ZEN_MODE_CONFIG_MANUAL_CONDITION,
    )

    /** Storing a changed rule as Settings' own backend does. */
    private val ruleWriter = listOf(S.NOTIFICATION_MANAGER, S.NOTIFICATION_MANAGER_UPDATE_RULE)

    /** A page's app bar menu. */
    private val appBarMenu = listOf(
        S.FRAGMENT, S.COMPONENT_ACTIVITY, S.MENU_PROVIDER, S.LIFECYCLE_OWNER, S.LIFECYCLE_STATE,
        S.FRAGMENT_REQUIRE_ACTIVITY, S.ADD_MENU_PROVIDER, S.MENU_PROVIDER_ON_CREATE, S.MENU_PROVIDER_ON_SELECT,
        S.LIFECYCLE_RESUMED,
    )

    /** What each system setting needs beyond Settings.System, Settings.Secure and public managers. */
    private val settingMembers: Map<Feature, List<HostApi>> = mapOf(
        Feature.SETTING_BRIGHTNESS to listOf(
            SS.DISPLAY_MANAGER, SS.DISPLAY_SET_BRIGHTNESS, SS.DISPLAY_GET_BRIGHTNESS,
            SS.DISPLAY, SS.DISPLAY_GET_BRIGHTNESS_INFO, SS.BRIGHTNESS_INFO, SS.BRIGHTNESS_INFO_MINIMUM, SS.BRIGHTNESS_INFO_MAXIMUM,
        ),
        Feature.SETTING_RINGER_MODE to listOf(SS.AUDIO_MANAGER, SS.AUDIO_SET_RINGER_MODE_INTERNAL, SS.AUDIO_GET_RINGER_MODE_INTERNAL),
        Feature.SETTING_RING_VOLUME to listOf(
            SS.AUDIO_MANAGER, SS.AUDIO_GET_LAST_AUDIBLE_STREAM_VOLUME, SS.AUDIO_GET_RINGER_MODE_INTERNAL, SS.AUDIO_SET_RINGER_MODE_INTERNAL,
        ),
        Feature.SETTING_NOTIFICATION_VOLUME to listOf(SS.AUDIO_MANAGER, SS.AUDIO_GET_LAST_AUDIBLE_STREAM_VOLUME),
        Feature.SETTING_NFC to listOf(SS.NFC_ADAPTER, SS.NFC_ENABLE, SS.NFC_DISABLE, SS.NFC_GET_ADAPTER_STATE),
        Feature.SETTING_HOTSPOT to listOf(
            SS.TETHERING_MANAGER, SS.START_TETHERING_CALLBACK, SS.TETHERING_START, SS.TETHERING_STOP,
            SS.WIFI_MANAGER, SS.WIFI_GET_AP_STATE,
        ),
    )

    val all: List<FeatureContract> = listOf(
        FeatureContract(
            Feature.MODE_PAGE,
            required = listOf(
                S.MODE_FRAGMENT, S.MODE_FRAGMENT_ON_RESUME, S.MODE_FRAGMENT_ON_UPDATED,
                S.MODE_FRAGMENT_GET_MODE, S.MODE_FRAGMENT_GET_SCREEN,
            ) + preferenceRows + modeIdentity,
            optional = buttonGroupRows + modeSchedule,
        ),
        FeatureContract(
            Feature.MODES_LIST,
            required = listOf(S.MODES_FRAGMENT_BASE, S.MODES_FRAGMENT_BASE_ON_START, S.MODES_FRAGMENT_BASE_GET_SCREEN) + preferenceRows,
        ),
        FeatureContract(
            Feature.EDITOR_RESULTS,
            required = listOf(S.SETTINGS_ACTIVITY, S.SETTINGS_ACTIVITY_ON_RESULT) + ruleWriter,
        ),
        FeatureContract(
            Feature.BUTTON_GROUP,
            required = listOf(
                S.PREFERENCE, S.PREFERENCE_VIEW_HOLDER, S.PREFERENCE_GET_KEY, S.VIEW_HOLDER_ITEM_VIEW,
                S.SEGMENTED_BUTTON, S.SEGMENTED_BUTTON_ON_BIND, S.MATERIAL_BUTTON, S.MATERIAL_BUTTON_SET_ICON,
                S.GROUP_ADAPTER, S.GROUP_ADAPTER_IS_DIVIDER,
            ),
        ),
        FeatureContract(
            Feature.CALENDAR_ROW,
            required = listOf(S.CALENDAR_CONTROLLER, S.CALENDAR_CONTROLLER_UPDATE_STATE) + preferenceRows + modeIdentity + modeSchedule,
        ),
        FeatureContract(
            Feature.SCHEDULE_REMOVAL,
            required = listOf(
                S.MODE_FRAGMENT, S.MODE_FRAGMENT_BASE, S.MODE_FRAGMENT_BASE_ON_CREATE, S.MODE_FRAGMENT_BASE_GET_MODE,
                S.SCHEDULE_TIME_FRAGMENT, S.SCHEDULE_CALENDAR_FRAGMENT,
            ) + appBarMenu + modeIdentity + modeSchedule + ruleWriter,
        ),
        FeatureContract(
            Feature.ICON_PICKER,
            required = listOf(
                S.ICON_OPTIONS, S.ICON_OPTIONS_CONTEXT, S.ICON_OPTIONS_GET_ICONS,
                S.ICON_INFO, S.ICON_INFO_NEW, S.ICON_INFO_RES_ID, S.IMMUTABLE_LIST, S.IMMUTABLE_LIST_COPY_OF,
            ),
        ),
        FeatureContract(Feature.SETTINGS_RESTART, required = listOf(S.APPLICATION, S.APPLICATION_ON_CREATE)),

        FeatureContract(
            Feature.AUTOMATION_START,
            required = listOf(SS.NOTIFICATION_SERVICE, SS.NOTIFICATION_SERVICE_ON_BOOT_PHASE, SS.NOTIFICATION_SERVICE_GET_CONTEXT),
        ),
        FeatureContract(
            Feature.CALENDAR_FILTER,
            required = listOf(
                SS.ZEN_MODE_CONFIG, SS.EVENT_INFO, SS.ZEN_MODE_CONFIG_PARSE_EVENT,
                SS.CALENDAR_TRACKER, SS.CALENDAR_TRACKER_USER_CONTEXT, SS.CALENDAR_TRACKER_MEETS_ATTENDEE,
            ),
        ),
        FeatureContract(
            Feature.TRIGGER_AUTOMATION,
            required = emptyList(),
            // Only what is connected at the moment a Bluetooth trigger starts; later changes arrive as broadcasts.
            optional = listOf(SS.BLUETOOTH_DEVICE, SS.BLUETOOTH_DEVICE_IS_CONNECTED),
        ),
        FeatureContract(
            Feature.ACTION_AUTOMATION,
            required = listOf(
                SS.CONTEXT_TYPE, SS.START_ACTIVITY_AS_USER,
                SS.ACTIVITY_THREAD, SS.ACTIVITY_THREAD_GET_PACKAGE_MANAGER, SS.PACKAGE_MANAGER_SERVICE, SS.PACKAGE_MANAGER_IS_PROTECTED_BROADCAST,
            ),
        ),
    ) + SystemSetting.entries.map { FeatureContract(it.feature, required = settingMembers[it.feature].orEmpty()) }

    private val byFeature = all.associateBy(FeatureContract::feature)

    fun of(feature: Feature): FeatureContract = byFeature.getValue(feature)

    fun of(host: Host): List<FeatureContract> = all.filter { it.host == host }

    /** Every member [host]'s features name, each once, in declaration order. */
    fun apisOf(host: Host): List<HostApi> = of(host).flatMap(FeatureContract::all).distinct()

    /** The features that depend on [api], and whether each requires it. */
    fun dependents(api: HostApi): Map<Feature, Boolean> =
        all.filter { api in it.all }.associate { it.feature to (api in it.required) }
}
