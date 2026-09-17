package my.github.MrxSiN.modeevolved.trigger

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

import my.github.MrxSiN.modeevolved.trigger.FlightDetector.Fix

class FlightDetectorTest {

    @Test
    fun `highway and high speed rail are not flight`() {
        val detector = FlightDetector()
        assertFalse(detector.onFix(Fix(33f, 50.0)))
        assertFalse(detector.onFix(Fix(83f, 20.0)))
    }

    @Test
    fun `cruise speed is flight at any altitude`() {
        assertTrue(FlightDetector().onFix(Fix(230f, null)))
    }

    @Test
    fun `climb needs both speed and altitude`() {
        assertFalse(FlightDetector().onFix(Fix(70f, 900.0)))
        assertTrue(FlightDetector().onFix(Fix(70f, 2_000.0)))
    }

    @Test
    fun `flight holds through fixes without speed and ends on a slow fix`() {
        val detector = FlightDetector()
        detector.onFix(Fix(230f, 11_000.0))
        assertTrue(detector.onFix(Fix(null, 11_000.0)))
        assertTrue(detector.onFix(Fix(60f, 300.0)))
        assertFalse(detector.onFix(Fix(8f, 10.0)))
    }
}
