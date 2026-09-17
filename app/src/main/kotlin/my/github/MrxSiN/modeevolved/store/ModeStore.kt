package my.github.MrxSiN.modeevolved.store

import android.content.ContentResolver
import android.database.ContentObserver
import android.provider.Settings

import org.json.JSONObject

/** Read side of what is stored per Mode: all system_server needs. */
interface ModeSource<L> {

    fun all(): List<L>

    /** Calls [listener], on any thread, after anything stored changes, until the returned handle is closed. */
    fun observe(listener: () -> Unit): AutoCloseable
}

/** Read and write side: what the Settings Mode page needs. */
interface ModeStore<L> : ModeSource<L> {

    fun get(modeId: String): L?

    /** Stores [list]. A list without items is removed, since it can never do anything. */
    fun save(list: L)
}

/**
 * Converts every stored list to and from one JSON text: an object keyed by mode id.
 *
 * A list that cannot be read is dropped alone, so one bad entry never hides the rest.
 */
abstract class ModeMapCodec<L : ModeItems<L, *>> {

    fun encode(lists: Collection<L>): String {
        val json = JSONObject()
        for (list in lists) json.put(list.modeId, encodeOne(list))
        return json.toString()
    }

    fun decode(text: String?): List<L> {
        if (text.isNullOrBlank()) return emptyList()
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return emptyList()
        return json.keys().asSequence()
            .mapNotNull { id -> runCatching { decodeOne(id, json.getJSONObject(id)) }.getOrNull() }
            .toList()
    }

    protected abstract fun encodeOne(list: L): JSONObject

    protected abstract fun decodeOne(modeId: String, json: JSONObject): L
}

/**
 * Lists kept in one `Settings.Secure` entry.
 *
 * Both processes that touch them run as the system uid: the Settings app
 * writes them and system_server reads them. Secure settings are a store both
 * can already reach, and they notify observers of every change.
 *
 * @param key the entry, readable with `adb shell settings get secure <key>`.
 */
class SecureSettingsStore<L : ModeItems<L, *>>(
    private val resolver: ContentResolver,
    private val key: String,
    private val codec: ModeMapCodec<L>,
) : ModeStore<L> {

    override fun all(): List<L> = codec.decode(Settings.Secure.getString(resolver, key))

    override fun get(modeId: String): L? = all().firstOrNull { it.modeId == modeId }

    override fun save(list: L) {
        val others = all().filterNot { it.modeId == list.modeId }
        val kept = if (list.items.isEmpty()) others else others + list
        Settings.Secure.putString(resolver, key, codec.encode(kept))
    }

    override fun observe(listener: () -> Unit): AutoCloseable {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) = listener()
        }
        resolver.registerContentObserver(Settings.Secure.getUriFor(key), false, observer)
        return AutoCloseable { resolver.unregisterContentObserver(observer) }
    }

    companion object {
        const val RULES_KEY = "mode_evolved_rules"
        const val ACTIONS_KEY = "mode_evolved_actions"
    }
}
