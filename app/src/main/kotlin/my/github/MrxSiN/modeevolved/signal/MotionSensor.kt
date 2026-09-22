package my.github.MrxSiN.modeevolved.signal

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.os.Handler

/**
 * The significant motion sensor, as this module uses it: armed once, fires once.
 *
 * Behind an interface so the rule that turns its events into moving and still
 * can be decided, and tested, without a device.
 */
interface MotionSensor {

    /** Arms the sensor to call [onMotion] once, on the engine thread. Throws when the device has no such sensor. */
    fun arm(onMotion: () -> Unit)

    /** Disarms it. Doing so when it is not armed does nothing. */
    fun disarm()
}

/** [MotionSensor] backed by `TYPE_SIGNIFICANT_MOTION`, a low-power one-shot wake-up sensor. */
class AndroidMotionSensor(context: Context, private val handler: Handler) : MotionSensor {

    private val sensors = context.getSystemService(SensorManager::class.java)
    private val motion: Sensor? = sensors.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)
    private var waiting: (() -> Unit)? = null

    private val listener = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent) {
            // The sensor disarms itself as it fires, so the callback is taken rather than kept.
            val onMotion = waiting ?: return
            waiting = null
            handler.post(onMotion)
        }
    }

    override fun arm(onMotion: () -> Unit) {
        val sensor = checkNotNull(motion) { "No significant motion sensor" }
        waiting = onMotion
        check(sensors.requestTriggerSensor(listener, sensor)) { "Significant motion sensor refused" }
    }

    override fun disarm() {
        waiting = null
        motion?.let { sensors.cancelTriggerSensor(listener, it) }
    }
}
