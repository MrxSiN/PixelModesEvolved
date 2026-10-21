package my.github.MrxSiN.modeevolved.action

import org.json.JSONObject

import my.github.MrxSiN.modeevolved.core.Logger

/**
 * Reads and writes device settings, in each setting's own units.
 *
 * Every call may throw: the setting is unavailable on this build, or cannot be changed for
 * the user in the foreground now. A throw costs that one setting that one time.
 */
interface SettingBackend {

    /** The setting's value now. */
    fun read(setting: SystemSetting): Int

    /** Writes [native] and returns the value to compare later readings with. */
    fun write(setting: SystemSetting, native: Int): Int

    /** [value], as the editor stores it, in the setting's own units. */
    fun toNative(setting: SystemSetting, value: Int): Int

    /** What the setting applies to now, such as the data subscription; a claim never restores onto another. */
    fun target(setting: SystemSetting): Int = 0
}

/**
 * Decides what each device setting is while Modes claim it (STATE_ENGINE.md, "Setting claims").
 *
 * - The first claim on a setting reads it once and keeps that as the baseline.
 * - The most recently activated Mode still on wins; the setting is written only when its
 *   value differs from what is there.
 * - When the winner ends, the next one applies; when the last one ends, the baseline comes back
 *   if that claim asked for it and the setting still holds what was last written here.
 * - A setting someone changed while claimed is theirs: it is not written again until a Mode
 *   turns on or a claim is saved, and is not restored.
 *
 * Ownership survives a reboot or a module update ([load], [adopt], [finishLoad]) without
 * replaying an activation: a Mode found on at load keeps its claim and nothing is written.
 *
 * Some settings move each other: Android puts the ringer on Ring when the ring volume goes above
 * zero. So a Mode turning on reads every baseline before it writes anything, settings are applied
 * in [SystemSetting] order and given back in reverse, so a restored volume never undoes the
 * ringer restored after it.
 *
 * Not thread-safe. Every call runs on the automation thread.
 *
 * @param persist stores [snapshot] text, so ownership outlives this process.
 */
class SettingClaims(
    private val backend: SettingBackend,
    private val logger: Logger,
    private val persist: (String) -> Unit,
) {

    private val settings = SystemSetting.entries
    private val owned = BooleanArray(settings.size)
    private val baseline = IntArray(settings.size)
    private val written = IntArray(settings.size)
    private val target = IntArray(settings.size)
    private val restoreOnEnd = BooleanArray(settings.size)
    private val claims = Array(settings.size) { ArrayList<Claim>(2) }

    /** Activation order of each Mode holding claims; the highest wins. */
    private val activated = HashMap<String, Long>()

    /** What [load] found, until [adopt] confirms each Mode is still on. */
    private val pending = HashMap<String, Long>()
    private var clock = 0L
    private var stored = false

    private data class Claim(val modeId: String, val value: Int, val restore: Boolean, val order: Long)

    /** [modeId] turned on: its claims win every setting they name. */
    fun activate(modeId: String, actions: List<SystemSettingAction>) {
        if (actions.isEmpty()) return
        val order = ++clock
        activated[modeId] = order
        for (action in actions) put(action, modeId, order)
        for (index in settings.indices) if (holds(index, modeId)) guarded(settings[index]) { own(index) }
        for (index in settings.indices) if (holds(index, modeId)) settle(index, modeId)
        save()
    }

    /** [modeId] turned off: each setting it held goes to the next claim, or back to its baseline. */
    fun deactivate(modeId: String) {
        if (activated.remove(modeId) == null) return
        for (index in settings.size - 1 downTo 0) release(index, modeId)
        save()
    }

    /** [modeId], still on, now has [actions]: a saved claim applies at once, a removed one ends. */
    fun update(modeId: String, actions: List<SystemSettingAction>) {
        val order = activated[modeId] ?: return activate(modeId, actions)
        for (index in settings.size - 1 downTo 0) {
            if (actions.none { it.setting.ordinal == index }) release(index, modeId)
        }
        // As in activate: every new baseline is read before anything is written, in setting order.
        val changed = BooleanArray(settings.size)
        for (action in actions) {
            val held = claims[action.setting.ordinal].find { it.modeId == modeId }
            if (held != null && held.value == action.value && held.restore == action.restore) continue
            put(action, modeId, order)
            changed[action.setting.ordinal] = true
        }
        for (index in settings.indices) if (changed[index]) guarded(settings[index]) { own(index) }
        for (index in settings.indices) if (changed[index]) settle(index, modeId)
        if (actions.isEmpty()) activated.remove(modeId)
        save()
    }

    /** Takes back what [snapshot] stored, before the first look at which Modes are on. */
    fun load(text: String?) {
        if (text.isNullOrBlank()) return
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return
        stored = true
        clock = json.optLong(CLOCK)
        json.optJSONObject(MODES)?.let { modes -> modes.keys().forEach { pending[it] = modes.getLong(it) } }
        val owners = json.optJSONObject(OWNERS) ?: return
        for (id in owners.keys()) {
            val setting = SystemSetting.of(id) ?: continue
            val owner = owners.getJSONObject(id)
            val index = setting.ordinal
            owned[index] = true
            baseline[index] = owner.getInt(BASELINE)
            written[index] = owner.getInt(WRITTEN)
            target[index] = owner.optInt(TARGET)
            restoreOnEnd[index] = owner.optBoolean(RESTORE, true)
        }
    }

    /** [modeId] was on at load and held claims before: it keeps them, and nothing is written. */
    fun adopt(modeId: String, actions: List<SystemSettingAction>) {
        val order = pending.remove(modeId) ?: return
        activated[modeId] = order
        for (action in actions) put(action, modeId, order)
    }

    /** Ends what no Mode on at load still claims: those Modes went off while this was not running. */
    fun finishLoad() {
        pending.clear()
        for (index in settings.size - 1 downTo 0) {
            if (owned[index] && claims[index].isEmpty()) end(index, restoreOnEnd[index])
        }
        save()
    }

    private fun put(action: SystemSettingAction, modeId: String, order: Long) {
        val list = claims[action.setting.ordinal]
        list.removeAll { it.modeId == modeId }
        list += Claim(modeId, action.value, action.restore, order)
    }

    private fun holds(index: Int, modeId: String): Boolean = claims[index].any { it.modeId == modeId }

    /** Takes the setting's value now as its baseline, unless it is already owned for the same target. Returns the value now. */
    private fun own(index: Int): Int {
        val setting = settings[index]
        val current = backend.read(setting)
        val now = backend.target(setting)
        if (!owned[index] || target[index] != now) {
            owned[index] = true
            baseline[index] = current
            written[index] = current
            target[index] = now
        }
        return current
    }

    private fun release(index: Int, modeId: String) {
        val list = claims[index]
        val held = list.find { it.modeId == modeId } ?: return
        list.remove(held)
        if (list.isEmpty()) end(index, held.restore) else settle(index, touched = null)
    }

    /**
     * Makes setting [index] what its winning claim asks, unless someone changed it since it was
     * last written here. [touched] is the Mode that just turned on or saved a claim: its claim
     * winning is the person asking again, which applies even over such a change.
     */
    private fun settle(index: Int, touched: String?) {
        val winner = claims[index].maxByOrNull { it.order } ?: return
        val setting = settings[index]
        guarded(setting) {
            val current = own(index)
            restoreOnEnd[index] = winner.restore
            if (current != written[index] && winner.modeId != touched) return@guarded
            val desired = backend.toNative(setting, winner.value)
            written[index] = if (desired == current) current else backend.write(setting, desired).also {
                logger.info("Setting ${setting.id} set for mode ${winner.modeId}")
            }
        }
    }

    /** The last claim on [index] ended: the baseline comes back only onto what was last written here. */
    private fun end(index: Int, restore: Boolean) {
        owned[index] = false
        if (!restore) return
        val setting = settings[index]
        guarded(setting) {
            val current = backend.read(setting)
            when {
                backend.target(setting) != target[index] -> logger.info("Setting ${setting.id} applies elsewhere now; not restored")
                current != written[index] -> logger.info("Setting ${setting.id} was changed while claimed; left as it is")
                current != baseline[index] -> {
                    backend.write(setting, baseline[index])
                    logger.info("Setting ${setting.id} restored")
                }
            }
        }
    }

    /** One setting that fails costs itself, never another setting or the Mode change. */
    private inline fun guarded(setting: SystemSetting, block: () -> Unit) {
        try {
            block()
        } catch (error: Exception) {
            logger.warn("Setting ${setting.id} could not be changed", error)
        }
    }

    /** What must survive this process: who owns which setting, from what baseline. */
    fun snapshot(): String? {
        if (activated.isEmpty() && owned.none { it }) return null
        val owners = JSONObject()
        for (index in settings.indices) {
            if (!owned[index]) continue
            owners.put(
                settings[index].id,
                JSONObject().put(BASELINE, baseline[index]).put(WRITTEN, written[index])
                    .put(TARGET, target[index]).put(RESTORE, restoreOnEnd[index]),
            )
        }
        val modes = JSONObject()
        for ((modeId, order) in activated) modes.put(modeId, order)
        return JSONObject().put(CLOCK, clock).put(MODES, modes).put(OWNERS, owners).toString()
    }

    /** Stores the snapshot, and nothing at all for someone who never claimed a setting. */
    private fun save() {
        val text = snapshot()
        if (text == null && !stored) return
        stored = text != null
        runCatching { persist(text.orEmpty()) }.onFailure { logger.warn("Could not store setting claims", it) }
    }

    private companion object {
        const val CLOCK = "clock"
        const val MODES = "modes"
        const val OWNERS = "owners"
        const val BASELINE = "baseline"
        const val WRITTEN = "written"
        const val TARGET = "target"
        const val RESTORE = "restore"
    }
}
