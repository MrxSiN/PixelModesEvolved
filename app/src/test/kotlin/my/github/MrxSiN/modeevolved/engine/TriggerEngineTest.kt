package my.github.MrxSiN.modeevolved.engine

import org.junit.Assert.assertEquals
import org.junit.Test

import my.github.MrxSiN.modeevolved.core.SilentLogger
import my.github.MrxSiN.modeevolved.trigger.AreaTrigger
import my.github.MrxSiN.modeevolved.trigger.DeviceState
import my.github.MrxSiN.modeevolved.trigger.GeoPoint
import my.github.MrxSiN.modeevolved.trigger.LocationSample
import my.github.MrxSiN.modeevolved.trigger.ModeRule
import my.github.MrxSiN.modeevolved.trigger.WifiTrigger

class TriggerEngineTest {

    private data class Switch(val modeId: String, val active: Boolean, val reset: Boolean)

    private val switches = mutableListOf<Switch>()
    private val delays = TestDelays()
    private val engine = TriggerEngine({ id, active, reset -> switches += Switch(id, active, reset) }, delays, SilentLogger)

    private val home = ModeRule("home", listOf(WifiTrigger("Home")))
    private val atHome = DeviceState(wifiNetworks = setOf("Home"))
    private val away = DeviceState()

    @Test
    fun `unmet rule at load does not switch off`() {
        engine.onRulesChanged(listOf(home))
        engine.onStateChanged(away)
        assertEquals(emptyList<Switch>(), switches)
    }

    /** A Mode this module switched on before a reboot or reload, whose trigger no longer holds. */
    @Test
    fun `unmet rule at load switches off a Mode this module left on`() {
        val left = object : ModeController {
            override fun setActive(modeId: String, active: Boolean, reset: Boolean) {
                switches += Switch(modeId, active, reset)
            }

            override fun switchedOnHere(modeId: String) = modeId == "home"
        }
        val reloaded = TriggerEngine(left, delays, SilentLogger)

        reloaded.onRulesChanged(listOf(home, ModeRule("work", listOf(WifiTrigger("Office")))))
        reloaded.onStateChanged(away)

        // Only the one it had switched on; then it is an ordinary decided rule.
        assertEquals(listOf(Switch("home", false, false)), switches)
        reloaded.onStateChanged(away.copy(bluetoothDevices = setOf("AA")))
        assertEquals(1, switches.size)
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
        val flaky = TriggerEngine({ id, _, _ -> calls += id; if (id == "a") error("boom") }, TestDelays(), SilentLogger)
        flaky.onRulesChanged(listOf(home.copy(modeId = "a"), home.copy(modeId = "b")))
        flaky.onStateChanged(atHome)
        assertEquals(setOf("a", "b"), calls.toSet())
    }

    // Stabilization.

    private val patient = home.withStability(30_000L)

    @Test
    fun `stabilized rule waits out its delay before switching`() {
        engine.onRulesChanged(listOf(patient))
        engine.onStateChanged(away)
        engine.onStateChanged(atHome)
        assertEquals(emptyList<Switch>(), switches)

        delays.advance(29_000L)
        assertEquals(emptyList<Switch>(), switches)

        delays.advance(1_000L)
        assertEquals(listOf(Switch("home", true, false)), switches)
    }

    @Test
    fun `an edge that comes back before the wait is over is dropped`() {
        engine.onRulesChanged(listOf(patient))
        engine.onStateChanged(away)
        engine.onStateChanged(atHome)
        engine.onStateChanged(away)
        delays.advance(60_000L)
        assertEquals(emptyList<Switch>(), switches)
        assertEquals(0, delays.pending)
    }

    @Test
    fun `the first decision after load never waits`() {
        engine.onStateChanged(atHome)
        engine.onRulesChanged(listOf(patient))
        assertEquals(listOf(Switch("home", true, false)), switches)
    }

    @Test
    fun `saving a rule applies at once however long its wait is`() {
        engine.onRulesChanged(emptyList())
        engine.onStateChanged(atHome)
        engine.onRulesChanged(listOf(patient))
        assertEquals(listOf(Switch("home", true, true)), switches)
    }

    @Test
    fun `editing a rule drops the edge its previous version was waiting out`() {
        engine.onRulesChanged(listOf(patient))
        engine.onStateChanged(away)
        engine.onStateChanged(atHome)
        engine.onRulesChanged(listOf(patient.withStability(60_000L)))
        delays.advance(120_000L)
        // One switch, from the edit applying at once, and no second one from the dropped wait.
        assertEquals(listOf(Switch("home", true, true)), switches)
    }

    @Test
    fun `stopping drops every edge still waiting`() {
        engine.onRulesChanged(listOf(patient))
        engine.onStateChanged(away)
        engine.onStateChanged(atHome)
        engine.stop()
        delays.advance(60_000L)
        assertEquals(emptyList<Switch>(), switches)
    }

    @Test
    fun `a wait covers a whole flapping burst rather than restarting on each edge`() {
        engine.onRulesChanged(listOf(patient))
        engine.onStateChanged(away)
        engine.onStateChanged(atHome)
        delays.advance(20_000L)
        // Off and on again inside the wait: the answer at the end is what it was at the start.
        engine.onStateChanged(away)
        engine.onStateChanged(atHome)
        delays.advance(20_000L)
        assertEquals(emptyList<Switch>(), switches)

        delays.advance(20_000L)
        assertEquals(listOf(Switch("home", true, false)), switches)
    }

    // Area hysteresis through the engine, which is where the previous answer is kept.

    @Test
    fun `an area rule does not flap while a coarse fix wanders along its edge`() {
        val center = GeoPoint(3.1390, 101.6869)
        val area = ModeRule("home", listOf(AreaTrigger(center, radiusMeters = 50.0)))
        engine.onRulesChanged(listOf(area))

        engine.onStateChanged(at(center, accuracy = 100.0))
        // 111 m north: past the 50 m radius, inside the widened exit boundary a 100 m fix earns.
        engine.onStateChanged(at(GeoPoint(3.1400, 101.6869), accuracy = 100.0))
        assertEquals(listOf(Switch("home", true, false)), switches)

        // 334 m north: clear of the exit boundary, so the Mode goes off once.
        engine.onStateChanged(at(GeoPoint(3.1420, 101.6869), accuracy = 100.0))
        assertEquals(listOf(Switch("home", true, false), Switch("home", false, false)), switches)
    }

    private fun at(point: GeoPoint, accuracy: Double) =
        DeviceState(location = LocationSample(point, accuracyMeters = accuracy))
}
