package my.github.MrxSiN.modeevolved.engine

import my.github.MrxSiN.modeevolved.core.Logger
import my.github.MrxSiN.modeevolved.trigger.DeviceState
import my.github.MrxSiN.modeevolved.trigger.ModeRule
import my.github.MrxSiN.modeevolved.trigger.Signal
import my.github.MrxSiN.modeevolved.trigger.Trigger

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
 * Rules are compiled when they change (STATE_ENGINE.md): each holds its triggers in an array,
 * its `or` groups as end offsets, every trigger's previous answer, and a mask of the signals it
 * reads. A state change works out which signals it touched and evaluates only the rules reading
 * one of them; a rule whose inputs did not change would give the answer it already holds.
 *
 * Not thread-safe. The caller delivers every call on one thread.
 */
class TriggerEngine(
    private val modes: ModeController,
    private val delays: Delays,
    private val logger: Logger,
) {

    private var rules: Array<Compiled> = emptyArray()

    /** Every signal some rule reads; a change to any other slice is stored without a look. */
    private var read = 0
    private var state = DeviceState()
    private var loaded = false

    /** Orders decisions as the order they were first made in, which is the order removed rules let go in. */
    private var decisionCount = 0L

    /** Set when the invariant "a held answer equals the decision or the edge being waited out" may be broken. */
    private var dirty = false

    /** One rule, compiled for evaluation. Everything mutable here is touched on the engine thread only. */
    private class Compiled(val rule: ModeRule) {
        val modeId: String = rule.modeId
        val triggers: Array<Trigger> = rule.triggers.toTypedArray()

        /** Each trigger's previous answer, which a trigger resisting flapping reads. */
        val answers = BooleanArray(triggers.size)

        /** The exclusive end of each `or` group in [triggers]. */
        val groupEnds: IntArray = IntArray(rule.joins.count { it == ModeRule.Join.OR } + if (triggers.isEmpty()) 0 else 1).also { ends ->
            var group = 0
            rule.joins.forEachIndexed { index, join -> if (join == ModeRule.Join.OR) ends[group++] = index + 1 }
            if (triggers.isNotEmpty()) ends[group] = triggers.size
        }
        val signals: Int = triggers.fold(0) { mask, trigger -> mask or bit(trigger.signal) }

        /** [NONE], [OFF] or [ON]: the last decision, or none since the rule appeared or changed. */
        var decision = NONE
        var decidedAt = 0L

        /** The answer an edge is being waited out for, when [timer] is set. */
        var waitMet = false
        var timer: AutoCloseable? = null

        fun isMetBy(state: DeviceState): Boolean {
            var start = 0
            var met = false
            for (end in groupEnds) {
                var all = true
                for (index in start until end) {
                    val answer = triggers[index].isMetBy(state, answers[index])
                    answers[index] = answer
                    all = all && answer
                }
                met = met || all
                start = end
            }
            return met
        }

        /** Takes over the answers [previous] gave for triggers this rule still has, as one memory per Mode. */
        fun inherit(previous: Compiled) {
            for (index in triggers.indices) {
                val at = previous.triggers.lastIndexOf(triggers[index])
                if (at >= 0) answers[index] = previous.answers[at]
            }
        }
    }

    fun onRulesChanged(newRules: List<ModeRule>) {
        val byId = newRules.associateBy { it.modeId }
        val previous = rules.associateBy { it.modeId }

        // A rule that went away while it held its Mode on lets the Mode go, in the order the decisions were made.
        rules.filter { it.decision == ON && it.modeId !in byId }
            .sortedBy { it.decidedAt }
            .forEach { switch(it.modeId, active = false, reset = false) }

        val compiled = byId.values.map { rule ->
            val old = previous[rule.modeId]
            when {
                old == null -> Compiled(rule)
                old.rule == rule -> old
                // A rule that changed is no longer the one whose edge was waiting, and has no decision yet.
                else -> Compiled(rule).also { it.inherit(old); cancelWait(old) }
            }
        }
        for (old in rules) if (old.modeId !in byId) cancelWait(old)

        val edited = if (loaded) byId.keys.filterTo(mutableSetOf()) { byId[it] != previous[it]?.rule } else emptySet()
        rules = compiled.toTypedArray()
        read = compiled.fold(0) { mask, rule -> mask or rule.signals }
        loaded = true
        dirty = false
        evaluate(ALL, edited)
    }

    fun onStateChanged(newState: DeviceState) {
        if (dirty) {
            if (newState == state) return
            state = newState
            dirty = false
            evaluate(ALL, emptySet())
            return
        }
        val changed = changedSignals(state, newState, read)
        // A slice no rule reads is still kept, equal or not, so a rule added later reads it.
        state = newState
        if (changed != 0) evaluate(changed, emptySet())
    }

    /** Drops every edge still waiting, so a stopped build never switches a Mode after it has handed over. */
    fun stop() {
        for (rule in rules) cancelWait(rule)
        dirty = true
    }

    private fun evaluate(signals: Int, edited: Set<String>) {
        for (rule in rules) {
            if (signals != ALL && rule.signals and signals == 0) continue
            val met = rule.isMetBy(state)
            val on = if (met) ON else OFF
            if (rule.decision == on) {
                // The answer came back before the wait was over: there is no edge after all.
                cancelWait(rule)
                continue
            }

            val first = rule.decision == NONE
            val isEdit = first && rule.modeId in edited
            if (first && !met && !isEdit) {
                // Loading never turns off a Mode someone else switched on; one this module
                // switched on before it stopped, and whose triggers no longer hold, it does.
                if (switchedOnHere(rule.modeId)) apply(rule, met = false, reset = false) else decide(rule, on)
                continue
            }
            if (rule.timer != null && rule.waitMet == met) continue

            cancelWait(rule)
            if (isEdit || first || rule.rule.stabilityMillis <= 0L) {
                apply(rule, met, reset = isEdit)
            } else {
                await(rule, met)
            }
        }
    }

    private fun await(rule: Compiled, met: Boolean) {
        val millis = rule.rule.stabilityMillis
        logger.info("Mode ${rule.modeId} would go ${onOff(met)}: waiting ${millis / 1000}s for it to hold")
        rule.waitMet = met
        rule.timer = delays.after(millis) {
            rule.timer = null
            apply(rule, met, reset = false)
        }
    }

    private fun cancelWait(rule: Compiled) {
        val timer = rule.timer ?: return
        rule.timer = null
        timer.close()
    }

    private fun decide(rule: Compiled, on: Int) {
        if (rule.decision == NONE) rule.decidedAt = ++decisionCount
        rule.decision = on
    }

    private fun apply(rule: Compiled, met: Boolean, reset: Boolean) {
        decide(rule, if (met) ON else OFF)
        switch(rule.modeId, met, reset)
    }

    private fun switch(modeId: String, active: Boolean, reset: Boolean) {
        logger.info("Mode $modeId -> ${onOff(active)}${if (reset) " (saved)" else ""}")
        runCatching { modes.setActive(modeId, active, reset) }
            .onFailure { logger.warn("Switching mode $modeId failed", it) }
    }

    private fun switchedOnHere(modeId: String): Boolean =
        runCatching { modes.switchedOnHere(modeId) }.getOrDefault(false)

    private fun onOff(active: Boolean) = if (active) "on" else "off"

    private companion object {
        const val NONE = 0
        const val OFF = 1
        const val ON = 2
        const val ALL = -1

        fun bit(signal: Signal): Int = 1 shl signal.ordinal


        /**
         * The signals among [among] whose slice differs between [old] and [new]. The hub keeps
         * every slice it did not touch by reference, so most are told apart without equals.
         */
        fun changedSignals(old: DeviceState, new: DeviceState, among: Int): Int {
            var mask = 0
            if (among and bit(Signal.WIFI) != 0 && old.wifiNetworks !== new.wifiNetworks && old.wifiNetworks != new.wifiNetworks) mask = mask or bit(Signal.WIFI)
            if (among and bit(Signal.BLUETOOTH) != 0 && old.bluetoothDevices !== new.bluetoothDevices && old.bluetoothDevices != new.bluetoothDevices) mask = mask or bit(Signal.BLUETOOTH)
            if (among and bit(Signal.LOCATION) != 0 && old.location !== new.location && old.location != new.location) mask = mask or bit(Signal.LOCATION)
            if (among and bit(Signal.MOVEMENT) != 0 && old.movement != new.movement) mask = mask or bit(Signal.MOVEMENT)
            if (among and bit(Signal.FLIGHT) != 0 && old.flying != new.flying) mask = mask or bit(Signal.FLIGHT)
            if (among and bit(Signal.AIRPLANE) != 0 && old.airplaneMode != new.airplaneMode) mask = mask or bit(Signal.AIRPLANE)
            return mask
        }
    }
}
