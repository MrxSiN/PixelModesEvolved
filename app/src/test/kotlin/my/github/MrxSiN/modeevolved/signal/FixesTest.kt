package my.github.MrxSiN.modeevolved.signal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

import my.github.MrxSiN.modeevolved.trigger.GeoPoint
import my.github.MrxSiN.modeevolved.trigger.LocationSample

/** How old a position fix is, which is what decides whether it may be acted on. */
class FixesTest {

    private val now = 5_000_000_000L // five seconds on the monotonic clock

    @Test
    fun `age is the gap between the fix and now, in milliseconds`() {
        assertEquals(3_000L, Fixes.ageMillis(now - 3_000_000_000L, now))
        assertEquals(0L, Fixes.ageMillis(now, now))
    }

    @Test
    fun `a fix that reads as newer than now has no age`() {
        assertEquals(0L, Fixes.ageMillis(now + 1_000_000L, now))
    }

    @Test
    fun `an hours-old last known fix is far outside what a location rule accepts`() {
        val hours = 4 * 60 * 60_000L
        val sample = LocationSample(GeoPoint(3.139, 101.6869), accuracyMeters = 20.0, ageMillis = hours)
        assertFalse(sample.isFreshWithin(10 * 60_000L))
        assertTrue(sample.isFreshWithin(hours))
    }

    @Test
    fun `a fix exactly at the limit is still accepted`() {
        val sample = LocationSample(GeoPoint(0.0, 0.0), ageMillis = 10 * 60_000L)
        assertTrue(sample.isFreshWithin(10 * 60_000L))
    }
}
