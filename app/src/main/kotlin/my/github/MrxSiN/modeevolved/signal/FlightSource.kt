package my.github.MrxSiN.modeevolved.signal

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationRequest
import android.os.Handler

import my.github.MrxSiN.modeevolved.core.Logger
import my.github.MrxSiN.modeevolved.engine.SignalSource
import my.github.MrxSiN.modeevolved.trigger.FlightDetector
import my.github.MrxSiN.modeevolved.trigger.Signal
import my.github.MrxSiN.modeevolved.trigger.StateUpdate

/**
 * Whether the device is flying, from cabin pressure and from the speed and altitude of position fixes.
 *
 * The barometer is the main witness: a cabin's climb shows in it with the phone in a pocket and
 * airplane mode on, where a satellite fix seldom comes. It is read at its slowest rate and batched
 * in the sensor hub's FIFO, so it never wakes the phone; readings arrive whenever something else
 * does, such as the location request below.
 *
 * Only satellite fixes carry a speed, and airplane mode leaves satellite positioning on, so this
 * also asks for high accuracy. It runs only while a rule needs it, and a short minimum interval
 * lets it share fixes other apps request.
 *
 * Fixes must be fresher here than an area rule's. A speed is a statement about
 * one moment; the fix from before takeoff says nothing about being in the air,
 * and a stale one either side of a flight would start or end it at the wrong time.
 */
class FlightSource(context: Context, private val handler: Handler, logger: Logger) : SignalSource {

    override val signal: Signal = Signal.FLIGHT

    private val updates = LocationUpdates(
        context,
        handler,
        LocationRequest.QUALITY_HIGH_ACCURACY,
        intervalMillis = REQUEST_INTERVAL_MILLIS,
        minIntervalMillis = 30_000L,
        maxAgeMillis = MAX_AGE_MILLIS,
        logger = logger,
    )
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val barometer: Sensor? = sensors.getDefaultSensor(Sensor.TYPE_PRESSURE)

    private var detector = FlightDetector()
    private var publish: ((StateUpdate) -> Unit)? = null
    private var published: Boolean? = null

    /** When the last reading the detector was given was taken; the sensor's own rate is far finer than needed. */
    private var pressureAtMillis = Long.MIN_VALUE

    private val pressureListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val at = event.timestamp / 1_000_000
            if (at - pressureAtMillis < PRESSURE_STEP_MILLIS) return
            pressureAtMillis = at
            emit(detector.onPressure(event.values[0], at))
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    override fun start(publish: (StateUpdate) -> Unit) {
        this.publish = publish
        detector = FlightDetector()
        published = null
        pressureAtMillis = Long.MIN_VALUE
        barometer?.let { sensors.registerListener(pressureListener, it, PRESSURE_SAMPLING_MICROS, PRESSURE_BATCH_MICROS, handler) }
        updates.start(::onFix)
    }

    override fun stop() {
        updates.stop()
        sensors.unregisterListener(pressureListener)
        publish?.invoke { it.copy(flying = null) }
        publish = null
    }

    private fun onFix(reading: LocationUpdates.Reading) {
        val location = reading.location
        val fix = FlightDetector.Fix(
            speedMetersPerSecond = location.speed.takeIf { location.hasSpeed() },
            altitudeMeters = location.altitude.takeIf { location.hasAltitude() },
            atMillis = location.elapsedRealtimeNanos / 1_000_000,
        )
        emit(detector.onFix(fix))
    }

    /** Only a change is published, since a reading arrives every half minute. */
    private fun emit(flying: Boolean) {
        if (flying == published) return
        published = flying
        publish?.invoke { it.copy(flying = flying) }
    }

    private companion object {
        const val REQUEST_INTERVAL_MILLIS = 3 * 60_000L

        /** Under one request interval, so a speed is never carried over from the previous one. */
        const val MAX_AGE_MILLIS = 2 * 60_000L

        /** The barometer's slowest rate, 1 Hz. */
        const val PRESSURE_SAMPLING_MICROS = 1_000_000

        /** Five minutes of readings fit the sensor hub's FIFO many times over, so none is lost while asleep. */
        const val PRESSURE_BATCH_MICROS = 5 * 60 * 1_000_000

        /** One reading kept per half minute. */
        const val PRESSURE_STEP_MILLIS = 30_000L
    }
}
