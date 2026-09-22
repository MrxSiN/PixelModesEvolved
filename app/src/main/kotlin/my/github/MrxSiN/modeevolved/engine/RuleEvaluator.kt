package my.github.MrxSiN.modeevolved.engine

import my.github.MrxSiN.modeevolved.trigger.DeviceState
import my.github.MrxSiN.modeevolved.trigger.ModeRule
import my.github.MrxSiN.modeevolved.trigger.Trigger

/**
 * Answers whether each rule is met, remembering what every trigger last said.
 *
 * A trigger that resists flapping reads its own previous answer, and a trigger
 * is a value the person edits, so the memory belongs here rather than in the
 * trigger. Each Mode remembers separately: the same area used by two Modes can
 * be inside for one and outside for the other only until both have seen a fix.
 *
 * Not thread-safe. The caller asks on one thread.
 */
class RuleEvaluator {

    private val answers = mutableMapOf<String, Map<Trigger, Boolean>>()

    /** Whether [rule] is met by [state], taking its triggers' previous answers into account. */
    fun isMetBy(rule: ModeRule, state: DeviceState): Boolean {
        val previous = answers[rule.modeId].orEmpty()
        val current = rule.triggers.associateWith { it.isMetBy(state, previous[it] == true) }
        answers[rule.modeId] = current
        return rule.isMetWhen { current[it] == true }
    }

    /** Forgets every Mode outside [modeIds], so a rule that comes back starts afresh. */
    fun retainOnly(modeIds: Set<String>) {
        answers.keys.retainAll(modeIds)
    }
}
