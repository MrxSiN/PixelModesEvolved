package my.github.MrxSiN.modeevolved.trigger

import my.github.MrxSiN.modeevolved.store.ModeItems

/**
 * The triggers attached to one Mode, joined into one condition.
 *
 * Triggers keep the order a person added them in, with a [Join] between each
 * neighbouring pair. As in ordinary logic, `and` binds before `or`:
 * `A and B or C` means `(A and B) or C`.
 *
 * @property modeId the automatic zen rule id, which survives renaming the Mode.
 * @property joins one join per gap, so always one fewer than [triggers].
 */
data class ModeRule(
    override val modeId: String,
    val triggers: List<Trigger> = emptyList(),
    val joins: List<Join> = List((triggers.size - 1).coerceAtLeast(0)) { Join.AND },
) : ModeItems<ModeRule, Trigger> {

    override val items: List<Trigger> get() = triggers

    init {
        require(joins.size == (triggers.size - 1).coerceAtLeast(0)) {
            "${triggers.size} triggers need ${triggers.size - 1} joins, not ${joins.size}"
        }
    }

    enum class Join { AND, OR }

    val signals: Set<Signal> get() = triggers.mapTo(mutableSetOf()) { it.signal }

    /** The triggers split at every `or`: the rule is met when every trigger of any one group is. */
    val groups: List<List<Trigger>>
        get() {
            if (triggers.isEmpty()) return emptyList()
            val groups = mutableListOf(mutableListOf(triggers.first()))
            joins.forEachIndexed { index, join ->
                if (join == Join.OR) groups += mutableListOf<Trigger>()
                groups.last() += triggers[index + 1]
            }
            return groups
        }

    /** A rule without triggers is never met, so an unfinished rule does nothing. */
    fun isMetBy(state: DeviceState): Boolean = groups.any { group -> group.all { it.isMetBy(state) } }

    /** Adds [item] at the end, joined with `and`. A trigger the rule already has is not added twice. */
    override fun plus(item: Trigger): ModeRule = when {
        item in triggers -> this
        triggers.isEmpty() -> copy(triggers = listOf(item), joins = emptyList())
        else -> copy(triggers = triggers + item, joins = joins + Join.AND)
    }

    /** Puts [new] in place of [old], keeping its joins. Becomes [plus] when [old] is gone. */
    override fun replace(old: Trigger, new: Trigger): ModeRule {
        val index = triggers.indexOf(old)
        return when {
            index < 0 -> plus(new)
            new != old && new in triggers -> minus(old)
            else -> copy(triggers = triggers.toMutableList().apply { set(index, new) })
        }
    }

    /**
     * Removes [item] with one of its joins.
     *
     * Between two joins an `or` is kept over an `and`, so the groups either side
     * stay apart: removing B from `A or B and C` leaves `A or C`, not `A and C`.
     */
    override fun minus(item: Trigger): ModeRule {
        val index = triggers.indexOf(item)
        if (index < 0) return this
        val joinIndex = when {
            index == 0 -> 0
            index == triggers.lastIndex -> index - 1
            joins[index - 1] == Join.OR -> index
            else -> index - 1
        }
        return copy(
            triggers = triggers.filterIndexed { i, _ -> i != index },
            joins = joins.filterIndexed { i, _ -> i != joinIndex },
        )
    }

    /** Sets the join between trigger [index] and the next one. */
    fun withJoin(index: Int, join: Join): ModeRule =
        copy(joins = joins.toMutableList().apply { set(index, join) })
}
