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
        assertTrue(trigger.isMetBy(DeviceState(location = near)))
        assertFalse(trigger.isMetBy(DeviceState(location = far)))
        assertFalse(trigger.isMetBy(DeviceState()))
    }

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

}
