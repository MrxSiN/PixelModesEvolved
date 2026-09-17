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
 * Not thread-safe. The caller delivers every call on one thread.
 */
class TriggerEngine(private val modes: ModeController, private val logger: Logger) {

    private var rules: Map<String, ModeRule> = emptyMap()
    private var state = DeviceState()
    private var loaded = false

    /** Last decision per mode id. Absent means not decided since the rule appeared or changed. */
    private val decisions = mutableMapOf<String, Boolean>()

    fun onRulesChanged(newRules: List<ModeRule>) {
        val byId = newRules.associateBy { it.modeId }

        decisions.filter { (id, on) -> on && id !in byId }.keys.forEach { switch(it, active = false, reset = false) }
        decisions.keys.retainAll { byId[it] == rules[it] }

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

    private fun evaluate(edited: Set<String>) {
        for (rule in rules.values) {
            val met = rule.isMetBy(state)
            val previous = decisions.put(rule.modeId, met)
            if (previous == met) continue

            val isEdit = previous == null && rule.modeId in edited
            if (previous == null && !met && !isEdit) continue
            switch(rule.modeId, met, reset = isEdit)
        }
    }

    private fun switch(modeId: String, active: Boolean, reset: Boolean) {
        logger.info("Mode $modeId -> ${if (active) "on" else "off"}${if (reset) " (saved)" else ""}")
        runCatching { modes.setActive(modeId, active, reset) }
            .onFailure { logger.warn("Switching mode $modeId failed", it) }
    }
}
