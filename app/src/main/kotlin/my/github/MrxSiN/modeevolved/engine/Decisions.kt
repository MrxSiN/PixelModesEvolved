package my.github.MrxSiN.modeevolved.engine

import org.json.JSONObject

import my.github.MrxSiN.modeevolved.trigger.Signal

/**
 * Why the engine last switched, or waited to switch, each Mode (ROADMAP I13/I16).
 *
 * One entry per Mode, replaced by the next decision, so it never grows past the rules there
 * are. Only the cause and the kinds of signal are kept: no network names, device addresses,
 * places or Mode names, so the text is safe to show and to copy into a report. A Mode the
 * engine never decided about has no entry: what turned it on (a schedule, a person, another
 * app) is not observable from here, and the page says so rather than guessing.
 */
data class Decision(val active: Boolean, val cause: Cause, val signals: Set<Signal>, val atMillis: Long) {

    enum class Cause {
        /** The triggers' answer changed. */
        SIGNALS,

        /** A changed answer is being waited out ([my.github.MrxSiN.modeevolved.trigger.ModeRule.stabilityMillis]). */
        WAITING,

        /** The rule was saved, which applies it at once. */
        SAVED,

        /** At load, a Mode this module had left on whose triggers no longer hold. */
        LOADED,

        /** The rule was removed while it held its Mode on. */
        REMOVED,
    }
}

/** [Decision]s by Mode id, as text for a secure setting. Unknown entries from another build are dropped. */
object DecisionCodec {

    /** The secure setting system_server writes and Settings reads. */
    const val KEY = "mode_evolved_decisions"

    fun encode(decisions: Map<String, Decision>): String {
        val json = JSONObject()
        for ((modeId, decision) in decisions) {
            json.put(
                modeId,
                JSONObject().put(ON, decision.active).put(CAUSE, decision.cause.name)
                    .put(SIGNALS, decision.signals.joinToString(",") { it.name }).put(AT, decision.atMillis),
            )
        }
        return json.toString()
    }

    fun decode(text: String?): Map<String, Decision> {
        val json = text?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return emptyMap()
        val decisions = LinkedHashMap<String, Decision>()
        for (modeId in json.keys()) {
            val entry = json.optJSONObject(modeId) ?: continue
            val cause = Decision.Cause.entries.firstOrNull { it.name == entry.optString(CAUSE) } ?: continue
            val signals = entry.optString(SIGNALS).split(',').mapNotNullTo(LinkedHashSet()) { name ->
                Signal.entries.firstOrNull { it.name == name }
            }
            decisions[modeId] = Decision(entry.optBoolean(ON), cause, signals, entry.optLong(AT))
        }
        return decisions
    }

    private const val ON = "on"
    private const val CAUSE = "cause"
    private const val SIGNALS = "signals"
    private const val AT = "at"
}
