package my.github.MrxSiN.modeevolved.engine

import my.github.MrxSiN.modeevolved.core.Logger
import my.github.MrxSiN.modeevolved.trigger.DeviceState
import my.github.MrxSiN.modeevolved.trigger.ModeRule

/**
 * Decides when each Mode turns on or off.
 *
 * Decisions are edge-triggered: a Mode is switched when its rule goes from met
 * to unmet or back. Between those edges the person stays in charge, so a Mode
 * turned off by hand is not turned straight back on by the next Wi-Fi scan.
 *
 * Three moments differ from an ordinary edge:
 * - At load, such as boot, a rule that is not met leaves its Mode alone, so a
 *   Mode someone started by hand or by schedule keeps running.
 * - A rule saved or edited afterwards is applied at once, met or not, and ends
 *   any manual override: saving a trigger is the person saying what they want.
 * - A rule removed while it held its Mode on lets the Mode go.
 *
 * A rule with a [ModeRule.stabilityMillis] waits that long before an edge the
 * signals caused is acted on, and drops the edge if the answer comes back
 * meanwhile. Roaming between two access points, a headset reconnecting and a
 * position fix wandering along a boundary all produce real edges, and without
 * the wait each of them would open an app or run a Tasker task. Load and edit
 * never wait: both are the person, not a noisy signal.
 *
 * Not thread-safe. The caller delivers every call on one thread.
 */
class TriggerEngine(
    private val modes: ModeController,
    private val delays: Delays,
    private val logger: Logger,
) {

    private val evaluator = RuleEvaluator()
    private var rules: Map<String, ModeRule> = emptyMap()
    private var state = DeviceState()
    private var loaded = false

    /** Last decision per mode id. Absent means not decided since the rule appeared or changed. */
    private val decisions = mutableMapOf<String, Boolean>()

    /** Edges waiting out their rule's stabilization, per mode id. */
    private val waiting = mutableMapOf<String, Wait>()

    private class Wait(val met: Boolean, val timer: AutoCloseable)

    fun onRulesChanged(newRules: List<ModeRule>) {
        val byId = newRules.associateBy { it.modeId }

        decisions.filter { (id, on) -> on && id !in byId }.keys.forEach { switch(it, active = false, reset = false) }
        decisions.keys.retainAll { byId[it] == rules[it] }
        // A rule that changed or went away is no longer the one whose edge was waiting.
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

    /** Drops every edge still waiting, so a stopped build never switches a Mode after it has handed over. */
    fun stop() {
        waiting.keys.toList().forEach(::cancelWait)
    }

    private fun evaluate(edited: Set<String>) {
        for (rule in rules.values) {
            val met = evaluator.isMetBy(rule, state)
            if (decisions[rule.modeId] == met) {
                // The answer came back before the wait was over: there is no edge after all.
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
        logger.info("Mode $modeId would go ${onOff(met)}: waiting ${millis / 1000}s for it to hold")
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
        logger.info("Mode $modeId -> ${onOff(active)}${if (reset) " (saved)" else ""}")
        runCatching { modes.setActive(modeId, active, reset) }
            .onFailure { logger.warn("Switching mode $modeId failed", it) }
    }

    private fun onOff(active: Boolean) = if (active) "on" else "off"
}
