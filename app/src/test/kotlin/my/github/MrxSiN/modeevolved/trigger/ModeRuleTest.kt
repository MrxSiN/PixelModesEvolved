package my.github.MrxSiN.modeevolved.trigger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

import my.github.MrxSiN.modeevolved.trigger.ModeRule.Join.AND
import my.github.MrxSiN.modeevolved.trigger.ModeRule.Join.OR

class ModeRuleTest {

    private val home = WifiTrigger("Home")
    private val still = MovementTrigger(Movement.STILL)
    private val car = BluetoothTrigger("AA", "Car")

    @Test
    fun `and binds before or`() {
        // home and still or car == (home and still) or car
        val rule = ModeRule("m", listOf(home, still, car), listOf(AND, OR))
        assertEquals(listOf(listOf(home, still), listOf(car)), rule.groups)

        assertTrue(rule.isMetBy(DeviceState(wifiNetworks = setOf("Home"), movement = Movement.STILL)))
        assertTrue(rule.isMetBy(DeviceState(bluetoothDevices = setOf("AA"))))
        assertFalse(rule.isMetBy(DeviceState(wifiNetworks = setOf("Home"))))
    }

    @Test
    fun `or before and`() {
        // home or still and car == home or (still and car)
        val rule = ModeRule("m", listOf(home, still, car), listOf(OR, AND))
        assertTrue(rule.isMetBy(DeviceState(wifiNetworks = setOf("Home"))))
        assertFalse(rule.isMetBy(DeviceState(movement = Movement.STILL)))
        assertTrue(rule.isMetBy(DeviceState(movement = Movement.STILL, bluetoothDevices = setOf("AA"))))
    }

    @Test
    fun `empty rule is never met`() {
        assertFalse(ModeRule("m").isMetBy(DeviceState()))
        assertEquals(emptyList<List<Trigger>>(), ModeRule("m").groups)
    }

    @Test
    fun `plus appends with and and ignores duplicates`() {
        val rule = ModeRule("m").plus(home).plus(still).plus(home)
        assertEquals(listOf(home, still), rule.triggers)
        assertEquals(listOf(AND), rule.joins)
    }

    @Test
    fun `minus takes the join before, or after for the first`() {
        val rule = ModeRule("m", listOf(home, still, car), listOf(OR, AND))
        assertEquals(ModeRule("m", listOf(home, car), listOf(OR)), rule.minus(still))
        assertEquals(ModeRule("m", listOf(still, car), listOf(AND)), rule.minus(home))
        assertEquals(ModeRule("m", listOf(home, still), listOf(OR)), rule.minus(car))
    }

    @Test
    fun `replace keeps position and joins`() {
        val rule = ModeRule("m", listOf(home, still), listOf(OR))
        val office = WifiTrigger("Office")
        assertEquals(ModeRule("m", listOf(office, still), listOf(OR)), rule.replace(home, office))
        // Replacing with a trigger already present leaves one copy.
        assertEquals(ModeRule("m", listOf(still)), rule.replace(home, still))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `joins must fit the triggers`() {
        ModeRule("m", listOf(home, still), emptyList())
    }
}
