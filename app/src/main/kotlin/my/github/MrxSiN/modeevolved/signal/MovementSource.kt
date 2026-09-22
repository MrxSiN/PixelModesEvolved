package my.github.MrxSiN.modeevolved.signal

import my.github.MrxSiN.modeevolved.core.Logger
import my.github.MrxSiN.modeevolved.engine.Delays
import my.github.MrxSiN.modeevolved.engine.SignalSource
import my.github.MrxSiN.modeevolved.trigger.Movement
import my.github.MrxSiN.modeevolved.trigger.Signal
import my.github.MrxSiN.modeevolved.trigger.StateUpdate

/**
 * Moving or still, from the significant motion sensor.
 *
 * The sensor is a low-power one-shot wake-up: it fires once when the person
 * starts walking, cycling or driving, and is armed again after every event.
 *
 * Movement begins unknown, never still. "Left still" means the phone has not
 * moved for [STILL_AFTER_MILLIS], and at the moment this source starts nobody
 * knows that yet, so the answer is withheld until the sensor has stayed quiet
 * that long. Publishing still at once would make a "left still" rule true the
 * instant the phone booted, or the instant the rule was saved.
 */
class MovementSource(
    private val sensor: MotionSensor,
    private val delays: Delays,
    private val logger: Logger,
) : SignalSource {

    override val signal: Signal = Signal.MOVEMENT

    private var publish: ((StateUpdate) -> Unit)? = null
    private var settling: AutoCloseable? = null

    override fun start(publish: (StateUpdate) -> Unit) {
        this.publish = publish
        // Throws when the device has no such sensor, which leaves the hub with no movement signal.
        sensor.arm(::onMotion)
        awaitStill()
    }

    override fun stop() {
        sensor.disarm()
        cancelSettling()
        publish?.invoke { it.copy(movement = null) }
        publish = null
    }

    private fun onMotion() {
        if (publish == null) return
        emit(Movement.MOVING)
        awaitStill()
        // Re-arming is the only step that can fail once running, and losing it costs this signal alone.
        runCatching { sensor.arm(::onMotion) }
            .onFailure { logger.warn("Could not arm the motion sensor again", it) }
    }

    /** Still is published only once the sensor has stayed quiet for the whole wait. */
    private fun awaitStill() {
        cancelSettling()
        settling = delays.after(STILL_AFTER_MILLIS) {
            settling = null
            emit(Movement.STILL)
        }
    }

    private fun cancelSettling() {
        settling?.close()
        settling = null
    }

    private fun emit(movement: Movement) {
        publish?.invoke { it.copy(movement = movement) }
    }

    private companion object {
        const val STILL_AFTER_MILLIS = 10 * 60_000L
    }
}
