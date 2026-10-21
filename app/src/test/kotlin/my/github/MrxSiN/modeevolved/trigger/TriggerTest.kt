package my.github.MrxSiN.modeevolved.trigger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerTest {

    private val home = GeoPoint(3.1390, 101.6869)

    @Test
    fun `wifi trigger matches connected ssid only`() {
        val trigger = WifiTrigger("Home")
        assertTrue(trigger.isMetBy(DeviceState(wifiNetworks = setOf("Home"))))
        assertFalse(trigger.isMetBy(DeviceState(wifiNetworks = setOf("Office"))))
        assertFalse(trigger.isMetBy(DeviceState()))
    }

    @Test
    fun `bluetooth trigger ignores address case`() {
        val trigger = BluetoothTrigger("aa:bb:cc:dd:ee:ff", "Car")
        assertTrue(trigger.isMetBy(DeviceState(bluetoothDevices = setOf("AA:BB:CC:DD:EE:FF"))))
    }

    @Test
    fun `area trigger uses radius`() {
        val trigger = AreaTrigger(home, radiusMeters = 150.0)
        val near = GeoPoint(3.1400, 101.6869) // about 111 m north
        val far = GeoPoint(3.1420, 101.6869) // about 334 m north
        assertTrue(trigger.isMetBy(at(near)))
        assertFalse(trigger.isMetBy(at(far)))
        assertFalse(trigger.isMetBy(DeviceState()))
    }

    @Test
    fun `leaving an area needs a quarter more than arriving did`() {
        val trigger = AreaTrigger(home, radiusMeters = 150.0)
        val justOutside = GeoPoint(3.1405, 101.6869) // about 167 m north

        // Between the 150 m entry boundary and the 187.5 m exit boundary.
        assertFalse(trigger.isMetBy(at(justOutside), wasMet = false))
        assertTrue(trigger.isMetBy(at(justOutside), wasMet = true))
    }

    @Test
    fun `a fix less precise than the area widens the exit boundary to its own accuracy`() {
        val trigger = AreaTrigger(home, radiusMeters = 50.0)
        val north = GeoPoint(3.1400, 101.6869) // about 111 m north

        // A 100 m fix must be more than 150 m out before it can say the phone left.
        assertTrue(trigger.isMetBy(at(north, accuracy = 100.0), wasMet = true))
        // A precise fix at the same place has left: its boundary is only 62.5 m.
        assertFalse(trigger.isMetBy(at(north, accuracy = 5.0), wasMet = true))
    }

    @Test
    fun `an unknown position is never inside, whatever was answered before`() {
        val trigger = AreaTrigger(home, radiusMeters = 150.0)
        assertFalse(trigger.isMetBy(DeviceState(), wasMet = true))
    }

    private fun at(point: GeoPoint, accuracy: Double? = null) =
        DeviceState(location = LocationSample(point, accuracyMeters = accuracy))

    @Test
    fun `haversine distance is accurate`() {
        // One degree of latitude is about 111.2 km.
        assertEquals(111_195.0, GeoPoint(0.0, 0.0).distanceTo(GeoPoint(1.0, 0.0)), 50.0)
    }

    @Test
    fun `movement trigger treats unknown as not met`() {
        assertFalse(MovementTrigger(Movement.STILL).isMetBy(DeviceState()))
        assertTrue(MovementTrigger(Movement.STILL).isMetBy(DeviceState(movement = Movement.STILL)))
    }

    @Test
    fun `flying trigger reads the flight signal, not the motion sensor`() {
        val trigger = MovementTrigger(Movement.FLYING)
        assertEquals(Signal.FLIGHT, trigger.signal)
        assertTrue(trigger.isMetBy(DeviceState(flying = true, movement = Movement.STILL)))
        assertFalse(trigger.isMetBy(DeviceState(flying = false)))
        assertFalse(trigger.isMetBy(DeviceState()))
    }

    @Test
    fun `airplane trigger follows the setting and treats unknown as not met`() {
        assertTrue(AirplaneTrigger(on = true).isMetBy(DeviceState(airplaneMode = true)))
        assertFalse(AirplaneTrigger(on = true).isMetBy(DeviceState(airplaneMode = false)))
        assertTrue(AirplaneTrigger(on = false).isMetBy(DeviceState(airplaneMode = false)))
        assertFalse(AirplaneTrigger(on = false).isMetBy(DeviceState()))
    }


    @Test
    fun `headphones are met only when known and matching`() {
        assertTrue(HeadphonesTrigger(connected = true).isMetBy(DeviceState(headphonesConnected = true)))
        assertFalse(HeadphonesTrigger(connected = false).isMetBy(DeviceState(headphonesConnected = true)))
        assertFalse(HeadphonesTrigger(connected = false).isMetBy(DeviceState()))
    }

    @Test
    fun `power is met only when known and matching`() {
        assertTrue(PowerTrigger(connected = true).isMetBy(DeviceState(powerConnected = true)))
        assertFalse(PowerTrigger(connected = true).isMetBy(DeviceState(powerConnected = false)))
        assertTrue(PowerTrigger(connected = false).isMetBy(DeviceState(powerConnected = false)))
        // Unknown is neither: a source that has not answered meets no trigger.
        assertFalse(PowerTrigger(connected = false).isMetBy(DeviceState()))
    }
}
