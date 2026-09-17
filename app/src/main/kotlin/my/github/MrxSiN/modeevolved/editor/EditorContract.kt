package my.github.MrxSiN.modeevolved.editor

import org.json.JSONArray
import org.json.JSONObject

import my.github.MrxSiN.modeevolved.store.KindCodec
import my.github.MrxSiN.modeevolved.store.ModeItems
import my.github.MrxSiN.modeevolved.trigger.BluetoothTrigger
import my.github.MrxSiN.modeevolved.trigger.GeoPoint

/** What the editor edits. Each subject has its own request code, so answers never mix. */
enum class EditorSubject(val requestCode: Int) {
    // "ME" and "MF" in ASCII.
    TRIGGER(0x4D45),
    ACTION(0x4D46),
    CALENDAR_EVENT(0x4D47),
    ;

    companion object {
        fun ofRequest(code: Int): EditorSubject? = entries.firstOrNull { it.requestCode == code }
    }
}

/**
 * What the device knows that the editor offers as choices.
 *
 * The editor runs as an ordinary app. Saved networks, paired devices, the
 * last position and Tasker tasks are only readable by the Settings
 * app, so Settings reads them and passes them along.
 */
data class EditorFacts(
    val currentSsid: String? = null,
    val savedSsids: List<String> = emptyList(),
    val pairedDevices: List<BluetoothTrigger> = emptyList(),
    val lastLocation: GeoPoint? = null,
    val taskerTasks: List<String> = emptyList(),
)

/**
 * What the Settings page hands the editor.
 *
 * @property modeId the Mode the item belongs to, handed back in the result.
 * @property original the item being edited, or null when adding one.
 */
data class EditorInput<I : Any>(
    val modeId: String,
    val modeName: String,
    val original: I? = null,
    val facts: EditorFacts = EditorFacts(),
)

/** What the editor hands back. */
sealed interface EditorResult<I : Any> {

    val modeId: String

    /** [list] with this result applied. */
    fun <L : ModeItems<L, I>> applyTo(list: L): L = when (this) {
        is Save -> original?.let { list.replace(it, item) } ?: list.plus(item)
        is Remove -> list.minus(original)
    }

    /** Replace [original] with [item], or add [item] when [original] is null. */
    data class Save<I : Any>(override val modeId: String, val original: I?, val item: I) : EditorResult<I>

    data class Remove<I : Any>(override val modeId: String, val original: I) : EditorResult<I>
}

/**
 * The editor's handshake with the Settings page, as text.
 *
 * Both sides carry one JSON string extra, so the format lives in one place
 * and is testable without Android.
 */
class EditorCodec<I : Any>(private val items: KindCodec<I>) {

    fun encodeInput(input: EditorInput<I>): String = JSONObject()
        .put(MODE_ID, input.modeId)
        .put(MODE_NAME, input.modeName)
        .putOpt(ORIGINAL, input.original?.let(items::encode))
        .put(FACTS, FactsCodec.encode(input.facts))
        .toString()

    fun decodeInput(text: String): EditorInput<I> {
        val json = JSONObject(text)
        return EditorInput(
            modeId = json.getString(MODE_ID),
            modeName = json.getString(MODE_NAME),
            original = json.optJSONObject(ORIGINAL)?.let(items::decode),
            facts = json.optJSONObject(FACTS)?.let(FactsCodec::decode) ?: EditorFacts(),
        )
    }

    fun encodeResult(result: EditorResult<I>): String = when (result) {
        is EditorResult.Save -> JSONObject()
            .put(MODE_ID, result.modeId)
            .put(ACTION, SAVE)
            .putOpt(ORIGINAL, result.original?.let(items::encode))
            .put(ITEM, items.encode(result.item))
        is EditorResult.Remove -> JSONObject()
            .put(MODE_ID, result.modeId)
            .put(ACTION, REMOVE)
            .put(ORIGINAL, items.encode(result.original))
    }.toString()

    /** The result in [text], or null when it cannot be read. */
    fun decodeResult(text: String): EditorResult<I>? = runCatching {
        val json = JSONObject(text)
        val modeId = json.getString(MODE_ID)
        val original = json.optJSONObject(ORIGINAL)?.let(items::decode)
        when (json.getString(ACTION)) {
            SAVE -> items.decode(json.getJSONObject(ITEM))?.let { EditorResult.Save(modeId, original, it) }
            REMOVE -> original?.let { EditorResult.Remove(modeId, it) }
            else -> null
        }
    }.getOrNull()

    private companion object {
        const val MODE_ID = "modeId"
        const val MODE_NAME = "modeName"
        const val ORIGINAL = "original"
        const val FACTS = "facts"
        const val ACTION = "action"
        const val ITEM = "item"
        const val SAVE = "save"
        const val REMOVE = "remove"
    }
}

/** [EditorFacts] as JSON. */
internal object FactsCodec {

    fun encode(facts: EditorFacts): JSONObject = JSONObject()
        .putOpt(CURRENT_SSID, facts.currentSsid)
        .put(SAVED_SSIDS, JSONArray(facts.savedSsids))
        .put(PAIRED_DEVICES, JSONArray(facts.pairedDevices.map { JSONObject().put(ADDRESS, it.address).put(NAME, it.name) }))
        .putOpt(LAST_LOCATION, facts.lastLocation?.let { JSONObject().put(LATITUDE, it.latitude).put(LONGITUDE, it.longitude) })
        .put(TASKER_TASKS, JSONArray(facts.taskerTasks))

    fun decode(json: JSONObject): EditorFacts = EditorFacts(
        currentSsid = json.optString(CURRENT_SSID).ifEmpty { null },
        savedSsids = json.optJSONArray(SAVED_SSIDS).strings(),
        pairedDevices = json.optJSONArray(PAIRED_DEVICES).objects().map { BluetoothTrigger(it.getString(ADDRESS), it.optString(NAME)) },
        lastLocation = json.optJSONObject(LAST_LOCATION)?.let { GeoPoint(it.getDouble(LATITUDE), it.getDouble(LONGITUDE)) },
        taskerTasks = json.optJSONArray(TASKER_TASKS).strings(),
    )

    private fun JSONArray?.strings() = if (this == null) emptyList() else (0 until length()).map(::getString)
    private fun JSONArray?.objects() = if (this == null) emptyList() else (0 until length()).map(::getJSONObject)
    private const val CURRENT_SSID = "currentSsid"
    private const val SAVED_SSIDS = "savedSsids"
    private const val PAIRED_DEVICES = "pairedDevices"
    private const val LAST_LOCATION = "lastLocation"
    private const val TASKER_TASKS = "taskerTasks"
    private const val ADDRESS = "address"
    private const val NAME = "name"
    private const val LATITUDE = "latitude"
    private const val LONGITUDE = "longitude"
}

/** Names both processes use to reach the editor and read its answer. */
object EditorIntents {
    const val PACKAGE = "my.github.MrxSiN.modeevolved"
    const val ACTIVITY = "$PACKAGE.editor.EditorActivity"
    const val EXTRA_SUBJECT = "$PACKAGE.extra.SUBJECT"
    const val EXTRA_INPUT = "$PACKAGE.extra.INPUT"
    const val EXTRA_RESULT = "$PACKAGE.extra.RESULT"

    /** The only caller the editor answers. */
    const val SETTINGS_PACKAGE = "com.android.settings"
}
