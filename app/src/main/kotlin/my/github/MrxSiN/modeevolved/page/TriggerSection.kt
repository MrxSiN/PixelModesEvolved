package my.github.MrxSiN.modeevolved.page

import android.app.AlertDialog
import android.content.Context
import java.util.WeakHashMap

import my.github.MrxSiN.modeevolved.R
import my.github.MrxSiN.modeevolved.editor.ItemFamilies
import my.github.MrxSiN.modeevolved.presentation.Durations
import my.github.MrxSiN.modeevolved.presentation.Stabilizations
import my.github.MrxSiN.modeevolved.store.ModeStore
import my.github.MrxSiN.modeevolved.trigger.ModeRule

/**
 * The trigger rows under "Mode settings" on one Mode's page.
 *
 * One row per trigger and one to add a trigger, both opening the editor.
 * Between each pair of triggers sits a connected button group choosing
 * AND or OR, the same expressive control Settings uses for its own choices.
 * Under them sit how long the triggers must hold before the Mode is switched
 * and, when Android's own schedule drives the same Mode, a note saying which
 * of the two wins at a schedule boundary.
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
    private val shown = WeakHashMap<Any, Shown>()

    /** Everything these rows depend on: the rule, and the one part of the Mode drawn beside it. */
    private data class Shown(val rule: ModeRule, val hasSchedule: Boolean)

    override fun render(screen: Any, mode: ModeRef) {
        val category = preferences.find(screen, CATEGORY) ?: return
        val rule = rules.get(mode.id) ?: ModeRule(mode.id)
        // Settings can rebuild the category itself, which removes these rows; then they are drawn again.
        if (shown[category] == Shown(rule, mode.hasSchedule) && preferences.find(category, ADD_KEY) != null) return
        draw(category, mode, rule)
    }

    private fun draw(category: Any, mode: ModeRef, rule: ModeRule) {
        shown[category] = Shown(rule, mode.hasSchedule)
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
        // A Mode with no trigger has nothing to stabilize and nothing to conflict with its schedule.
        if (rule.triggers.isEmpty()) return@buildList
        add(stabilityRow(category, context, mode, rule))
        add(
            PreferenceRow.Link(
                key = "${KEY_PREFIX}simulate",
                title = text.string(R.string.simulate_row_title),
                summary = text.string(R.string.simulate_row_summary),
                icon = text.icon(context, R.drawable.ic_check),
            ) { editor.simulate(context, mode, rule) },
        )
        if (mode.hasSchedule) add(scheduleWarningRow(context))
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

    /** How long the triggers must keep agreeing before the Mode is switched and its actions run. */
    private fun stabilityRow(category: Any, context: Context, mode: ModeRef, rule: ModeRule): PreferenceRow =
        PreferenceRow.Link(
            key = STABILITY_KEY,
            title = text.string(R.string.stability_title),
            summary = text.raw.getString(R.string.stability_summary, stabilityLabel(rule.stabilityMillis)),
            icon = text.icon(context, R.drawable.ic_wait),
        ) { chooseStability(category, context, mode, rule) }

    private fun chooseStability(category: Any, context: Context, mode: ModeRef, rule: ModeRule) {
        val activity = context.activity() ?: return
        val offered = Stabilizations.offered
        val labels = offered.map(::stabilityLabel).toTypedArray<CharSequence>()
        AlertDialog.Builder(activity)
            .setTitle(text.string(R.string.stability_title))
            .setSingleChoiceItems(labels, offered.indexOf(rule.stabilityMillis)) { dialog, index ->
                dialog.dismiss()
                setStability(category, mode, offered[index])?.let { draw(category, mode, it) }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun stabilityLabel(millis: Long): String {
        if (millis <= 0L) return text.string(R.string.stability_none)
        val label = Durations.label(millis)
        val wording = if (label.seconds) R.plurals.duration_seconds else R.plurals.duration_minutes
        return text.raw.getQuantityString(wording, label.amount, label.amount)
    }

    /** Says which of the two wins, rather than leaving a schedule boundary looking like a bug. */
    private fun scheduleWarningRow(context: Context): PreferenceRow = PreferenceRow.Info(
        key = "${KEY_PREFIX}schedule_conflict",
        title = text.string(R.string.schedule_conflict_title),
        summary = text.string(R.string.schedule_conflict_summary),
        icon = text.icon(context, R.drawable.ic_warning),
    )

    /**
     * Stores the join without redrawing, so the button group finishes its own animation.
     *
     * @return the stored rule, or null when nothing changed.
     */
    private fun setJoin(category: Any, mode: ModeRef, index: Int, join: ModeRule.Join): ModeRule? =
        update(category, mode) { it.takeIf { rule -> index < rule.joins.size && rule.joins[index] != join }?.withJoin(index, join) }

    private fun setStability(category: Any, mode: ModeRef, millis: Long): ModeRule? =
        update(category, mode) { it.takeIf { rule -> rule.stabilityMillis != millis }?.withStability(millis) }

    /**
     * Applies [change] to the stored rule and stores the result.
     *
     * The rule is read again first, so a change stored since the page was drawn is not lost.
     *
     * @return the stored rule, or null when [change] made none.
     */
    private fun update(category: Any, mode: ModeRef, change: (ModeRule) -> ModeRule?): ModeRule? {
        val current = rules.get(mode.id) ?: return null
        val updated = change(current) ?: return null
        shown[category] = Shown(updated, mode.hasSchedule)
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
        private const val STABILITY_KEY = "${KEY_PREFIX}stability"
    }
}
