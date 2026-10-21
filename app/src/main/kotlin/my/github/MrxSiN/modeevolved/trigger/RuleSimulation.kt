package my.github.MrxSiN.modeevolved.trigger

/**
 * What a rule would decide for answers a person makes up (ROADMAP I19).
 *
 * Pure: no signal is read, no Mode switched, nothing stored. It is the same
 * grouping the engine evaluates (AND binds before OR), told by trigger position
 * so two equal triggers can be answered apart.
 */
object RuleSimulation {

    /** The positions of each OR group's triggers, in order. */
    fun groups(rule: ModeRule): List<IntRange> {
        if (rule.triggers.isEmpty()) return emptyList()
        val groups = ArrayList<IntRange>()
        var start = 0
        rule.joins.forEachIndexed { index, join ->
            if (join == ModeRule.Join.OR) {
                groups += start..index
                start = index + 1
            }
        }
        groups += start until rule.triggers.size
        return groups
    }

    /** The first group whose triggers are all [met], or null when the Mode would be off. */
    fun metGroup(rule: ModeRule, met: List<Boolean>): Int? =
        groups(rule).indexOfFirst { group -> group.all { met.getOrElse(it) { false } } }.takeIf { it >= 0 }
}
