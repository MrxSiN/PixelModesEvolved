package my.github.MrxSiN.modeevolved.page

import java.util.WeakHashMap

import my.github.MrxSiN.modeevolved.R
import my.github.MrxSiN.modeevolved.action.ModeActions
import my.github.MrxSiN.modeevolved.editor.ItemFamilies
import my.github.MrxSiN.modeevolved.presentation.Timings
import my.github.MrxSiN.modeevolved.store.ModeStore

/**
 * A "Mode actions" card on each Mode's page, right under "Mode settings".
 *
 * One row per action, saying what it does and whether it runs as the Mode
 * turns on or off, and one row to add an action. Rows open the editor.
 */
class ActionSection(
    private val preferences: PreferenceApi,
    private val actions: ModeStore<ModeActions>,
    private val editor: EditorLauncher,
    private val text: ModuleText,
) : ModePageSection {

    private val family = ItemFamilies.actions

    /** What each category last showed, so unchanged actions are not drawn again. */
    private val shown = WeakHashMap<Any, ModeActions>()

    override fun render(screen: Any, mode: ModeRef) {
        val category = preferences.ensureCategory(screen, CATEGORY, text.string(R.string.actions_title), TriggerSection.CATEGORY)
            ?: return
        val list = actions.get(mode.id) ?: ModeActions(mode.id)
        if (shown[category] == list && preferences.find(category, ADD_KEY) != null) return
        shown[category] = list
        preferences.replaceRows(category, KEY_PREFIX, rows(category, mode, list))
    }

    private fun rows(category: Any, mode: ModeRef, list: ModeActions): List<PreferenceRow> = buildList {
        val context = preferences.context(category)
        list.items.forEachIndexed { index, action ->
            add(
                PreferenceRow.Link(
                    key = "${KEY_PREFIX}action_$index",
                    title = family.presentations.describe(text.raw, action),
                    summary = text.string(Timings.summaryOf(action.timing)),
                    icon = text.icon(context, family.presentations.of(action)?.icon),
                ) { editor.open(context, family, mode, action) },
            )
        }
        add(
            PreferenceRow.Link(
                key = ADD_KEY,
                title = text.string(R.string.actions_add_title),
                summary = text.string(R.string.actions_add_summary),
                icon = text.icon(context, R.drawable.ic_trigger_add),
            ) { editor.open(context, family, mode, null) },
        )
    }

    private companion object {
        /** Starts like every key this module adds, so Settings hooks recognise the whole card as this module's. */
        const val CATEGORY = "${TriggerSection.KEY_PREFIX}actions"

        const val KEY_PREFIX = "${TriggerSection.KEY_PREFIX}actions_"
        const val ADD_KEY = "${KEY_PREFIX}add"
    }
}
