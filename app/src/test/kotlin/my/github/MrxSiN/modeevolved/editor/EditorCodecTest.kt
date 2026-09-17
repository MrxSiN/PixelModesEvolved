package my.github.MrxSiN.modeevolved.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

import my.github.MrxSiN.modeevolved.action.BroadcastAction
import my.github.MrxSiN.modeevolved.action.ModeAction
import my.github.MrxSiN.modeevolved.action.ModeActions
import my.github.MrxSiN.modeevolved.action.TaskerTaskAction
import my.github.MrxSiN.modeevolved.action.Timing
import my.github.MrxSiN.modeevolved.trigger.AreaTrigger
import my.github.MrxSiN.modeevolved.trigger.BluetoothTrigger
import my.github.MrxSiN.modeevolved.trigger.GeoPoint
import my.github.MrxSiN.modeevolved.trigger.ModeRule
import my.github.MrxSiN.modeevolved.trigger.Movement
import my.github.MrxSiN.modeevolved.trigger.MovementTrigger
import my.github.MrxSiN.modeevolved.trigger.Trigger
import my.github.MrxSiN.modeevolved.trigger.WifiTrigger

class EditorCodecTest {

    private val triggers = ItemFamilies.triggers.codec
    private val actions = ItemFamilies.actions.codec

    @Test
    fun `input with every fact round trips`() {
        val input = EditorInput<Trigger>(
            modeId = "work",
            modeName = "Work",
            original = AreaTrigger(GeoPoint(3.1, 101.6), 250.0),
            facts = EditorFacts(
                currentSsid = "Office",
                savedSsids = listOf("Home", "Office"),
                pairedDevices = listOf(BluetoothTrigger("AA:BB:CC:DD:EE:FF", "Car")),
                lastLocation = GeoPoint(3.2, 101.7),
                taskerTasks = listOf("Quiet", "Loud"),
            ),
        )
        assertEquals(input, triggers.decodeInput(triggers.encodeInput(input)))
    }

    @Test
    fun `minimal input round trips`() {
        val input = EditorInput<Trigger>(modeId = "work", modeName = "Work")
        assertEquals(input, triggers.decodeInput(triggers.encodeInput(input)))
    }

    @Test
    fun `trigger and action results round trip`() {
        val save = EditorResult.Save<Trigger>("work", WifiTrigger("Old"), WifiTrigger("Office"))
        val add = EditorResult.Save<Trigger>("work", null, MovementTrigger(Movement.STILL))
        val remove = EditorResult.Remove<Trigger>("work", WifiTrigger("Old"))
        for (result in listOf(save, add, remove)) assertEquals(result, triggers.decodeResult(triggers.encodeResult(result)))

        val action = EditorResult.Save<ModeAction>("work", null, BroadcastAction(Timing.END, "com.example.QUIET", null))
        assertEquals(action, actions.decodeResult(actions.encodeResult(action)))
        assertNull(triggers.decodeResult("nonsense"))
    }

    @Test
    fun `results apply to a rule`() {
        val rule = ModeRule("work", listOf(WifiTrigger("Old"), MovementTrigger(Movement.STILL)), listOf(ModeRule.Join.OR))

        assertEquals(
            ModeRule("work", listOf(WifiTrigger("New"), MovementTrigger(Movement.STILL)), listOf(ModeRule.Join.OR)),
            EditorResult.Save<Trigger>("work", WifiTrigger("Old"), WifiTrigger("New")).applyTo(rule),
        )
        assertEquals(
            ModeRule(
                "work",
                listOf(WifiTrigger("Old"), MovementTrigger(Movement.STILL), WifiTrigger("Home")),
                listOf(ModeRule.Join.OR, ModeRule.Join.AND),
            ),
            EditorResult.Save<Trigger>("work", null, WifiTrigger("Home")).applyTo(rule),
        )
        assertEquals(
            ModeRule("work", listOf(MovementTrigger(Movement.STILL))),
            EditorResult.Remove<Trigger>("work", WifiTrigger("Old")).applyTo(rule),
        )
    }

    @Test
    fun `results apply to actions`() {
        val quiet = TaskerTaskAction(Timing.START, "Quiet")
        val loud = TaskerTaskAction(Timing.END, "Loud")
        val list = ModeActions("work", listOf(quiet))

        assertEquals(ModeActions("work", listOf(quiet, loud)), EditorResult.Save<ModeAction>("work", null, loud).applyTo(list))
        assertEquals(ModeActions("work", listOf(loud)), EditorResult.Save<ModeAction>("work", quiet, loud).applyTo(list))
        assertEquals(ModeActions("work"), EditorResult.Remove<ModeAction>("work", quiet).applyTo(list))
    }
}
