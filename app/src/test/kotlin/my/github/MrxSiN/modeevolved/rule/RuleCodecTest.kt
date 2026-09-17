package my.github.MrxSiN.modeevolved.rule

import org.junit.Assert.assertEquals
import org.junit.Test

import my.github.MrxSiN.modeevolved.trigger.AirplaneTrigger
import my.github.MrxSiN.modeevolved.trigger.AreaTrigger
import my.github.MrxSiN.modeevolved.trigger.BluetoothTrigger
import my.github.MrxSiN.modeevolved.trigger.GeoPoint
import my.github.MrxSiN.modeevolved.trigger.ModeRule
import my.github.MrxSiN.modeevolved.trigger.Movement
import my.github.MrxSiN.modeevolved.trigger.MovementTrigger
import my.github.MrxSiN.modeevolved.trigger.WifiTrigger

class RuleCodecTest {

    private val codec = RuleCodec.standard()

    @Test
    fun `every trigger kind round trips`() {
        val rules = listOf(
            ModeRule(
                modeId = "work",
                triggers = listOf(
                    WifiTrigger("Office \"5G\""),
                    BluetoothTrigger("AA:BB:CC:DD:EE:FF", "Car"),
                    AreaTrigger(GeoPoint(3.139, 101.6869), 200.0),
                    MovementTrigger(Movement.MOVING),
                    AirplaneTrigger(on = true),
                ),
                joins = listOf(ModeRule.Join.AND, ModeRule.Join.OR, ModeRule.Join.AND, ModeRule.Join.OR),
            ),
            ModeRule("home", triggers = listOf(WifiTrigger("Home"))),
        )
        assertEquals(rules.toSet(), codec.decode(codec.encode(rules)).toSet())
    }

    @Test
    fun `unknown trigger kind is dropped with the join before it`() {
        val text = """{"m":{"triggers":[{"type":"wifi","ssid":"A"},{"type":"nfc"},{"type":"wifi","ssid":"B"}],"joins":["AND","OR"]}}"""
        assertEquals(
            listOf(ModeRule("m", listOf(WifiTrigger("A"), WifiTrigger("B")), listOf(ModeRule.Join.OR))),
            codec.decode(text),
        )
    }

    @Test
    fun `rules stored with match still load`() {
        val any = """{"m":{"match":"ANY","triggers":[{"type":"wifi","ssid":"A"},{"type":"wifi","ssid":"B"}]}}"""
        val all = """{"m":{"match":"ALL","triggers":[{"type":"wifi","ssid":"A"},{"type":"wifi","ssid":"B"}]}}"""
        assertEquals(listOf(ModeRule.Join.OR), codec.decode(any).single().joins)
        assertEquals(listOf(ModeRule.Join.AND), codec.decode(all).single().joins)
    }

    @Test
    fun `unreadable rule is dropped alone`() {
        val text = """{"bad":{"joins":["XOR"],"triggers":[{"type":"wifi","ssid":"A"},{"type":"wifi","ssid":"B"}]},"m":{"triggers":[]}}"""
        assertEquals(listOf(ModeRule("m")), codec.decode(text))
    }

    @Test
    fun `missing or garbage text holds no rules`() {
        assertEquals(emptyList<ModeRule>(), codec.decode(null))
        assertEquals(emptyList<ModeRule>(), codec.decode("not json"))
    }
}
