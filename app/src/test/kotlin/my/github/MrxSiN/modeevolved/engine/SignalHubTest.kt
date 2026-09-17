package my.github.MrxSiN.modeevolved.engine

import org.junit.Assert.assertEquals
import org.junit.Test

import my.github.MrxSiN.modeevolved.core.SilentLogger
import my.github.MrxSiN.modeevolved.trigger.DeviceState
import my.github.MrxSiN.modeevolved.trigger.Signal
import my.github.MrxSiN.modeevolved.trigger.StateUpdate

class SignalHubTest {

    private val states = mutableListOf<DeviceState>()

    @Test
    fun `starts only required sources and stops unneeded ones`() {
        val wifi = FakeSource(Signal.WIFI)
        val movement = FakeSource(Signal.MOVEMENT)
        val hub = SignalHub(listOf(wifi, movement), states::add, SilentLogger)

        hub.require(setOf(Signal.WIFI))
        assertEquals(1 to 0, wifi.starts to movement.starts)

        hub.require(setOf(Signal.MOVEMENT))
        assertEquals(1, wifi.stops)
        assertEquals(1, movement.starts)
    }

    @Test
    fun `source failing to start is retried on next require`() {
        val wifi = FakeSource(Signal.WIFI, failStarts = 1)
        val hub = SignalHub(listOf(wifi), states::add, SilentLogger)

        hub.require(setOf(Signal.WIFI))
        hub.require(setOf(Signal.WIFI))
        assertEquals(2, wifi.starts)
    }

    @Test
    fun `updates merge into one state`() {
        val wifi = FakeSource(Signal.WIFI)
        val hub = SignalHub(listOf(wifi), states::add, SilentLogger)
        hub.require(setOf(Signal.WIFI))

        wifi.publish { it.copy(wifiNetworks = setOf("Home")) }
        assertEquals(setOf("Home"), states.last().wifiNetworks)
    }

    @Test
    fun `stop ends every source quietly and for good`() {
        val wifi = FakeSource(Signal.WIFI)
        val hub = SignalHub(listOf(wifi), states::add, SilentLogger)
        hub.require(setOf(Signal.WIFI))

        hub.stop()
        wifi.publish { it.copy(wifiNetworks = emptySet()) }
        hub.require(setOf(Signal.WIFI))

        assertEquals(1 to 1, wifi.starts to wifi.stops)
        assertEquals(emptyList<DeviceState>(), states)
    }

    private class FakeSource(override val signal: Signal, private var failStarts: Int = 0) : SignalSource {
        var starts = 0
        var stops = 0
        lateinit var publish: (StateUpdate) -> Unit

        override fun start(publish: (StateUpdate) -> Unit) {
            starts++
            if (failStarts-- > 0) error("unavailable")
            this.publish = publish
        }

        override fun stop() {
            stops++
        }
    }
}
