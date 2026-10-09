package my.github.MrxSiN.modeevolved.action

import java.util.concurrent.Executor

import org.junit.Assert.assertEquals
import org.junit.Test

import my.github.MrxSiN.modeevolved.core.SilentLogger
import my.github.MrxSiN.modeevolved.store.ModeSource

class ActionTest {

    private val codec = ActionCodec.standard()

    @Test
    fun `every action kind round trips`() {
        val lists = listOf(
            ModeActions(
                "work",
                listOf(
                    LaunchAppAction(Timing.START, "com.example.notes", "Notes"),
                    BroadcastAction(Timing.END, "com.example.QUIET", "net.dinglisch.android.taskerm", cooldownMillis = 60_000L),
                    BroadcastAction(Timing.START, "com.example.ANY", null),
                    TaskerTaskAction(Timing.END, "Loud", cooldownMillis = 300_000L),
                ),
            ),
        )
        assertEquals(lists, codec.decode(codec.encode(lists)))
    }

    @Test
    fun `runs start actions on turning on and end actions on turning off, never on first sight`() {
        val states = FakeStates(mutableMapOf("work" to false))
        val runs = mutableListOf<Pair<ModeAction, Boolean>>()
        val open = LaunchAppAction(Timing.START, "com.example.notes", "Notes")
        val quiet = TaskerTaskAction(Timing.END, "Quiet")
        val store = FakeSource(listOf(ModeActions("work", listOf(open, quiet))))

        automation(store, states) { action, _, active -> runs += action to active }.start()
        assertEquals(emptyList<Pair<ModeAction, Boolean>>(), runs)

        states.set("work", true)
        states.set("work", true)
        states.set("work", false)
        assertEquals(listOf<Pair<ModeAction, Boolean>>(open to true, quiet to false), runs)
    }

    @Test
    fun `a failing action does not stop the next`() {
        val states = FakeStates(mutableMapOf("work" to false))
        val first = TaskerTaskAction(Timing.START, "Broken")
        val second = TaskerTaskAction(Timing.START, "Fine")
        val ran = mutableListOf<ModeAction>()
        val store = FakeSource(listOf(ModeActions("work", listOf(first, second))))

        automation(store, states) { action, _, _ -> ran += action; if (action == first) error("boom") }.start()
        states.set("work", true)
        assertEquals(listOf<ModeAction>(first, second), ran)
    }

    @Test
    fun `stopped automation runs nothing`() {
        val states = FakeStates(mutableMapOf("work" to false))
        val open = TaskerTaskAction(Timing.START, "Open")
        val ran = mutableListOf<ModeAction>()
        val automation = automation(FakeSource(listOf(ModeActions("work", listOf(open)))), states) { action, _, _ -> ran += action }

        automation.start()
        automation.stop()
        states.set("work", true)
        assertEquals(emptyList<ModeAction>(), ran)
    }

    @Test
    fun `an action inside its cooldown is skipped, and runs again once the cooldown is over`() {
        val states = FakeStates(mutableMapOf("work" to false))
        val open = LaunchAppAction(Timing.START, "com.example.notes", "Notes", cooldownMillis = 60_000L)
        val runs = mutableListOf<Long>()
        val store = FakeSource(listOf(ModeActions("work", listOf(open))))

        automation(store, states) { _, _, _ -> runs += clock }.start()

        states.set("work", true)
        assertEquals(listOf(0L), runs)

        // Off and on again half a minute later: a real pair of edges, inside the cooldown.
        clock = 30_000L
        states.set("work", false)
        states.set("work", true)
        assertEquals(listOf(0L), runs)

        clock = 61_000L
        states.set("work", false)
        states.set("work", true)
        assertEquals(listOf(0L, 61_000L), runs)
    }

    @Test
    fun `an action without a cooldown runs on every edge`() {
        val states = FakeStates(mutableMapOf("work" to false))
        val open = LaunchAppAction(Timing.START, "com.example.notes", "Notes")
        var runs = 0
        val store = FakeSource(listOf(ModeActions("work", listOf(open))))

        automation(store, states) { _, _, _ -> runs++ }.start()
        repeat(3) {
            states.set("work", true)
            states.set("work", false)
        }
        assertEquals(3, runs)
    }

    private fun automation(store: ModeSource<ModeActions>, states: ModeStates, runner: ActionRunner) =
        ActionAutomation(store, states, runner, DIRECT, { clock }, SilentLogger)

    /** The monotonic clock the automation reads, moved by hand. */
    private var clock = 0L

    private companion object {
        val DIRECT = Executor { it.run() }
    }
}

/** Stored actions, replaced by hand; replacing them tells the observer, as a write to storage does. */
internal class FakeSource(var lists: List<ModeActions>) : ModeSource<ModeActions> {
    private var listener: () -> Unit = {}
    override fun all() = lists
    override fun observe(listener: () -> Unit) = AutoCloseable { this.listener = {} }.also { this.listener = listener }

    fun replace(lists: List<ModeActions>) {
        this.lists = lists
        listener()
    }
}

/** Mode states set by hand; null is a Mode that no longer exists. */
internal class FakeStates(private val states: MutableMap<String, Boolean?> = mutableMapOf()) : ModeStates {
    private var listener: () -> Unit = {}
    override fun isActive(modeId: String) = states[modeId]
    override fun observe(listener: () -> Unit) = AutoCloseable { this.listener = {} }.also { this.listener = listener }

    fun set(modeId: String, active: Boolean?) {
        states[modeId] = active
        listener()
    }
}
