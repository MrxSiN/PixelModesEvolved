package my.github.MrxSiN.modeevolved.action

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

import my.github.MrxSiN.modeevolved.core.SilentLogger

class SystemSettingActionTest {

    private val codec = ActionCodec.standard()

    @Test
    fun `a system setting survives storage`() {
        val list = ModeActions("m", listOf(SystemSettingAction(SystemSetting.MEDIA_VOLUME, 70, restore = false)))
        assertEquals(listOf(list), codec.decode(codec.encode(listOf(list))))
    }

    @Test
    fun `actions stored before system settings existed read the same`() {
        val old = """{"m":{"actions":[{"type":"launchApp","timing":"START","cooldown":0,"package":"p","label":"P"}]}}"""
        assertEquals(listOf(ModeActions("m", listOf(LaunchAppAction(Timing.START, "p", "P")))), codec.decode(old))
    }

    @Test
    fun `an unknown setting or a value out of range is dropped alone`() {
        val text = JSONObject().put(
            "m",
            JSONObject().put(
                "actions",
                org.json.JSONArray()
                    .put(JSONObject().put("type", "systemSetting").put("setting", "warpDrive").put("value", 1))
                    .put(JSONObject().put("type", "systemSetting").put("setting", "brightness").put("value", 400))
                    .put(JSONObject().put("type", "systemSetting").put("setting", "screenTimeout").put("value", 12345))
                    .put(JSONObject().put("type", "systemSetting").put("setting", "wifi").put("value", 0)),
            ),
        ).toString()
        assertEquals(listOf(SystemSettingAction(SystemSetting.WIFI, 0)), codec.decode(text).single().items)
    }

    @Test
    fun `restore defaults to on for a record without it`() {
        val text = """{"m":{"actions":[{"type":"systemSetting","setting":"nfc","value":0}]}}"""
        assertTrue((codec.decode(text).single().items.single() as SystemSettingAction).restore)
    }

    @Test
    fun `system settings are held, never run`() {
        val list = ModeActions("m", listOf(SystemSettingAction(SystemSetting.WIFI, 0), TaskerTaskAction(Timing.START, "t")))
        assertEquals(listOf(TaskerTaskAction(Timing.START, "t")), list.at(Timing.START))
        assertEquals(listOf(SystemSettingAction(SystemSetting.WIFI, 0)), list.claims)
    }

    @Test
    fun `every setting has a valid default and a stable unique id`() {
        assertEquals(SystemSetting.entries.size, SystemSetting.entries.map { it.id }.toSet().size)
        SystemSetting.entries.forEach { assertTrue(it.id, it.value.isValid(it.defaultValue)) }
    }

    @Test
    fun `the brightness curve matches the slider at its ends and middle`() {
        assertEquals(0.1f, Brightness.percentToLinear(0, 0.1f, 1f), 1e-6f)
        assertEquals(1f, Brightness.percentToLinear(100, 0.1f, 1f), 1e-4f)
        // SettingsLib's HLG: the slider's middle is 1/12 of the way up the linear range.
        assertEquals(0.1f + 0.9f / 12f, Brightness.percentToLinear(50, 0.1f, 1f), 1e-6f)
    }

    // Through the automation: Mode transitions, boot and edits.

    private val device = FakeSettings()
    private var stored: String? = null
    private val store = FakeSource(listOf(ModeActions("m", listOf(SystemSettingAction(SystemSetting.BRIGHTNESS, 30)))))
    private val states = FakeStates()

    private fun automation(): ActionAutomation {
        val claims = SettingClaims(device, SilentLogger) { stored = it }.apply { load(stored) }
        return ActionAutomation(store, states, { _, _, _ -> error("system settings are never run") }, Runnable::run, { 0L }, SilentLogger, claims)
            .also { it.start() }
    }

    @Test
    fun `a mode on at boot is not applied, and is restored when it later ends only if it held the setting`() {
        device[SystemSetting.BRIGHTNESS] = 70
        states.set("m", true)
        automation()
        assertEquals(emptyList<Any>(), device.writes)
        states.set("m", false)
        assertEquals(emptyList<Any>(), device.writes)
    }

    @Test
    fun `a mode on across an update keeps its claim without writing, and restores when it ends`() {
        device[SystemSetting.BRIGHTNESS] = 70
        states.set("m", false)
        automation()
        states.set("m", true)
        assertEquals(30, device[SystemSetting.BRIGHTNESS])

        val next = automation()
        assertEquals(1, device.writes.size)
        states.set("m", false)
        assertEquals(70, device[SystemSetting.BRIGHTNESS])
        next.stop()
    }

    @Test
    fun `saving, changing and removing a setting while the mode is on`() {
        device[SystemSetting.BRIGHTNESS] = 70
        states.set("m", true)
        store.lists = emptyList()
        automation()

        store.replace(listOf(ModeActions("m", listOf(SystemSettingAction(SystemSetting.BRIGHTNESS, 30)))))
        assertEquals(30, device[SystemSetting.BRIGHTNESS])

        store.replace(listOf(ModeActions("m", listOf(SystemSettingAction(SystemSetting.BRIGHTNESS, 40)))))
        assertEquals(40, device[SystemSetting.BRIGHTNESS])

        store.replace(emptyList())
        assertEquals(70, device[SystemSetting.BRIGHTNESS])
    }

    @Test
    fun `a mode deleted while on gives its settings back`() {
        device[SystemSetting.BRIGHTNESS] = 70
        states.set("m", false)
        automation()
        states.set("m", true)
        states.set("m", null)
        assertEquals(70, device[SystemSetting.BRIGHTNESS])
    }
}
