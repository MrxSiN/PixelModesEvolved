package my.github.MrxSiN.modeevolved.presentation

import android.content.res.Resources

import my.github.MrxSiN.modeevolved.store.JsonKind

/**
 * How one kind of item is named, drawn and described to a person.
 *
 * Shared by the rows on the Settings Mode page and the editor, so both always
 * say the same thing. Takes [Resources] rather than a Context because the
 * Settings page reads this module's resources from another app's process.
 */
abstract class Presentation<B : Any, T : B>(val kind: JsonKind<B, T>) {

    abstract val label: Int

    /** One line on what the kind does, shown where a kind is chosen. */
    abstract val hint: Int

    abstract val icon: Int

    /** [item] in words, or null when it belongs to another kind. */
    fun describe(resources: Resources, item: B): String? = kind.cast(item)?.let { summary(resources, it) }

    protected abstract fun summary(resources: Resources, item: T): String
}

/** Every presentation of one family, in the order a person chooses between them. */
class PresentationSet<B : Any>(val all: List<Presentation<B, *>>) {

    fun of(item: B): Presentation<B, *>? = all.firstOrNull { it.kind.cast(item) != null }

    fun describe(resources: Resources, item: B): String =
        of(item)?.describe(resources, item) ?: item.toString()
}
