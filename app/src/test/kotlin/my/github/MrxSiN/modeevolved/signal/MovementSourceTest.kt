package my.github.MrxSiN.modeevolved.signal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

import my.github.MrxSiN.modeevolved.core.SilentLogger
import my.github.MrxSiN.modeevolved.engine.TestDelays
import my.github.MrxSiN.modeevolved.trigger.DeviceState
import my.github.MrxSiN.modeevolved.trigger.Movement

/**
 * The lifecycle of the movement signal: what it says before it knows, and
 * what starting, stopping and starting again leave behind.
 */
class MovementSourceTest {

    private val sensor = FakeMotionSensor()
    private val delays = TestDelays()
    private val source = MovementSource(sensor, delays, SilentLogger)

    private var state = DeviceState()
    private val published = mutableListOf<Movement?>()

    private fun start() = source.start { update ->
        state = update(state)
        published += state.movement
    }

    @Test
    fun `movement begins unknown, not still`() {
        start()
        assertNull(state.movement)
        assertEquals(emptyList<Movement?>(), published)
    }

    @Test
    fun `still is published only after ten quiet minutes`() {
        start()
        delays.advance(9 * 60_000L)
        assertNull(state.movement)

        delays.advance(60_000L)
        assertEquals(Movement.STILL, state.movement)
    }

    @Test
    fun `motion publishes moving and re-arms the sensor`() {
        start()
        sensor.fire()
        assertEquals(Movement.MOVING, state.movement)
        assertTrue(sensor.isArmed)
        assertEquals(2, sensor.arms)
    }

    @Test
    fun `motion restarts the wait, so the phone is not called still ten minutes after it moved`() {
        start()
        delays.advance(9 * 60_000L)
        sensor.fire()

        delays.advance(9 * 60_000L)
        assertEquals(Movement.MOVING, state.movement)

        delays.advance(60_000L)
        assertEquals(Movement.STILL, state.movement)
    }

    @Test
    fun `stopping disarms the sensor, drops the wait and reports movement unknown again`() {
        start()
        source.stop()

        assertEquals(false, sensor.isArmed)
        assertNull(state.movement)
        assertEquals(0, delays.pending)

        delays.advance(60 * 60_000L)
        assertEquals(listOf<Movement?>(null), published)
    }

    @Test
    fun `a sensor event arriving after stop changes nothing`() {
        start()
        source.stop()
        sensor.fire()
        assertNull(state.movement)
    }

    @Test
    fun `starting again begins unknown, not where it left off`() {
        start()
        delays.advance(10 * 60_000L)
        assertEquals(Movement.STILL, state.movement)

        source.stop()
        start()
        assertNull(state.movement)

        delays.advance(9 * 60_000L)
        assertNull(state.movement)
    }

    @Test
    fun `a sensor that cannot be armed again costs the signal nothing else`() {
        start()
        sensor.failNextArm = true
        sensor.fire()
        assertEquals(Movement.MOVING, state.movement)

        // The wait still runs, so the source is not left saying "moving" for ever.
        delays.advance(10 * 60_000L)
        assertEquals(Movement.STILL, state.movement)
    }

    /** A significant motion sensor whose one shot is fired by the test. */
    private class FakeMotionSensor : MotionSensor {
        var arms = 0
        var failNextArm = false
        private var waiting: (() -> Unit)? = null

        val isArmed: Boolean get() = waiting != null

        override fun arm(onMotion: () -> Unit) {
            arms++
            if (failNextArm) {
                failNextArm = false
                error("Significant motion sensor refused")
            }
            waiting = onMotion
        }

        override fun disarm() {
            waiting = null
        }

        /** Fires the one shot, which disarms the sensor as the real one does. */
        fun fire() {
            val onMotion = waiting ?: return
            waiting = null
            onMotion()
        }
    }
}
