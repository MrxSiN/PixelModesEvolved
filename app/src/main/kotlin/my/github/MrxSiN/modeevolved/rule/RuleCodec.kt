package my.github.MrxSiN.modeevolved.rule

import org.json.JSONArray
import org.json.JSONObject

import my.github.MrxSiN.modeevolved.store.KindCodec
import my.github.MrxSiN.modeevolved.store.ModeMapCodec
import my.github.MrxSiN.modeevolved.trigger.ModeRule
import my.github.MrxSiN.modeevolved.trigger.Trigger

/**
 * Converts every stored [ModeRule] to and from JSON.
 *
 * A trigger of a kind this version does not know is dropped from its rule, so
 * a downgrade keeps the triggers it understands.
 */
class RuleCodec(val triggers: KindCodec<Trigger>) : ModeMapCodec<ModeRule>() {

    override fun encodeOne(list: ModeRule): JSONObject {
        val stored = JSONArray()
        list.triggers.forEach { stored.put(triggers.encode(it)) }
        return JSONObject().put(TRIGGERS, stored).put(JOINS, JSONArray(list.joins.map { it.name }))
    }

    /**
     * A dropped trigger takes the join before it along, so the triggers either side stay joined
     * the way the later of them was.
     */
    override fun decodeOne(modeId: String, json: JSONObject): ModeRule {
        val stored = json.getJSONArray(TRIGGERS)
        val joins = json.optJSONArray(JOINS)?.let { array -> (0 until array.length()).map { ModeRule.Join.valueOf(array.getString(it)) } }
            ?: List((stored.length() - 1).coerceAtLeast(0)) { legacyJoin(json) }

        val kept = mutableListOf<Trigger>()
        val keptJoins = mutableListOf<ModeRule.Join>()
        for (index in 0 until stored.length()) {
            val trigger = triggers.decode(stored.getJSONObject(index)) ?: continue
            if (kept.isNotEmpty()) keptJoins += joins[index - 1]
            kept += trigger
        }
        return ModeRule(modeId, kept, keptJoins)
    }

    /** Rules stored before joins existed said "match": "ALL" or "ANY" for every gap at once. */
    private fun legacyJoin(json: JSONObject): ModeRule.Join =
        if (json.optString(LEGACY_MATCH) == "ANY") ModeRule.Join.OR else ModeRule.Join.AND

    companion object {
        private const val TRIGGERS = "triggers"
        private const val JOINS = "joins"
        private const val LEGACY_MATCH = "match"

        fun standard(): RuleCodec = RuleCodec(
            KindCodec(listOf(WifiKind, BluetoothKind, AreaKind, MovementKind, AirplaneKind)),
        )
    }
}
