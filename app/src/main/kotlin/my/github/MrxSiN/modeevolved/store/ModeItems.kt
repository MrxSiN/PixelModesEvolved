package my.github.MrxSiN.modeevolved.store

/**
 * The items one Mode holds: its triggers, or its actions.
 *
 * The editor adds, replaces and removes items the same way for both, so it
 * works against this rather than either concrete list.
 */
interface ModeItems<L : ModeItems<L, I>, I : Any> {

    val modeId: String

    val items: List<I>

    /** Adds [item] at the end. An item the list already has is not added twice. */
    fun plus(item: I): L

    /** Puts [new] in place of [old]. Becomes [plus] when [old] is gone. */
    fun replace(old: I, new: I): L

    fun minus(item: I): L
}
