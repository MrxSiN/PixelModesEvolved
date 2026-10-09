package my.github.MrxSiN.modeevolved.system

import android.annotation.SuppressLint
import android.app.UiModeManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.UserManager
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.view.Display
import java.lang.reflect.Proxy
import java.util.concurrent.Executor
import kotlin.math.roundToInt

import my.github.MrxSiN.modeevolved.action.Brightness
import my.github.MrxSiN.modeevolved.action.SettingBackend
import my.github.MrxSiN.modeevolved.action.SystemSetting
import my.github.MrxSiN.modeevolved.action.SystemSetting.ADAPTIVE_BRIGHTNESS
import my.github.MrxSiN.modeevolved.action.SystemSetting.ALARM_VOLUME
import my.github.MrxSiN.modeevolved.action.SystemSetting.ALWAYS_ON_DISPLAY
import my.github.MrxSiN.modeevolved.action.SystemSetting.AUTO_ROTATE
import my.github.MrxSiN.modeevolved.action.SystemSetting.BLUETOOTH
import my.github.MrxSiN.modeevolved.action.SystemSetting.BRIGHTNESS
import my.github.MrxSiN.modeevolved.action.SystemSetting.DARK_THEME
import my.github.MrxSiN.modeevolved.action.SystemSetting.EXTRA_DIM
import my.github.MrxSiN.modeevolved.action.SystemSetting.HOTSPOT
import my.github.MrxSiN.modeevolved.action.SystemSetting.MEDIA_VOLUME
import my.github.MrxSiN.modeevolved.action.SystemSetting.MOBILE_DATA
import my.github.MrxSiN.modeevolved.action.SystemSetting.NFC
import my.github.MrxSiN.modeevolved.action.SystemSetting.NOTIFICATION_VOLUME
import my.github.MrxSiN.modeevolved.action.SystemSetting.RINGER_MODE
import my.github.MrxSiN.modeevolved.action.SystemSetting.RING_VOLUME
import my.github.MrxSiN.modeevolved.action.SystemSetting.SCREEN_TIMEOUT
import my.github.MrxSiN.modeevolved.action.SystemSetting.SMOOTH_DISPLAY
import my.github.MrxSiN.modeevolved.action.SystemSetting.WIFI
import my.github.MrxSiN.modeevolved.compat.SystemServerApis as SS
import my.github.MrxSiN.modeevolved.core.Logger
import my.github.MrxSiN.modeevolved.dex.HostResolver
import my.github.MrxSiN.modeevolved.signal.ATTRIBUTION_TAG

/**
 * Device settings, read and written from system_server the way Android's own controls do.
 *
 * Each setting goes through the service that applies it: a value written to `Settings` is only
 * used where the service on this build observes that value and changes the hardware or UI from
 * it (POWER_AUDIT.md lists each). Nothing runs a shell, a process or a Settings screen.
 *
 * Modes, their states and the stored rules are the system user's, so settings change only while
 * that user is in the foreground; for anyone else every call throws and nothing is written.
 */
// system_server holds every permission these calls check, and may use hidden APIs.
@SuppressLint("MissingPermission", "WrongConstant", "PrivateApi")
class SystemSettingsBackend(
    base: Context,
    private val apis: HostResolver,
    private val logger: Logger,
) : SettingBackend {

    // system_server must name who inside it calls; Bluetooth refuses a call that does not.
    private val context = base.createAttributionContext(ATTRIBUTION_TAG)

    private val resolver = context.contentResolver
    private val users by lazy { context.getSystemService(UserManager::class.java) }
    private val audio by lazy { context.getSystemService(AudioManager::class.java) }
    private val displays by lazy { context.getSystemService(DisplayManager::class.java) }
    private val uiModes by lazy { context.getSystemService(UiModeManager::class.java) }
    private val wifi by lazy { context.getSystemService(WifiManager::class.java) }
    private val bluetooth: BluetoothAdapter by lazy { checkNotNull(context.getSystemService(BluetoothManager::class.java)?.adapter) { "No Bluetooth" } }
    private val nfc: NfcAdapter by lazy { checkNotNull(NfcAdapter.getDefaultAdapter(context)) { "No NFC hardware" } }
    private val tethering: Any by lazy { checkNotNull(context.getSystemService(TETHERING_SERVICE)) { "No tethering service" } }

    /** Whether this build lets system_server change [setting]. Throws, saying why, when it does not. */
    fun probe(setting: SystemSetting) {
        when (setting) {
            BRIGHTNESS -> brightnessRange()
            SMOOTH_DISPLAY -> check(maxRefreshRate() > DEFAULT_REFRESH_RATE) { "This display has one refresh rate" }
            DARK_THEME -> checkNotNull(uiModes)
            RINGER_MODE, RING_VOLUME, NOTIFICATION_VOLUME, MEDIA_VOLUME, ALARM_VOLUME -> checkNotNull(audio)
            WIFI -> checkNotNull(wifi)
            BLUETOOTH -> bluetooth
            NFC -> nfc
            HOTSPOT -> tethering
            MOBILE_DATA -> check(context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_DATA)) { "No mobile data" }
            AUTO_ROTATE, ADAPTIVE_BRIGHTNESS, EXTRA_DIM, SCREEN_TIMEOUT, ALWAYS_ON_DISPLAY -> Unit
        }
    }

    override fun read(setting: SystemSetting): Int {
        checkForeground()
        return when (setting) {
            AUTO_ROTATE -> system(Settings.System.ACCELEROMETER_ROTATION)
            DARK_THEME -> uiModes.nightMode
            BRIGHTNESS -> Brightness.toMicro(apis.method(SS.DISPLAY_GET_BRIGHTNESS).invoke(displays, Display.DEFAULT_DISPLAY) as Float)
            ADAPTIVE_BRIGHTNESS -> system(Settings.System.SCREEN_BRIGHTNESS_MODE)
            EXTRA_DIM -> secure(EXTRA_DIM_KEY)
            SCREEN_TIMEOUT -> system(Settings.System.SCREEN_OFF_TIMEOUT)
            ALWAYS_ON_DISPLAY -> secure(ALWAYS_ON_KEY)
            SMOOTH_DISPLAY -> Settings.System.getString(resolver, PEAK_REFRESH_RATE)?.toFloatOrNull()?.toRawBits() ?: ABSENT
            RINGER_MODE -> ringer()
            // Ring and Vibrate share the ring stream: the ring volume is read with the ringer it implies.
            RING_VOLUME -> (ringer() shl RINGER_SHIFT) or lastAudible(AudioManager.STREAM_RING)
            // A stream the ringer mutes reads as zero; what comes back when it unmutes is what is held.
            NOTIFICATION_VOLUME -> lastAudible(AudioManager.STREAM_NOTIFICATION)
            MEDIA_VOLUME, ALARM_VOLUME -> audio.getStreamVolume(stream(setting))
            WIFI -> on(wifi.wifiState == WifiManager.WIFI_STATE_ENABLED || wifi.wifiState == WifiManager.WIFI_STATE_ENABLING)
            BLUETOOTH -> on(bluetooth.state == BluetoothAdapter.STATE_ON || bluetooth.state == BluetoothAdapter.STATE_TURNING_ON)
            NFC -> on((apis.method(SS.NFC_GET_ADAPTER_STATE).invoke(nfc) as Int).let { it == NFC_STATE_ON || it == NFC_STATE_TURNING_ON })
            HOTSPOT -> on((apis.method(SS.WIFI_GET_AP_STATE).invoke(wifi) as Int).let { it == WIFI_AP_STATE_ENABLED || it == WIFI_AP_STATE_ENABLING })
            MOBILE_DATA -> on(dataSubscription().isDataEnabledForReason(TelephonyManager.DATA_ENABLED_REASON_USER))
        }
    }

    // setWifiEnabled and BluetoothAdapter.enable are refused to apps, not to system_server.
    @Suppress("DEPRECATION")
    override fun write(setting: SystemSetting, native: Int): Int {
        checkForeground()
        val on = native == 1
        when (setting) {
            AUTO_ROTATE -> Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, native)
            DARK_THEME -> uiModes.setNightMode(native)
            BRIGHTNESS -> {
                apis.method(SS.DISPLAY_SET_BRIGHTNESS).invoke(displays, Display.DEFAULT_DISPLAY, Brightness.fromMicro(native))
                // The display clamps what it is given; what it holds now is what a later reading will say.
                return read(setting)
            }
            ADAPTIVE_BRIGHTNESS -> Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, native)
            EXTRA_DIM -> Settings.Secure.putInt(resolver, EXTRA_DIM_KEY, native)
            SCREEN_TIMEOUT -> Settings.System.putInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, native)
            ALWAYS_ON_DISPLAY -> Settings.Secure.putInt(resolver, ALWAYS_ON_KEY, native)
            SMOOTH_DISPLAY -> {
                // A rate that was never set has no value to put back; the device default stays in charge.
                check(native != ABSENT) { "No stored refresh rate to restore" }
                Settings.System.putFloat(resolver, PEAK_REFRESH_RATE, Float.fromBits(native))
            }
            RINGER_MODE -> apis.method(SS.AUDIO_SET_RINGER_MODE_INTERNAL).invoke(audio, native)
            RING_VOLUME -> {
                // A ring volume above zero puts the ringer on Ring; the ringer it was read with goes back after it.
                audio.setStreamVolume(AudioManager.STREAM_RING, native and INDEX_MASK, 0)
                val ringer = native shr RINGER_SHIFT
                if (ringer != AudioManager.RINGER_MODE_NORMAL && ringer() != ringer) apis.method(SS.AUDIO_SET_RINGER_MODE_INTERNAL).invoke(audio, ringer)
                return read(setting)
            }
            NOTIFICATION_VOLUME, MEDIA_VOLUME, ALARM_VOLUME -> {
                // The safe media volume may hold a stream below what was asked.
                audio.setStreamVolume(stream(setting), native, 0)
                return read(setting)
            }
            WIFI -> check(wifi.setWifiEnabled(on)) { "Wi-Fi refused" }
            BLUETOOTH -> check(if (on) bluetooth.enable() else bluetooth.disable()) { "Bluetooth refused" }
            NFC -> check(apis.method(if (on) SS.NFC_ENABLE else SS.NFC_DISABLE).invoke(nfc) as Boolean) { "NFC refused" }
            HOTSPOT -> if (on) startHotspot() else apis.method(SS.TETHERING_STOP).invoke(tethering, TETHERING_WIFI)
            MOBILE_DATA -> dataSubscription().setDataEnabledForReason(TelephonyManager.DATA_ENABLED_REASON_USER, on)
        }
        return native
    }

    override fun toNative(setting: SystemSetting, value: Int): Int = when (setting) {
        DARK_THEME -> if (value == 1) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO
        BRIGHTNESS -> brightnessRange().let { (min, max) -> Brightness.toMicro(Brightness.percentToLinear(value, min, max)) }
        SMOOTH_DISPLAY -> (if (value == 1) Float.POSITIVE_INFINITY else DEFAULT_REFRESH_RATE).toRawBits()
        RING_VOLUME -> (AudioManager.RINGER_MODE_NORMAL shl RINGER_SHIFT) or index(AudioManager.STREAM_RING, value)
        NOTIFICATION_VOLUME, MEDIA_VOLUME, ALARM_VOLUME -> index(stream(setting), value)
        else -> value
    }

    /** Mobile data follows the default data subscription; everything else applies to the device. */
    override fun target(setting: SystemSetting): Int =
        if (setting == MOBILE_DATA) SubscriptionManager.getDefaultDataSubscriptionId() else 0

    private fun index(stream: Int, percent: Int): Int =
        (percent * audio.getStreamMaxVolume(stream) / 100.0).roundToInt().coerceAtLeast(audio.getStreamMinVolume(stream))

    private fun ringer(): Int = apis.method(SS.AUDIO_GET_RINGER_MODE_INTERNAL).invoke(audio) as Int

    private fun lastAudible(stream: Int): Int = apis.method(SS.AUDIO_GET_LAST_AUDIBLE_STREAM_VOLUME).invoke(audio, stream) as Int

    private fun checkForeground() = check(users.isUserForeground) { "Another user is in the foreground" }

    private fun system(key: String) = Settings.System.getInt(resolver, key)

    private fun secure(key: String) = Settings.Secure.getInt(resolver, key, 0)

    private fun on(value: Boolean) = if (value) 1 else 0

    private fun stream(setting: SystemSetting) = when (setting) {
        RING_VOLUME -> AudioManager.STREAM_RING
        NOTIFICATION_VOLUME -> AudioManager.STREAM_NOTIFICATION
        MEDIA_VOLUME -> AudioManager.STREAM_MUSIC
        else -> AudioManager.STREAM_ALARM
    }

    /** The display's linear brightness range, as the slider maps its percentages onto it. */
    private fun brightnessRange(): Pair<Float, Float> {
        val display = checkNotNull(displays.getDisplay(Display.DEFAULT_DISPLAY)) { "No display" }
        val info = checkNotNull(apis.method(SS.DISPLAY_GET_BRIGHTNESS_INFO).invoke(display)) { "No brightness information" }
        return apis.field(SS.BRIGHTNESS_INFO_MINIMUM).getFloat(info) to apis.field(SS.BRIGHTNESS_INFO_MAXIMUM).getFloat(info)
    }

    private fun maxRefreshRate(): Float =
        checkNotNull(displays.getDisplay(Display.DEFAULT_DISPLAY)) { "No display" }.supportedModes.maxOf { it.refreshRate }

    /** The default data subscription, or a throw when there is none: nothing is guessed. */
    private fun dataSubscription(): TelephonyManager {
        val id = SubscriptionManager.getDefaultDataSubscriptionId()
        check(SubscriptionManager.isValidSubscriptionId(id)) { "No default mobile data SIM" }
        return context.getSystemService(TelephonyManager::class.java).createForSubscriptionId(id)
    }

    /** Through the carrier's entitlement check, as Quick Settings starts it; a refusal is logged, not retried. */
    private fun startHotspot() {
        val callbackType = apis.type(SS.START_TETHERING_CALLBACK)
        val callback = Proxy.newProxyInstance(callbackType.classLoader, arrayOf(callbackType)) { proxy, method, args ->
            when (method.name) {
                "onTetheringFailed" -> logger.warn("Hotspot did not start: error ${args?.firstOrNull()}")
                "equals" -> return@newProxyInstance args?.firstOrNull() === proxy
                "hashCode" -> return@newProxyInstance System.identityHashCode(proxy)
                "toString" -> return@newProxyInstance "PixelModesEvolved hotspot callback"
            }
            null
        }
        apis.method(SS.TETHERING_START).invoke(tethering, TETHERING_WIFI, Executor(Runnable::run), callback)
    }

    private companion object {
        /** `Context.TETHERING_SERVICE`, hidden from the SDK. */
        const val TETHERING_SERVICE = "tethering"
        const val TETHERING_WIFI = 0

        /** `Settings.Secure.REDUCE_BRIGHT_COLORS_ACTIVATED`: Extra dim, applied by ColorDisplayService. */
        const val EXTRA_DIM_KEY = "reduce_bright_colors_activated"

        /** `Settings.Secure.DOZE_ALWAYS_ON`: Always show time and info, applied by System UI. */
        const val ALWAYS_ON_KEY = "doze_always_on"

        /** `Settings.System.PEAK_REFRESH_RATE`: Smooth Display, applied by DisplayModeDirector. */
        const val PEAK_REFRESH_RATE = "peak_refresh_rate"
        const val DEFAULT_REFRESH_RATE = 60f
        const val ABSENT = Int.MIN_VALUE

        /** A ring volume held as `ringer shl 8 or index`. */
        const val RINGER_SHIFT = 8
        const val INDEX_MASK = 0xFF

        /** `NfcAdapter.STATE_*` and `WifiManager.WIFI_AP_STATE_*`, hidden from the SDK. */
        const val NFC_STATE_TURNING_ON = 2
        const val NFC_STATE_ON = 3
        const val WIFI_AP_STATE_ENABLING = 12
        const val WIFI_AP_STATE_ENABLED = 13
    }
}
