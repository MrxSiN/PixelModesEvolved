package my.github.MrxSiN.modeevolved.signal

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.os.Handler

import my.github.MrxSiN.modeevolved.engine.SignalSource
import my.github.MrxSiN.modeevolved.trigger.Movement
import my.github.MrxSiN.modeevolved.trigger.Signal
import my.github.MrxSiN.modeevolved.trigger.StateUpdate

/**
 * Moving or still, from the significant motion sensor.
 *
 * That sensor is a low-power, one-shot wake-up: it fires once when the person
 * starts walking, cycling or driving. It is re-armed after every event, and a
 * quiet period of [STILL_AFTER_MILLIS] without one counts as still.
 */
class MovementSource(context: Context, private val handler: Handler) : SignalSource {

    override val signal: Signal = Signal.MOVEMENT

    private val sensors = context.getSystemService(SensorManager::class.java)
    private val motion: Sensor? = sensors.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)
    private var publish: ((StateUpdate) -> Unit)? = null

    private val trigger = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent) {
            handler.post(::onMotion)
        }
    }

    private val settle = Runnable { emit(Movement.STILL) }

    override fun start(publish: (StateUpdate) -> Unit) {
        val sensor = checkNotNull(motion) { "No significant motion sensor" }
        this.publish = publish
        check(sensors.requestTriggerSensor(trigger, sensor)) { "Significant motion sensor refused" }
        emit(Movement.STILL)
    }

    override fun stop() {
        motion?.let { sensors.cancelTriggerSensor(trigger, it) }
        handler.removeCallbacks(settle)
        publish?.invoke { it.copy(movement = null) }
        publish = null
    }

    private fun onMotion() {
        val sensor = motion ?: return
        if (publish == null) return
        emit(Movement.MOVING)
        handler.removeCallbacks(settle)
        handler.postDelayed(settle, STILL_AFTER_MILLIS)
        sensors.requestTriggerSensor(trigger, sensor)
    }

    private fun emit(movement: Movement) {
        publish?.invoke { it.copy(movement = movement) }
    }

    private companion object {
        const val STILL_AFTER_MILLIS = 10 * 60_000L
    }
}
