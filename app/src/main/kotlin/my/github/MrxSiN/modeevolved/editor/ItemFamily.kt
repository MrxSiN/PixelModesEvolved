package my.github.MrxSiN.modeevolved.editor

import android.content.ContentResolver

import my.github.MrxSiN.modeevolved.action.ActionCodec
import my.github.MrxSiN.modeevolved.action.ModeAction
import my.github.MrxSiN.modeevolved.action.ModeActions
import my.github.MrxSiN.modeevolved.presentation.ActionPresentations
import my.github.MrxSiN.modeevolved.presentation.PresentationSet
import my.github.MrxSiN.modeevolved.presentation.TriggerPresentations
import my.github.MrxSiN.modeevolved.rule.RuleCodec
import my.github.MrxSiN.modeevolved.store.KindCodec
import my.github.MrxSiN.modeevolved.store.ModeItems
import my.github.MrxSiN.modeevolved.store.ModeMapCodec
import my.github.MrxSiN.modeevolved.store.ModeStore
import my.github.MrxSiN.modeevolved.store.SecureSettingsStore
import my.github.MrxSiN.modeevolved.trigger.ModeRule
import my.github.MrxSiN.modeevolved.trigger.Trigger

/**
 * Everything that differs between editing triggers and editing actions.
 *
 * The Settings page, the editor and the result hook all work through this, so
 * a list of another kind of item is one more family, not a copy of each.
 */
class ItemFamily<I : Any, L : ModeItems<L, I>>(
    val subject: EditorSubject,
    val items: KindCodec<I>,
    val presentations: PresentationSet<I>,
    private val storeKey: String,
    private val lists: ModeMapCodec<L>,
    private val empty: (modeId: String) -> L,
) {

    val codec = EditorCodec(items)

    fun store(resolver: ContentResolver): ModeStore<L> = SecureSettingsStore(resolver, storeKey, lists)

    fun emptyFor(modeId: String): L = empty(modeId)

    /** Applies the editor's answer [text] to what is stored. The answer, or null when it cannot be read. */
    fun applyResult(resolver: ContentResolver, text: String): EditorResult<I>? {
        val result = codec.decodeResult(text) ?: return null
        val store = store(resolver)
        store.save(result.applyTo(store.get(result.modeId) ?: emptyFor(result.modeId)))
        return result
    }
}

object ItemFamilies {

    private val ruleCodec = RuleCodec.standard()
    private val actionCodec = ActionCodec.standard()

    val triggers: ItemFamily<Trigger, ModeRule> = ItemFamily(
        subject = EditorSubject.TRIGGER,
        items = ruleCodec.triggers,
        presentations = TriggerPresentations,
        storeKey = SecureSettingsStore.RULES_KEY,
        lists = ruleCodec,
        empty = { ModeRule(it) },
    )

    val actions: ItemFamily<ModeAction, ModeActions> = ItemFamily(
        subject = EditorSubject.ACTION,
        items = actionCodec.actions,
        presentations = ActionPresentations,
        storeKey = SecureSettingsStore.ACTIONS_KEY,
        lists = actionCodec,
        empty = { ModeActions(it) },
    )

    val all: List<ItemFamily<*, *>> = listOf(triggers, actions)

    fun of(subject: EditorSubject): ItemFamily<*, *> = all.first { it.subject == subject }
}
