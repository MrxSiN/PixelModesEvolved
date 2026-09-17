package my.github.MrxSiN.modeevolved.store

import org.json.JSONObject

import my.github.MrxSiN.modeevolved.core.castOrNull

/**
 * How one kind of [B] is written to and read from storage.
 *
 * Triggers and actions are both open families of kinds. Each kind is one
 * object extending this, registered in a [KindCodec], so a new kind is added
 * without touching the others.
 *
 * @property id the stored type name. Changing it orphans every saved item of this kind.
 */
abstract class JsonKind<B : Any, T : B>(val id: String, private val type: Class<T>) {

    /** [item] as this kind, or null when it belongs to another kind. */
    fun cast(item: B): T? = type.castOrNull(item)

    /** [item] as JSON, or null when it belongs to another kind. */
    fun encode(item: B): JSONObject? = cast(item)?.let { write(it).put(TYPE, id) }

    abstract fun decode(json: JSONObject): T

    protected abstract fun write(item: T): JSONObject

    companion object {
        const val TYPE = "type"
    }
}

/** Reads and writes any [B] through the kinds registered for it. */
class KindCodec<B : Any>(private val kinds: List<JsonKind<B, *>>) {

    fun encode(item: B): JSONObject =
        kinds.firstNotNullOfOrNull { it.encode(item) }
            ?: throw IllegalArgumentException("No stored kind for ${item::class.java.name}")

    /** The item stored as [json], or null when its kind is unknown or its fields unreadable. */
    fun decode(json: JSONObject): B? {
        val type = json.optString(JsonKind.TYPE)
        return kinds.firstOrNull { it.id == type }?.let { kind -> runCatching { kind.decode(json) }.getOrNull() }
    }
}
