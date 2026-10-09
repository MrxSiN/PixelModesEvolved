package my.github.MrxSiN.modeevolved.engine

import kotlin.random.Random

import org.junit.Assert.assertEquals
import org.junit.Test

import my.github.MrxSiN.modeevolved.core.Logger
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
import my.github.MrxSiN.modeevolved.trigger.Trigger
import my.github.MrxSiN.modeevolved.trigger.WifiTrigger

/**
 * Feeds the same random traces to [TriggerEngine] and to the engine it replaced, kept below
 * verbatim as the oracle, and requires the same Mode switches in the same order.
 */
class TriggerEngineParityTest {

    private data class Switch(val modeId: String, val active: Boolean, val reset: Boolean)

    @Test
    fun `compiled engine switches exactly as the interpreted one did`() {
        repeat(TRACES) { seed -> compare(Random(seed), seed) }
    }

    private fun compare(random: Random, seed: Int) {
        val expected = mutableListOf<Switch>()
        val actual = mutableListOf<Switch>()
        val oldDelays = TestDelays()
        val newDelays = TestDelays()
        val old = LegacyTriggerEngine({ id, on, reset -> expected += Switch(id, on, reset) }, oldDelays, SilentLogger)
        val new = TriggerEngine({ id, on, reset -> actual += Switch(id, on, reset) }, newDelays, SilentLogger)

        var state = DeviceState()
        var rules = emptyList<ModeRule>()
        repeat(STEPS) { step ->
            when (random.nextInt(10)) {
                0 -> {
                    rules = editRules(random, rules)
                    old.onRulesChanged(rules)
                    new.onRulesChanged(rules)
                }
                1, 2 -> {
                    val millis = random.nextLong(0, 90_000)
                    oldDelays.advance(millis)
                    newDelays.advance(millis)
                }
                3 -> if (random.nextInt(8) == 0) {
                    old.stop()
                    new.stop()
                }
                else -> {
                    state = changeState(random, state)
                    old.onStateChanged(state)
                    new.onStateChanged(state)
                }
            }
            assertEquals("seed $seed, step $step", expected, actual)
            assertEquals("seed $seed, step $step: pending waits", oldDelays.pending, newDelays.pending)
        }
    }

    private fun editRules(random: Random, rules: List<ModeRule>): List<ModeRule> {
        val kept = rules.filter { random.nextInt(4) != 0 }.map { if (random.nextInt(3) == 0) randomRule(random, it.modeId) else it }
        val added = List(random.nextInt(3)) { randomRule(random, MODES.random(random)) }
        return (kept + added).shuffled(random)
    }

    private fun randomRule(random: Random, modeId: String): ModeRule {
        val triggers = List(random.nextInt(0, 4)) { TRIGGERS.random(random) }
        val joins = List((triggers.size - 1).coerceAtLeast(0)) { if (random.nextBoolean()) ModeRule.Join.AND else ModeRule.Join.OR }
        val stability = if (random.nextBoolean()) 0L else random.nextLong(1, 60_000)
        return ModeRule(modeId, triggers, joins, stability)
    }

    private fun changeState(random: Random, state: DeviceState): DeviceState = when (random.nextInt(7)) {
        0 -> state.copy(wifiNetworks = SSIDS.filter { random.nextBoolean() }.toSet())
        1 -> state.copy(bluetoothDevices = DEVICES.filter { random.nextBoolean() }.toSet())
        2 -> state.copy(location = if (random.nextInt(6) == 0) null else sample(random))
        3 -> state.copy(movement = listOf(null, Movement.MOVING, Movement.STILL).random(random))
        4 -> state.copy(flying = listOf(null, true, false).random(random))
        5 -> state.copy(airplaneMode = listOf(null, true, false).random(random))
        else -> state
    }

    /** Fixes scattered across the edge of [AREA], so hysteresis decides many of them. */
    private fun sample(random: Random) = LocationSample(
        GeoPoint(AREA.center.latitude + random.nextDouble(-0.002, 0.002), AREA.center.longitude),
        accuracyMeters = if (random.nextBoolean()) null else random.nextDouble(5.0, 200.0),
    )

    private companion object {
        const val TRACES = 2_000
        const val STEPS = 200
        val MODES = listOf("a", "b", "c", "d")
        val SSIDS = listOf("Home", "Office")
        val DEVICES = listOf("AA:BB", "CC:DD")
        val AREA = AreaTrigger(GeoPoint(3.1390, 101.6869), radiusMeters = 100.0)
        val TRIGGERS: List<Trigger> = listOf(
            WifiTrigger("Home"), WifiTrigger("Office"),
            BluetoothTrigger("aa:bb", "Car"), BluetoothTrigger("CC:DD", "Buds"),
            AREA, AreaTrigger(AREA.center, radiusMeters = 30.0),
            MovementTrigger(Movement.MOVING), MovementTrigger(Movement.STILL), MovementTrigger(Movement.FLYING),
            AirplaneTrigger(true), AirplaneTrigger(false),
        )
    }
}

/** The engine as it was before rules were compiled, with its evaluator, unchanged apart from names. */
internal class LegacyTriggerEngine(
    private val modes: ModeController,
    private val delays: Delays,
    private val logger: Logger,
) {

    private class RuleEvaluator {
        private val answers = mutableMapOf<String, Map<Trigger, Boolean>>()

        fun isMetBy(rule: ModeRule, state: DeviceState): Boolean {
            val previous = answers[rule.modeId].orEmpty()
            val current = rule.triggers.associateWith { it.isMetBy(state, previous[it] == true) }
            answers[rule.modeId] = current
            return rule.isMetWhen { current[it] == true }
        }

        fun retainOnly(modeIds: Set<String>) {
            answers.keys.retainAll(modeIds)
        }
    }

    private val evaluator = RuleEvaluator()
    private var rules: Map<String, ModeRule> = emptyMap()
    private var state = DeviceState()
    private var loaded = false
    private val decisions = mutableMapOf<String, Boolean>()
    private val waiting = mutableMapOf<String, Wait>()

    private class Wait(val met: Boolean, val timer: AutoCloseable)

    fun onRulesChanged(newRules: List<ModeRule>) {
        val byId = newRules.associateBy { it.modeId }

        decisions.filter { (id, on) -> on && id !in byId }.keys.forEach { switch(it, active = false, reset = false) }
        decisions.keys.retainAll { byId[it] == rules[it] }
        waiting.keys.toList().filter { byId[it] != rules[it] }.forEach(::cancelWait)
        evaluator.retainOnly(byId.keys)

        val edited = if (loaded) byId.keys.filterTo(mutableSetOf()) { byId[it] != rules[it] } else emptySet()
        rules = byId
        loaded = true
        evaluate(edited)
    }

    fun onStateChanged(newState: DeviceState) {
        if (newState == state) return
        state = newState
        evaluate(emptySet())
    }

    fun stop() {
        waiting.keys.toList().forEach(::cancelWait)
    }

    private fun evaluate(edited: Set<String>) {
        for (rule in rules.values) {
            val met = evaluator.isMetBy(rule, state)
            if (decisions[rule.modeId] == met) {
                cancelWait(rule.modeId)
                continue
            }

            val first = rule.modeId !in decisions
            val isEdit = first && rule.modeId in edited
            if (first && !met && !isEdit) {
                decisions[rule.modeId] = met
                continue
            }
            if (waiting[rule.modeId]?.met == met) continue

            cancelWait(rule.modeId)
            if (isEdit || first || rule.stabilityMillis <= 0L) {
                apply(rule.modeId, met, reset = isEdit)
            } else {
                await(rule.modeId, met, rule.stabilityMillis)
            }
        }
    }

    private fun await(modeId: String, met: Boolean, millis: Long) {
        waiting[modeId] = Wait(
            met,
            delays.after(millis) {
                waiting -= modeId
                apply(modeId, met, reset = false)
            },
        )
    }

    private fun cancelWait(modeId: String) {
        waiting.remove(modeId)?.timer?.close()
    }

    private fun apply(modeId: String, met: Boolean, reset: Boolean) {
        decisions[modeId] = met
        switch(modeId, met, reset)
    }

    private fun switch(modeId: String, active: Boolean, reset: Boolean) {
        runCatching { modes.setActive(modeId, active, reset) }
            .onFailure { logger.warn("Switching mode $modeId failed", it) }
    }
}
