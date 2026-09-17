package my.github.MrxSiN.modeevolved.page

import android.content.Context
import java.util.WeakHashMap

import my.github.MrxSiN.modeevolved.R
import my.github.MrxSiN.modeevolved.editor.ItemFamilies
import my.github.MrxSiN.modeevolved.store.ModeStore
import my.github.MrxSiN.modeevolved.trigger.ModeRule

/**
 * The trigger rows under "Mode settings" on one Mode's page.
 *
 * One row per trigger and one to add a trigger, both opening the editor.
 * Between each pair of triggers sits a connected button group choosing
 * AND or OR, the same expressive control Settings uses for its own choices.
 *
 * Rows are rebuilt only when the stored rule differs from what is on screen.
 * The page refreshes whenever any Mode changes state, and rebuilding then
 * would cut short the button group's own selection animation.
 */
class TriggerSection(
    private val preferences: PreferenceApi,
    private val rules: ModeStore<ModeRule>,
    private val editor: EditorLauncher,
    private val text: ModuleText,
) : ModePageSection {

    private val family = ItemFamilies.triggers

    /** What each category last showed, so an unchanged rule is not drawn again. */
    private val shown = WeakHashMap<Any, ModeRule>()

    override fun render(screen: Any, mode: ModeRef) {
        val category = preferences.find(screen, CATEGORY) ?: return
        val rule = rules.get(mode.id) ?: ModeRule(mode.id)
        // Settings can rebuild the category itself, which removes these rows; then they are drawn again.
        if (shown[category] == rule && preferences.find(category, ADD_KEY) != null) return
        draw(category, mode, rule)
    }

    private fun draw(category: Any, mode: ModeRef, rule: ModeRule) {
        shown[category] = rule
        preferences.replaceRows(category, KEY_PREFIX, rows(category, mode, rule))
    }

    private fun rows(category: Any, mode: ModeRef, rule: ModeRule): List<PreferenceRow> = buildList {
        val context = preferences.context(category)
        rule.triggers.forEachIndexed { index, trigger ->
            if (index > 0) add(joinRow(category, context, mode, rule, index - 1))
            add(
                PreferenceRow.Link(
                    key = "${KEY_PREFIX}trigger_$index",
                    title = family.presentations.describe(text.raw, trigger),
                    summary = text.string(R.string.row_trigger_summary),
                    icon = text.icon(context, family.presentations.of(trigger)?.icon),
                ) { editor.open(context, family, mode, trigger) },
            )
        }
        add(
            PreferenceRow.Link(
                key = ADD_KEY,
                title = text.string(R.string.row_add_title),
                summary = text.string(if (rule.triggers.isEmpty()) R.string.row_add_summary else R.string.row_add_more_summary),
                icon = text.icon(context, R.drawable.ic_trigger_add),
            ) { editor.open(context, family, mode, null) },
        )
    }

    /** The join between trigger [index] and the next. */
    private fun joinRow(category: Any, context: Context, mode: ModeRef, rule: ModeRule, index: Int): PreferenceRow {
        val key = "${KEY_PREFIX}join_$index"
        val joins = ModeRule.Join.entries

        if (!preferences.supportsChoices) {
            val current = rule.joins[index]
            return PreferenceRow.Link(key, text.string(labelOf(current)), text.string(R.string.join_tap_to_change), icon = null) {
                setJoin(category, mode, index, joins[(current.ordinal + 1) % joins.size])?.let { draw(category, mode, it) }
            }
        }
        return PreferenceRow.Choice(
            key = key,
            options = joins.map { PreferenceRow.Option(text.string(labelOf(it))) },
            selected = rule.joins[index].ordinal,
        ) { setJoin(category, mode, index, joins[it]) }
    }

    /**
     * Stores [join] without redrawing, so the button group finishes its own animation.
     * The rule is re-read first, so a change stored since the page was drawn is not lost.
     *
     * @return the stored rule, or null when nothing changed.
     */
    private fun setJoin(category: Any, mode: ModeRef, index: Int, join: ModeRule.Join): ModeRule? {
        val current = rules.get(mode.id) ?: return null
        if (index >= current.joins.size || current.joins[index] == join) return null
        val updated = current.withJoin(index, join)
        shown[category] = updated
        rules.save(updated)
        return updated
    }

    private fun labelOf(join: ModeRule.Join) = if (join == ModeRule.Join.AND) R.string.join_and else R.string.join_or

    companion object {
        /** Keys of every row this module adds to a Mode page. */
        const val KEY_PREFIX = "mode_evolved_"

        /** The "Mode settings" category that holds the schedule trigger. */
        const val CATEGORY = "zen_automatic_trigger_category"

        private const val ADD_KEY = "${KEY_PREFIX}add"
    }
}
