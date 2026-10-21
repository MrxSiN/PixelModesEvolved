package my.github.MrxSiN.modeevolved.trigger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuleSimulationTest {

    // (Home Wi-Fi AND left still) OR Car, as the README's example.
    private val rule = ModeRule(
        "m",
        listOf(WifiTrigger("Home"), MovementTrigger(Movement.STILL), BluetoothTrigger("AA", "Car")),
        listOf(ModeRule.Join.AND, ModeRule.Join.OR),
    )

    @Test
    fun `AND binds before OR`() {
        assertEquals(listOf(0..1, 2..2), RuleSimulation.groups(rule))
    }

    @Test
    fun `a group counts only when every trigger in it is met`() {
        assertNull(RuleSimulation.metGroup(rule, listOf(true, false, false)))
        assertEquals(0, RuleSimulation.metGroup(rule, listOf(true, true, false)))
        assertEquals(1, RuleSimulation.metGroup(rule, listOf(false, false, true)))
    }

    @Test
    fun `it agrees with the engine's own reading`() {
        val answers = listOf(true, true, false)
        val byTrigger = rule.triggers.zip(answers).toMap()
        assertEquals(rule.isMetWhen { byTrigger.getValue(it) }, RuleSimulation.metGroup(rule, answers) != null)
    }

    @Test
    fun `no triggers means no group`() {
        assertEquals(emptyList<IntRange>(), RuleSimulation.groups(ModeRule("m")))
        assertNull(RuleSimulation.metGroup(ModeRule("m"), emptyList()))
    }
}
