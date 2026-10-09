package my.github.MrxSiN.modeevolved.engine

import java.lang.management.ManagementFactory

import org.junit.Assume.assumeTrue
import org.junit.Test

import my.github.MrxSiN.modeevolved.core.SilentLogger
import my.github.MrxSiN.modeevolved.trigger.AirplaneTrigger
import my.github.MrxSiN.modeevolved.trigger.AreaTrigger
import my.github.MrxSiN.modeevolved.trigger.BluetoothTrigger
import my.github.MrxSiN.modeevolved.trigger.DeviceState
import my.github.MrxSiN.modeevolved.trigger.GeoPoint
import my.github.MrxSiN.modeevolved.trigger.LocationSample
import my.github.MrxSiN.modeevolved.trigger.ModeRule
import my.github.MrxSiN.modeevolved.trigger.Movement
import my.github.MrxSiN.modeevolved.trigger.MovementTrigger
import my.github.MrxSiN.modeevolved.trigger.WifiTrigger

/**
 * Host-JVM cost per state change, legacy engine against compiled engine. A proxy for ART, not a
 * device measurement (PERFORMANCE.md). Runs only with BENCH=1 in the environment.
 */
class TriggerEngineBenchmark {

    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
    private val home = GeoPoint(3.1390, 101.6869)

    @Test
    fun bench() {
        assumeTrue(System.getenv("BENCH") == "1")
        val configs = linkedMapOf(
            "zero rules" to emptyList(),
            "one wifi rule" to listOf(ModeRule("a", listOf(WifiTrigger("Home")))),
            "four mixed rules" to listOf(
                ModeRule("a", listOf(WifiTrigger("Home"))),
                ModeRule("b", listOf(BluetoothTrigger("aa:bb", "Car"), MovementTrigger(Movement.MOVING)), stabilityMillis = 30_000),
                ModeRule("c", listOf(AreaTrigger(home, 100.0), WifiTrigger("Office")), listOf(ModeRule.Join.OR)),
                ModeRule("d", listOf(AirplaneTrigger(true))),
            ),
            "32 rules stress" to List(32) { i ->
                ModeRule("m$i", listOf(WifiTrigger("w$i"), AreaTrigger(GeoPoint(3.0 + i / 1000.0, 101.0), 50.0), BluetoothTrigger("b$i", "")), listOf(ModeRule.Join.AND, ModeRule.Join.OR))
            },
        )
        // Every other event is Wi-Fi, which only some rules read; the rest are fixes moving along the edge of an area.
        val events = List(1024) { i ->
            if (i % 2 == 0) DeviceState(wifiNetworks = if (i % 4 == 0) setOf("Home") else emptySet(), location = fix(i - 1))
            else DeviceState(wifiNetworks = if ((i - 1) % 4 == 0) setOf("Home") else emptySet(), location = fix(i))
        }
        println("config | engine | ns/event median of 15 runs (min-max) | bytes/event")
        for ((name, rules) in configs) {
            for (engine in (System.getenv("BENCH_ORDER") ?: "legacy,compiled").split(",")) {
                val (onState, onRules) = if (engine == "legacy") {
                    val e = LegacyTriggerEngine({ _, _, _ -> }, TestDelays(), SilentLogger)
                    Pair<(DeviceState) -> Unit, (List<ModeRule>) -> Unit>(e::onStateChanged, e::onRulesChanged)
                } else {
                    val e = TriggerEngine({ _, _, _ -> }, TestDelays(), SilentLogger)
                    Pair<(DeviceState) -> Unit, (List<ModeRule>) -> Unit>(e::onStateChanged, e::onRulesChanged)
                }
                onRules(rules)
                repeat(200) { for (event in events) onState(event) }
                val runs = LongArray(15)
                var bytes = 0L
                for (run in runs.indices) {
                    val allocated = threads.getThreadAllocatedBytes(Thread.currentThread().id)
                    val start = System.nanoTime()
                    repeat(50) { for (event in events) onState(event) }
                    runs[run] = (System.nanoTime() - start) / (50L * events.size)
                    bytes = (threads.getThreadAllocatedBytes(Thread.currentThread().id) - allocated) / (50L * events.size)
                }
                runs.sort()
                println("$name | $engine | ${runs[7]} (${runs.first()}-${runs.last()}) | $bytes")
            }
        }
    }

    private fun fix(i: Int) = LocationSample(GeoPoint(3.1390 + (i % 7) * 0.0003, 101.6869), accuracyMeters = 40.0)
}
