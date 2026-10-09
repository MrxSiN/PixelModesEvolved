package my.github.MrxSiN.modeevolved.action

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

import my.github.MrxSiN.modeevolved.action.SystemSetting.BLUETOOTH
import my.github.MrxSiN.modeevolved.action.SystemSetting.BRIGHTNESS
import my.github.MrxSiN.modeevolved.action.SystemSetting.MEDIA_VOLUME
import my.github.MrxSiN.modeevolved.action.SystemSetting.MOBILE_DATA
import my.github.MrxSiN.modeevolved.action.SystemSetting.NFC
import my.github.MrxSiN.modeevolved.core.SilentLogger

/** A device whose settings are numbers, counting what is written. */
class FakeSettings : SettingBackend {
    val values = IntArray(SystemSetting.entries.size)
    val writes = mutableListOf<Pair<SystemSetting, Int>>()
    val broken = mutableSetOf<SystemSetting>()
    var dataSubscription = 1

    operator fun get(setting: SystemSetting) = values[setting.ordinal]
    operator fun set(setting: SystemSetting, value: Int) { values[setting.ordinal] = value }

    override fun read(setting: SystemSetting): Int {
        check(setting !in broken) { "unavailable" }
        return values[setting.ordinal]
    }

    override fun write(setting: SystemSetting, native: Int): Int {
        check(setting !in broken) { "unavailable" }
        writes += setting to native
        values[setting.ordinal] = native
        return native
    }

    override fun toNative(setting: SystemSetting, value: Int) = value

    override fun target(setting: SystemSetting) = if (setting == MOBILE_DATA) dataSubscription else 0
}

class SettingClaimsTest {

    private val device = FakeSettings()
    private var stored: String? = null
    private val claims = SettingClaims(device, SilentLogger) { stored = it }

    private fun set(setting: SystemSetting, value: Int, restore: Boolean = true) = SystemSettingAction(setting, value, restore)

    // Single claim.

    @Test
    fun `turning on applies and turning off restores`() {
        device[BRIGHTNESS] = 70
        claims.activate("a", listOf(set(BRIGHTNESS, 30)))
        assertEquals(30, device[BRIGHTNESS])
        claims.deactivate("a")
        assertEquals(70, device[BRIGHTNESS])
        assertEquals(listOf(BRIGHTNESS to 30, BRIGHTNESS to 70), device.writes)
    }

    @Test
    fun `a setting already at the value is not written`() {
        device[BLUETOOTH] = 1
        claims.activate("a", listOf(set(BLUETOOTH, 1)))
        claims.deactivate("a")
        assertEquals(emptyList<Any>(), device.writes)
    }

    @Test
    fun `without restore the value stays after the mode ends`() {
        device[BRIGHTNESS] = 70
        claims.activate("a", listOf(set(BRIGHTNESS, 30, restore = false)))
        claims.deactivate("a")
        assertEquals(30, device[BRIGHTNESS])
    }

    // Someone else changed it.

    @Test
    fun `a value changed while claimed is left when the mode ends`() {
        device[BRIGHTNESS] = 70
        claims.activate("a", listOf(set(BRIGHTNESS, 30)))
        device[BRIGHTNESS] = 50
        claims.deactivate("a")
        assertEquals(50, device[BRIGHTNESS])
    }

    @Test
    fun `wifi turned back on by hand is not forced back`() {
        device[SystemSetting.WIFI] = 1
        claims.activate("a", listOf(set(SystemSetting.WIFI, 0)))
        device[SystemSetting.WIFI] = 1
        claims.deactivate("a")
        assertEquals(1, device[SystemSetting.WIFI])
        assertEquals(1, device.writes.size)
    }

    // Overlapping Modes.

    @Test
    fun `the latest mode wins and the earlier one comes back, then the baseline`() {
        device[BRIGHTNESS] = 70
        claims.activate("a", listOf(set(BRIGHTNESS, 30)))
        claims.activate("b", listOf(set(BRIGHTNESS, 80)))
        assertEquals(80, device[BRIGHTNESS])
        claims.deactivate("b")
        assertEquals(30, device[BRIGHTNESS])
        claims.deactivate("a")
        assertEquals(70, device[BRIGHTNESS])
    }

    @Test
    fun `an earlier mode ending under a later one writes nothing`() {
        device[BRIGHTNESS] = 70
        claims.activate("a", listOf(set(BRIGHTNESS, 30)))
        claims.activate("b", listOf(set(BRIGHTNESS, 80)))
        claims.deactivate("a")
        assertEquals(listOf(BRIGHTNESS to 30, BRIGHTNESS to 80), device.writes)
        claims.deactivate("b")
        assertEquals(70, device[BRIGHTNESS])
    }

    @Test
    fun `turning a mode on again makes it the winner again`() {
        device[BRIGHTNESS] = 70
        claims.activate("a", listOf(set(BRIGHTNESS, 30)))
        claims.activate("b", listOf(set(BRIGHTNESS, 80)))
        claims.deactivate("a")
        claims.activate("a", listOf(set(BRIGHTNESS, 30)))
        assertEquals(30, device[BRIGHTNESS])
        claims.deactivate("a")
        assertEquals(80, device[BRIGHTNESS])
        claims.deactivate("b")
        assertEquals(70, device[BRIGHTNESS])
    }

    @Test
    fun `a change by hand is not overwritten when the winner hands over`() {
        device[BRIGHTNESS] = 70
        claims.activate("a", listOf(set(BRIGHTNESS, 30)))
        claims.activate("b", listOf(set(BRIGHTNESS, 80)))
        device[BRIGHTNESS] = 55
        claims.deactivate("b")
        assertEquals(55, device[BRIGHTNESS])
        claims.deactivate("a")
        assertEquals(55, device[BRIGHTNESS])
    }

    @Test
    fun `a mode turning on applies over a change by hand, and the baseline is kept`() {
        device[BRIGHTNESS] = 70
        claims.activate("a", listOf(set(BRIGHTNESS, 30)))
        device[BRIGHTNESS] = 55
        claims.activate("b", listOf(set(BRIGHTNESS, 80)))
        assertEquals(80, device[BRIGHTNESS])
        claims.deactivate("b")
        assertEquals(30, device[BRIGHTNESS])
        claims.deactivate("a")
        assertEquals(70, device[BRIGHTNESS])
    }

    // Several settings.

    @Test
    fun `one setting failing leaves the others`() {
        device.broken += NFC
        device[BRIGHTNESS] = 70
        claims.activate("a", listOf(set(BRIGHTNESS, 30), set(NFC, 0), set(MEDIA_VOLUME, 5), set(BLUETOOTH, 1)))
        assertEquals(30, device[BRIGHTNESS])
        assertEquals(5, device[MEDIA_VOLUME])
        assertEquals(1, device[BLUETOOTH])
        claims.deactivate("a")
        assertEquals(70, device[BRIGHTNESS])
        assertEquals(0, device[MEDIA_VOLUME])
        assertEquals(0, device[BLUETOOTH])
    }

    /** As Android does: a ring volume above zero puts the ringer on Ring. */
    private class CoupledRinger : SettingBackend by FakeSettings() {
        val device = FakeSettings()
        override fun read(setting: SystemSetting) = device.read(setting)
        override fun toNative(setting: SystemSetting, value: Int) = value
        override fun write(setting: SystemSetting, native: Int): Int {
            device.write(setting, native)
            if (setting == SystemSetting.RING_VOLUME && native > 0) device[SystemSetting.RINGER_MODE] = SystemSetting.RINGER_NORMAL
            return native
        }
    }

    @Test
    fun `baselines are read before anything is written, and the ringer is given back last`() {
        val coupled = CoupledRinger()
        coupled.device[SystemSetting.RINGER_MODE] = SystemSetting.RINGER_VIBRATE
        coupled.device[SystemSetting.RING_VOLUME] = 2
        val claims = SettingClaims(coupled, SilentLogger) {}
        claims.activate("a", listOf(set(SystemSetting.RING_VOLUME, 5), set(SystemSetting.RINGER_MODE, SystemSetting.RINGER_NORMAL)))
        claims.deactivate("a")
        assertEquals(SystemSetting.RINGER_VIBRATE, coupled.device[SystemSetting.RINGER_MODE])
        assertEquals(2, coupled.device[SystemSetting.RING_VOLUME])
    }

    // Edits while on.

    @Test
    fun `a claim saved while the mode is on applies at once`() {
        device[BRIGHTNESS] = 70
        claims.activate("a", listOf(set(BRIGHTNESS, 30)))
        claims.update("a", listOf(set(BRIGHTNESS, 40)))
        assertEquals(40, device[BRIGHTNESS])
        claims.deactivate("a")
        assertEquals(70, device[BRIGHTNESS])
    }

    @Test
    fun `a claim removed while the mode is on gives the setting back`() {
        device[BRIGHTNESS] = 70
        device[BLUETOOTH] = 1
        claims.activate("a", listOf(set(BRIGHTNESS, 30), set(BLUETOOTH, 0)))
        claims.update("a", listOf(set(BLUETOOTH, 0)))
        assertEquals(70, device[BRIGHTNESS])
        assertEquals(0, device[BLUETOOTH])
    }

    @Test
    fun `an unchanged claim is not written again on reload`() {
        claims.activate("a", listOf(set(BRIGHTNESS, 30)))
        claims.update("a", listOf(set(BRIGHTNESS, 30)))
        assertEquals(1, device.writes.size)
    }

    // Mobile data follows its subscription.

    @Test
    fun `mobile data is not restored onto another subscription`() {
        device[MOBILE_DATA] = 1
        claims.activate("a", listOf(set(MOBILE_DATA, 0)))
        device.dataSubscription = 2
        claims.deactivate("a")
        assertEquals(0, device[MOBILE_DATA])
    }

    // Reboot and update.

    private fun restarted(): SettingClaims = SettingClaims(device, SilentLogger) { stored = it }.apply { load(stored) }

    @Test
    fun `a mode still on after a restart keeps its claim and nothing is written`() {
        device[BRIGHTNESS] = 70
        claims.activate("a", listOf(set(BRIGHTNESS, 30)))
        val after = restarted()
        after.adopt("a", listOf(set(BRIGHTNESS, 30)))
        after.finishLoad()
        assertEquals(1, device.writes.size)
        after.deactivate("a")
        assertEquals(70, device[BRIGHTNESS])
    }

    @Test
    fun `a mode that ended while nothing ran is restored at load`() {
        device[BRIGHTNESS] = 70
        claims.activate("a", listOf(set(BRIGHTNESS, 30)))
        val after = restarted()
        after.finishLoad()
        assertEquals(70, device[BRIGHTNESS])
        assertEquals("", stored)
    }

    @Test
    fun `a setting the restart reset is not restored`() {
        device[BRIGHTNESS] = 70
        claims.activate("a", listOf(set(BRIGHTNESS, 30)))
        device[BRIGHTNESS] = 45
        val after = restarted()
        after.finishLoad()
        assertEquals(45, device[BRIGHTNESS])
    }

    @Test
    fun `a mode on at load that held nothing before is not applied`() {
        val after = restarted()
        after.adopt("a", listOf(set(BRIGHTNESS, 30)))
        after.finishLoad()
        assertEquals(emptyList<Any>(), device.writes)
        assertNull(stored)
    }

    @Test
    fun `nothing is stored for someone who never claims a setting`() {
        claims.deactivate("a")
        claims.update("a", emptyList())
        claims.finishLoad()
        assertNull(stored)
    }
}
