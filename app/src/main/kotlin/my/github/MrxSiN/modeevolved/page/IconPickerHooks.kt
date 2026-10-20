package my.github.MrxSiN.modeevolved.page

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import java.lang.reflect.Constructor

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.ExceptionMode

import my.github.MrxSiN.modeevolved.R
import my.github.MrxSiN.modeevolved.compat.SettingsApis as S
import my.github.MrxSiN.modeevolved.core.Feature
import my.github.MrxSiN.modeevolved.core.FeatureHooks
import my.github.MrxSiN.modeevolved.core.Logger
import my.github.MrxSiN.modeevolved.dex.HostResolver

/**
 * Offers more icons when a Mode's icon is changed.
 *
 * A Mode made in Settings belongs to the `android` package, and SystemUI draws its icon in the
 * status bar, Quick Settings and the lock screen from that package's resources. So every icon
 * offered here is a framework drawable, as Settings' own are: the choice keeps working everywhere,
 * and after this module is removed. Each is looked up by name and left out when a build lacks it.
 */
// Framework drawables are not in the SDK and only reachable by name.
@SuppressLint("DiscouragedApi")
class IconPickerHooks(
    private val xposed: XposedInterface,
    private val apis: HostResolver,
    private val logger: Logger,
    private val textFor: (Context) -> ModuleText,
) : FeatureHooks {

    override val feature = Feature.ICON_PICKER

    /** The extra icons, built once from the first picker shown; they cannot change while Settings runs. */
    private var extras: List<Any>? = null

    override fun install() {
        val context = apis.field(S.ICON_OPTIONS_CONTEXT)
        val newIcon = apis.constructor(S.ICON_INFO_NEW)
        val resId = apis.method(S.ICON_INFO_RES_ID)
        val copyOf = apis.method(S.IMMUTABLE_LIST_COPY_OF)

        xposed.hook(apis.method(S.ICON_OPTIONS_GET_ICONS)).setExceptionMode(ExceptionMode.PROTECTIVE).intercept { chain ->
            val stock = chain.proceed() as List<*>
            runCatching {
                val extras = extras ?: build(textFor(context.get(chain.thisObject) as Context), newIcon).also { extras = it }
                val offered = stock.mapTo(HashSet()) { resId.invoke(it) }
                copyOf.invoke(null, stock + extras.filter { resId.invoke(it) !in offered })
            }.onFailure { logger.warn("Could not add Mode icons", it) }.getOrDefault(stock)
        }
    }

    private fun build(text: ModuleText, newIcon: Constructor<*>): List<Any> {
        val framework = Resources.getSystem()
        return ICONS.mapNotNull { (name, description) ->
            val id = framework.getIdentifier(name, "drawable", "android")
            if (id == 0) null else newIcon.newInstance(id, text.string(description))
        }
    }

    private companion object {
        /** Monochrome vector icons the framework ships and Settings does not offer, by drawable name. */
        val ICONS: List<Pair<String, Int>> = listOf(
        "ic_zen_mode_icon_camping" to R.string.mode_icon_zen_mode_icon_camping,
        "ic_zen_mode_icon_croissant" to R.string.mode_icon_zen_mode_icon_croissant,
        "ic_qs_airplane" to R.string.mode_icon_qs_airplane,
        "ic_qs_flashlight" to R.string.mode_icon_qs_flashlight,
        "ic_qs_battery_saver" to R.string.mode_icon_qs_battery_saver,
        "ic_battery" to R.string.mode_icon_battery,
        "ic_qs_bluetooth" to R.string.mode_icon_qs_bluetooth,
        "ic_qs_night_display_on" to R.string.mode_icon_qs_night_display_on,
        "ic_qs_ui_mode_night" to R.string.mode_icon_qs_ui_mode_night,
        "ic_qs_auto_rotate" to R.string.mode_icon_qs_auto_rotate,
        "ic_qs_one_handed_mode" to R.string.mode_icon_qs_one_handed_mode,
        "ic_audio_alarm" to R.string.mode_icon_audio_alarm,
        "ic_audio_vol_mute" to R.string.mode_icon_audio_vol_mute,
        "ic_notifications" to R.string.mode_icon_notifications,
        "ic_bt_laptop" to R.string.mode_icon_bt_laptop,
        "ic_bt_headset_hfp" to R.string.mode_icon_bt_headset_hfp,
        "ic_bt_hearing_aid" to R.string.mode_icon_bt_hearing_aid,
        "ic_bt_misc_hid" to R.string.mode_icon_bt_misc_hid,
        "ic_camera" to R.string.mode_icon_camera,
        "ic_mic" to R.string.mode_icon_mic,
        "ic_phone" to R.string.mode_icon_phone,
        "ic_mobile_phone" to R.string.mode_icon_mobile_phone,
        "ic_dual_screen" to R.string.mode_icon_dual_screen,
        "ic_thermostat" to R.string.mode_icon_thermostat,
        "ic_fingerprint" to R.string.mode_icon_fingerprint,
        "ic_lock" to R.string.mode_icon_lock,
        "ic_satellite_alt_24px" to R.string.mode_icon_satellite_alt_24px,
        "ic_signal_location" to R.string.mode_icon_signal_location,
        "ic_thread_network" to R.string.mode_icon_thread_network,
        "ic_widgets" to R.string.mode_icon_widgets,
        "ic_apps" to R.string.mode_icon_apps,
        "ic_visibility" to R.string.mode_icon_visibility,
        "ic_account_circle" to R.string.mode_icon_account_circle,
        "ic_pan_tool" to R.string.mode_icon_pan_tool,
        "ic_print" to R.string.mode_icon_print,
        "ic_screenshot" to R.string.mode_icon_screenshot,
        "ic_mode_edit" to R.string.mode_icon_mode_edit,
        "ic_feedback" to R.string.mode_icon_feedback,
        "ic_folder_24dp" to R.string.mode_icon_folder_24dp,
        "ic_doc_document" to R.string.mode_icon_doc_document,
        "ic_doc_image" to R.string.mode_icon_doc_image,
        "ic_doc_video" to R.string.mode_icon_doc_video,
        "ic_doc_audio" to R.string.mode_icon_doc_audio,
        )
    }
}
