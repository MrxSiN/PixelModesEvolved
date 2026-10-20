package my.github.MrxSiN.modeevolved.trigger

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

import my.github.MrxSiN.modeevolved.trigger.FlightDetector.Fix

class FlightDetectorTest {

    private val minute = 60_000L

    /** Feeds one reading every 30 s, moving linearly from [from] to [to] hPa over [minutes]; returns the last answer. */
    private fun FlightDetector.pressure(from: Float, to: Float, minutes: Int, startMinute: Int): Boolean {
        val steps = minutes * 2
        var flying = false
        for (step in 0..steps) {
            flying = onPressure(from + (to - from) * step / steps, startMinute * minute + step * 30_000L)
        }
        return flying
    }

    @Test
    fun `highway and high speed rail are not flight`() {
        val detector = FlightDetector()
        assertFalse(detector.onFix(Fix(33f, 50.0, 0)))
        assertFalse(detector.onFix(Fix(83f, 20.0, minute)))
        assertFalse(detector.onFix(Fix(83f, 20.0, 2 * minute)))
    }

    @Test
    fun `cruise speed is flight at any altitude once confirmed`() {
        val detector = FlightDetector()
        assertFalse(detector.onFix(Fix(230f, null, 0)))
        assertTrue(detector.onFix(Fix(230f, null, 3 * minute)))
    }

    @Test
    fun `one bad satellite fix is not a flight`() {
        val detector = FlightDetector()
        assertFalse(detector.onFix(Fix(150f, null, 0)))
        assertFalse(detector.onFix(Fix(20f, null, 3 * minute)))
        assertFalse(detector.onFix(Fix(150f, null, 6 * minute)))
    }

    @Test
    fun `climb needs both speed and altitude`() {
        val low = FlightDetector()
        low.onFix(Fix(70f, 900.0, 0))
        assertFalse(low.onFix(Fix(70f, 900.0, minute)))
        val high = FlightDetector()
        high.onFix(Fix(70f, 2_000.0, 0))
        assertTrue(high.onFix(Fix(70f, 2_000.0, minute)))
    }

    @Test
    fun `flight holds through fixes without speed and ends on a slow fix`() {
        val detector = FlightDetector()
        detector.onFix(Fix(230f, 11_000.0, 0))
        detector.onFix(Fix(230f, 11_000.0, minute))
        assertTrue(detector.onFix(Fix(null, 11_000.0, 2 * minute)))
        assertTrue(detector.onFix(Fix(60f, 300.0, 3 * minute)))
        assertFalse(detector.onFix(Fix(8f, 10.0, 4 * minute)))
    }

    @Test
    fun `flight no evidence has confirmed for a while ends`() {
        val detector = FlightDetector()
        detector.onFix(Fix(230f, 11_000.0, 0))
        detector.onFix(Fix(230f, 11_000.0, minute))
        assertTrue(detector.onFix(Fix(null, null, 20 * minute)))
        assertFalse(detector.onFix(Fix(null, null, 21 * minute)))
    }

    @Test
    fun `a cabin climbing after takeoff is flight without any fix`() {
        val detector = FlightDetector()
        assertFalse(detector.pressure(1_012f, 1_012f, minutes = 15, startMinute = 0))
        // About 400 ft a minute of cabin climb, from sea level to 2,000 m.
        assertTrue(detector.pressure(1_012f, 795f, minutes = 15, startMinute = 15))
        assertTrue(detector.pressure(795f, 794f, minutes = 120, startMinute = 30))
    }

    @Test
    fun `a mountain road is not flight`() {
        val detector = FlightDetector()
        // 1,700 m of climb in 45 minutes, steeper than Genting or Stelvio on average.
        assertFalse(detector.pressure(1_000f, 815f, minutes = 45, startMinute = 0))
    }

    @Test
    fun `weather and a skyscraper lift are not flight`() {
        val detector = FlightDetector()
        assertFalse(detector.pressure(1_012f, 1_004f, minutes = 60, startMinute = 0))
        assertFalse(detector.pressure(1_004f, 940f, minutes = 2, startMinute = 60))
    }

    @Test
    fun `a slow fix at cruise does not end a flight the cabin still shows`() {
        val detector = FlightDetector()
        detector.pressure(1_012f, 1_012f, minutes = 12, startMinute = 0)
        detector.pressure(1_012f, 790f, minutes = 15, startMinute = 12)
        assertTrue(detector.onFix(Fix(0f, null, 27 * minute)))
    }

    @Test
    fun `a flight ends once the cabin has descended and settled on the ground`() {
        val detector = FlightDetector()
        detector.pressure(1_012f, 1_012f, minutes = 12, startMinute = 0)
        detector.pressure(1_012f, 790f, minutes = 15, startMinute = 12)
        detector.pressure(790f, 790f, minutes = 60, startMinute = 27)
        // Landing at an airfield higher than the departure: the cabin rises less than it fell.
        assertTrue(detector.pressure(790f, 900f, minutes = 15, startMinute = 87))
        assertFalse(detector.pressure(900f, 901f, minutes = 11, startMinute = 102))
        // The same climb does not start another flight.
        assertFalse(detector.pressure(901f, 901f, minutes = 30, startMinute = 113))
    }
}
