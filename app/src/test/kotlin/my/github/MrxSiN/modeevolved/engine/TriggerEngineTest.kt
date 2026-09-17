package my.github.MrxSiN.modeevolved.engine

import org.junit.Assert.assertEquals
import org.junit.Test

import my.github.MrxSiN.modeevolved.core.SilentLogger
import my.github.MrxSiN.modeevolved.trigger.DeviceState
import my.github.MrxSiN.modeevolved.trigger.ModeRule
import my.github.MrxSiN.modeevolved.trigger.WifiTrigger

class TriggerEngineTest {

    private data class Switch(val modeId: String, val active: Boolean, val reset: Boolean)

    private val switches = mutableListOf<Switch>()
    private val engine = TriggerEngine({ id, active, reset -> switches += Switch(id, active, reset) }, SilentLogger)

    private val home = ModeRule("home", listOf(WifiTrigger("Home")))
    private val atHome = DeviceState(wifiNetworks = setOf("Home"))
    private val away = DeviceState()

    @Test
    fun `unmet rule at load does not switch off`() {
        engine.onRulesChanged(listOf(home))
        engine.onStateChanged(away)
        assertEquals(emptyList<Switch>(), switches)
    }

    @Test
    fun `met rule at load switches on without reset`() {
        engine.onStateChanged(atHome)
        engine.onRulesChanged(listOf(home))
        assertEquals(listOf(Switch("home", true, false)), switches)
    }

    @Test
    fun `switches only on edges`() {
        engine.onRulesChanged(listOf(home))
        engine.onStateChanged(atHome)
        engine.onStateChanged(atHome.copy(bluetoothDevices = setOf("AA")))
        engine.onStateChanged(away)
        engine.onStateChanged(away.copy(bluetoothDevices = setOf("BB")))
        assertEquals(listOf(Switch("home", true, false), Switch("home", false, false)), switches)
    }

    @Test
    fun `unchanged rule keeps its edge across reloads`() {
        engine.onRulesChanged(listOf(home))
        engine.onStateChanged(atHome)
        engine.onRulesChanged(listOf(home))
        assertEquals(listOf(Switch("home", true, false)), switches)
    }

    @Test
    fun `rule saved after load applies at once with reset, met or not`() {
        engine.onRulesChanged(emptyList())
        engine.onStateChanged(atHome)
        engine.onRulesChanged(listOf(home, ModeRule("office", listOf(WifiTrigger("Office")))))
        assertEquals(setOf(Switch("home", true, true), Switch("office", false, true)), switches.toSet())
    }

    @Test
    fun `removed rule that held its mode on lets it go`() {
        engine.onRulesChanged(listOf(home))
        engine.onStateChanged(atHome)
        engine.onRulesChanged(emptyList())
        assertEquals(listOf(Switch("home", true, false), Switch("home", false, false)), switches)
    }

    @Test
    fun `failing controller does not stop other modes`() {
        val calls = mutableListOf<String>()
        val flaky = TriggerEngine({ id, _, _ -> calls += id; if (id == "a") error("boom") }, SilentLogger)
        flaky.onRulesChanged(listOf(home.copy(modeId = "a"), home.copy(modeId = "b")))
        flaky.onStateChanged(atHome)
        assertEquals(setOf("a", "b"), calls.toSet())
    }
}
