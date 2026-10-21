package my.github.MrxSiN.modeevolved.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

import my.github.MrxSiN.modeevolved.core.SilentLogger
import my.github.MrxSiN.modeevolved.trigger.DeviceState
import my.github.MrxSiN.modeevolved.trigger.ModeRule
import my.github.MrxSiN.modeevolved.trigger.Signal
import my.github.MrxSiN.modeevolved.trigger.WifiTrigger

class DecisionsTest {

    private val told = LinkedHashMap<String, Decision>()
    private val delays = TestDelays()
    private val engine = TriggerEngine({ _, _, _ -> }, delays, SilentLogger, explain = { id, d -> told[id] = d }, now = { 42L })

    private val home = ModeRule("home", listOf(WifiTrigger("Home")))

    @Test
    fun `a switch says it was the triggers, and which kind`() {
        engine.onRulesChanged(listOf(home))
        engine.onStateChanged(DeviceState(wifiNetworks = setOf("Home")))
        assertEquals(Decision(true, Decision.Cause.SIGNALS, setOf(Signal.WIFI), 42L), told["home"])
    }

    @Test
    fun `saving and removing are told apart from signals`() {
        engine.onRulesChanged(listOf(home))
        engine.onRulesChanged(listOf(home.copy(triggers = listOf(WifiTrigger("Office")))))
        assertEquals(Decision.Cause.SAVED, told["home"]?.cause)

        engine.onStateChanged(DeviceState(wifiNetworks = setOf("Office")))
        engine.onRulesChanged(emptyList())
        assertEquals(Decision(false, Decision.Cause.REMOVED, emptySet(), 42L), told["home"])
    }

    @Test
    fun `a waited edge is told as waiting`() {
        engine.onRulesChanged(listOf(home.withStability(5_000)))
        engine.onStateChanged(DeviceState(wifiNetworks = setOf("Home")))
        assertEquals(Decision(true, Decision.Cause.WAITING, setOf(Signal.WIFI), 42L), told["home"])
    }

    @Test
    fun `the stored text names no network`() {
        val text = DecisionCodec.encode(mapOf("home" to Decision(true, Decision.Cause.SIGNALS, setOf(Signal.WIFI), 1L)))
        assertFalse(text.contains("Home"))
        assertEquals(mapOf("home" to Decision(true, Decision.Cause.SIGNALS, setOf(Signal.WIFI), 1L)), DecisionCodec.decode(text))
    }
}
