package my.github.MrxSiN.modeevolved.signal

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler

import my.github.MrxSiN.modeevolved.engine.SignalSource
import my.github.MrxSiN.modeevolved.trigger.Signal
import my.github.MrxSiN.modeevolved.trigger.StateUpdate

/**
 * Whether wired or USB headphones are connected, from the audio service's own device callback.
 *
 * Woken only when an audio device comes or goes. Registering reports the devices already there,
 * but later, on [handler]; they are read once at start too, so a rule saved now is judged on the
 * answer rather than on unknown. Started only while a rule reads it.
 */
class HeadphonesSource(context: Context, private val handler: Handler) : SignalSource {

    override val signal: Signal = Signal.HEADPHONES

    private val audio = context.getSystemService(AudioManager::class.java)
    private var publish: ((StateUpdate) -> Unit)? = null

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = emit()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = emit()
    }

    override fun start(publish: (StateUpdate) -> Unit) {
        this.publish = publish
        audio.registerAudioDeviceCallback(callback, handler)
        emit()
    }

    override fun stop() {
        audio.unregisterAudioDeviceCallback(callback)
        publish?.invoke { it.copy(headphonesConnected = null) }
        publish = null
    }

    private fun emit() {
        val connected = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in HEADPHONES }
        publish?.invoke { it.copy(headphonesConnected = connected) }
    }

    private companion object {
        val HEADPHONES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
        )
    }
}
