package my.github.MrxSiN.modeevolved.engine

import java.util.concurrent.Executor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

import my.github.MrxSiN.modeevolved.core.SilentLogger
import my.github.MrxSiN.modeevolved.store.ModeSource
import my.github.MrxSiN.modeevolved.trigger.ModeRule
import my.github.MrxSiN.modeevolved.trigger.MovementTrigger
import my.github.MrxSiN.modeevolved.trigger.Movement
import my.github.MrxSiN.modeevolved.trigger.Signal
import my.github.MrxSiN.modeevolved.trigger.StateUpdate
import my.github.MrxSiN.modeevolved.trigger.WifiTrigger

/**
 * Signals starting, stopping and starting again as rules change, and what a
 * hot reload's hand-off leaves running.
 */
class ModeAutomationTest {

    private data class Switch(val modeId: String, val active: Boolean)

    private val switches = mutableListOf<Switch>()
    private val delays = TestDelays()
    private val engine = TriggerEngine({ id, active, _ -> switches += Switch(id, active) }, delays, SilentLogger)

    private val wifi = FakeSource(Signal.WIFI)
    private val movement = FakeSource(Signal.MOVEMENT)
    private val rules = FakeRules()
    private val hub = SignalHub(listOf(wifi, movement), engine::onStateChanged, SilentLogger)
    private val automation = ModeAutomation(rules, engine, hub, DIRECT)

    private val home = ModeRule("home", listOf(WifiTrigger("Home")))
    private val desk = ModeRule("desk", listOf(MovementTrigger(Movement.STILL)))

    @Test
    fun `only the signals the stored rules read are started`() {
        rules.set(home)
        automation.start()
        assertEquals(1, wifi.starts)
        assertEquals(0, movement.starts)
    }

    @Test
    fun `saving a rule of another kind starts its signal and stops the one nothing reads`() {
        rules.set(home)
        automation.start()

        rules.set(desk)
        assertEquals(1, wifi.stops)
        assertEquals(1, movement.starts)
    }

    @Test
    fun `a signal a rule still reads is not restarted when another rule changes`() {
        rules.set(home)
        automation.start()

        rules.set(home, home.copy(modeId = "office", triggers = listOf(WifiTrigger("Office"))))
        assertEquals(1, wifi.starts)
        assertEquals(0, wifi.stops)
    }

    @Test
    fun `rules changing three times in a row leave exactly the signals the last one reads`() {
        automation.start()
        rules.set(home)
        rules.set(desk)
        rules.set(home)

        assertTrue(wifi.isRunning)
        assertEquals(false, movement.isRunning)
    }

    @Test
    fun `a signal that starts before the engine sees the rule is judged on what it reports`() {
        rules.set(home)
        wifi.next = { it.copy(wifiNetworks = setOf("Home")) }
        automation.start()
        assertEquals(listOf(Switch("home", true)), switches)
    }

    @Test
    fun `stopping stops every signal and reports nothing more`() {
        rules.set(home)
        automation.start()
        automation.stop()

        assertEquals(false, wifi.isRunning)
        wifi.publish { it.copy(wifiNetworks = emptySet()) }
        assertEquals(emptyList<Switch>(), switches)
    }

    @Test
    fun `stopping leaves every Mode as it is`() {
        rules.set(home)
        wifi.next = { it.copy(wifiNetworks = setOf("Home")) }
        automation.start()
        automation.stop()
        assertEquals(listOf(Switch("home", true)), switches)
    }

    @Test
    fun `a build taking over after a hand-off starts its own signals afresh`() {
        rules.set(home)
        automation.start()
        automation.stop()

        // What Vector does on a hot reload: the new build builds its own parts over the same store.
        val taken = mutableListOf<Switch>()
        val next = FakeSource(Signal.WIFI)
        val engine = TriggerEngine({ id, active, _ -> taken += Switch(id, active) }, TestDelays(), SilentLogger)
        val hub = SignalHub(listOf(next), engine::onStateChanged, SilentLogger)
        ModeAutomation(rules, engine, hub, DIRECT).start()

        assertEquals(1, next.starts)
        // As at boot: a rule that is not met leaves its Mode alone.
        assertEquals(emptyList<Switch>(), taken)

        next.publish { it.copy(wifiNetworks = setOf("Home")) }
        assertEquals(listOf(Switch("home", true)), taken)
    }

    @Test
    fun `a source reporting the same thing twice switches nothing twice`() {
        rules.set(home)
        automation.start()
        repeat(3) { wifi.publish { state -> state.copy(wifiNetworks = setOf("Home")) } }
        assertEquals(listOf(Switch("home", true)), switches)
    }

    @Test
    fun `the store notifying without anything changing switches nothing`() {
        rules.set(home)
        automation.start()
        wifi.publish { it.copy(wifiNetworks = setOf("Home")) }
        repeat(3) { rules.notifyWatchers() }
        assertEquals(listOf(Switch("home", true)), switches)
    }

    /** A store whose contents and notifications the test drives. */
    private class FakeRules : ModeSource<ModeRule> {
        private var stored: List<ModeRule> = emptyList()
        private val listeners = mutableListOf<() -> Unit>()

        override fun all(): List<ModeRule> = stored

        override fun observe(listener: () -> Unit): AutoCloseable {
            listeners += listener
            return AutoCloseable { listeners -= listener }
        }

        fun set(vararg rules: ModeRule) {
            stored = rules.toList()
            notifyWatchers()
        }

        fun notifyWatchers() = listeners.toList().forEach { it() }
    }

    private class FakeSource(override val signal: Signal) : SignalSource {
        var starts = 0
        var stops = 0
        var isRunning = false
            private set

        /** Published as soon as the source starts, the way a source with a cached answer does. */
        var next: StateUpdate? = null

        private var publish: ((StateUpdate) -> Unit)? = null

        override fun start(publish: (StateUpdate) -> Unit) {
            starts++
            isRunning = true
            this.publish = publish
            next?.let(publish)
        }

        override fun stop() {
            stops++
            isRunning = false
            publish = null
        }

        fun publish(update: StateUpdate) {
            publish?.invoke(update)
        }
    }

    private companion object {
        val DIRECT = Executor { it.run() }
    }
}
