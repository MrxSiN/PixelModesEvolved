package my.github.MrxSiN.modeevolved.compat

import my.github.MrxSiN.modeevolved.compat.Sdk.EXECUTOR
import my.github.MrxSiN.modeevolved.compat.Sdk.FLOAT
import my.github.MrxSiN.modeevolved.compat.Sdk.INT
import my.github.MrxSiN.modeevolved.compat.Sdk.INTENT
import my.github.MrxSiN.modeevolved.compat.Sdk.STRING
import my.github.MrxSiN.modeevolved.compat.Sdk.URI
import my.github.MrxSiN.modeevolved.compat.Sdk.USER_HANDLE

/**
 * Every private member of system_server this module reaches for, as read off
 * `CP3A.260905.009`.
 *
 * Found by name only: searching the framework's own dex would mean parsing every
 * boot jar at boot, for a gain the Settings half already covers. So no class here
 * declares a structural [Locator]; `FeatureContractsTest` holds that.
 *
 * Declared in dependency order, as [SettingsApis] is.
 */
object SystemServerApis {

    private fun type(name: String) = ClassApi(name, Host.SYSTEM_SERVER)

    // Where the automation starts.

    val NOTIFICATION_SERVICE = type("com.android.server.notification.NotificationManagerService")
    val NOTIFICATION_SERVICE_ON_BOOT_PHASE = MethodApi(NOTIFICATION_SERVICE, "onBootPhase", listOf(INT), Scope.HOOK)

    /** Declared on `SystemService`, above the notification service. */
    val NOTIFICATION_SERVICE_GET_CONTEXT = MethodApi(NOTIFICATION_SERVICE, "getContext")

    // The stock calendar condition, narrowed to one event.

    val ZEN_MODE_CONFIG = type("android.service.notification.ZenModeConfig")
    val EVENT_INFO = type("android.service.notification.ZenModeConfig\$EventInfo")
    val ZEN_MODE_CONFIG_PARSE_EVENT = MethodApi(ZEN_MODE_CONFIG, "tryParseEventConditionId", listOf(URI), Scope.HOOK)

    val CALENDAR_TRACKER = type("com.android.server.modes.CalendarTracker")
    val CALENDAR_TRACKER_USER_CONTEXT = FieldApi(CALENDAR_TRACKER, "mUserContext")
    val CALENDAR_TRACKER_MEETS_ATTENDEE = MethodApi(CALENDAR_TRACKER, "meetsAttendee", listOf(EVENT_INFO, INT, STRING), Scope.HOOK)

    // Acting for the person using the phone.

    val CONTEXT_TYPE = type(Sdk.CONTEXT.name)

    /** Hidden: system_server may start activities for any user. */
    val START_ACTIVITY_AS_USER = MethodApi(CONTEXT_TYPE, "startActivityAsUser", listOf(INTENT, USER_HANDLE))

    val ACTIVITY_THREAD = type("android.app.ActivityThread")

    /** Hidden, static: the package manager service. */
    val ACTIVITY_THREAD_GET_PACKAGE_MANAGER = MethodApi(ACTIVITY_THREAD, "getPackageManager")

    val PACKAGE_MANAGER_SERVICE = type("android.content.pm.IPackageManager")

    /**
     * Whether the platform or a system app declared an action a protected broadcast.
     * Not `ActivityThread.isProtectedBroadcast(Intent)`: that answers false unless
     * StrictMode's unsafe intent check is on, measured on `CP3A.261005.005`.
     */
    val PACKAGE_MANAGER_IS_PROTECTED_BROADCAST = MethodApi(PACKAGE_MANAGER_SERVICE, "isProtectedBroadcast", listOf(STRING))

    // What is already there when a trigger starts.

    val BLUETOOTH_DEVICE = type("android.bluetooth.BluetoothDevice")

    /** A system API: whether a bonded device is connected now, for devices connected before the receiver existed. */
    val BLUETOOTH_DEVICE_IS_CONNECTED = MethodApi(BLUETOOTH_DEVICE, "isConnected")

    // System settings a Mode holds. Each is a hidden or system API of the framework, called from
    // system_server, whose uid passes the permission each checks. Read off CP3A.261005.005.

    val AUDIO_MANAGER = type("android.media.AudioManager")

    /** What the volume panel sets: the ringer itself, without the Do Not Disturb mapping the public setter applies. */
    val AUDIO_SET_RINGER_MODE_INTERNAL = MethodApi(AUDIO_MANAGER, "setRingerModeInternal", listOf(INT))
    val AUDIO_GET_RINGER_MODE_INTERNAL = MethodApi(AUDIO_MANAGER, "getRingerModeInternal")

    /** A stream's volume as it comes back when the ringer stops muting it. */
    val AUDIO_GET_LAST_AUDIBLE_STREAM_VOLUME = MethodApi(AUDIO_MANAGER, "getLastAudibleStreamVolume", listOf(INT))

    val DISPLAY_MANAGER = type("android.hardware.display.DisplayManager")

    /** What the brightness slider sets, on a linear scale between the display's minimum and maximum. */
    val DISPLAY_SET_BRIGHTNESS = MethodApi(DISPLAY_MANAGER, "setBrightness", listOf(INT, FLOAT))
    val DISPLAY_GET_BRIGHTNESS = MethodApi(DISPLAY_MANAGER, "getBrightness", listOf(INT))

    val DISPLAY = type("android.view.Display")
    val DISPLAY_GET_BRIGHTNESS_INFO = MethodApi(DISPLAY, "getBrightnessInfo")
    val BRIGHTNESS_INFO = type("android.hardware.display.BrightnessInfo")
    val BRIGHTNESS_INFO_MINIMUM = FieldApi(BRIGHTNESS_INFO, "brightnessMinimum")
    val BRIGHTNESS_INFO_MAXIMUM = FieldApi(BRIGHTNESS_INFO, "brightnessMaximum")

    val NFC_ADAPTER = type("android.nfc.NfcAdapter")
    val NFC_ENABLE = MethodApi(NFC_ADAPTER, "enable")
    val NFC_DISABLE = MethodApi(NFC_ADAPTER, "disable")

    /** Turning on and off count as the state they are heading for. */
    val NFC_GET_ADAPTER_STATE = MethodApi(NFC_ADAPTER, "getAdapterState")

    val TETHERING_MANAGER = type("android.net.TetheringManager")
    val START_TETHERING_CALLBACK = type("android.net.TetheringManager\$StartTetheringCallback")

    /** Starts the Wi-Fi hotspot through the carrier's entitlement check, as Quick Settings does. */
    val TETHERING_START = MethodApi(TETHERING_MANAGER, "startTethering", listOf(INT, EXECUTOR, START_TETHERING_CALLBACK))
    val TETHERING_STOP = MethodApi(TETHERING_MANAGER, "stopTethering", listOf(INT))

    val WIFI_MANAGER = type("android.net.wifi.WifiManager")
    val WIFI_GET_AP_STATE = MethodApi(WIFI_MANAGER, "getWifiApState")
}
