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
                    BroadcastAction(Timing.END, "com.example.QUIET", "net.dinglisch.android.taskerm"),
                    BroadcastAction(Timing.START, "com.example.ANY", null),
                    TaskerTaskAction(Timing.END, "Loud"),
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

        ActionAutomation(store, states, { action, _, active -> runs += action to active }, DIRECT, SilentLogger).start()
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

        ActionAutomation(store, states, { action, _, _ -> ran += action; if (action == first) error("boom") }, DIRECT, SilentLogger).start()
        states.set("work", true)
        assertEquals(listOf<ModeAction>(first, second), ran)
    }

    @Test
    fun `stopped automation runs nothing`() {
        val states = FakeStates(mutableMapOf("work" to false))
        val open = TaskerTaskAction(Timing.START, "Open")
        val ran = mutableListOf<ModeAction>()
        val automation = ActionAutomation(FakeSource(listOf(ModeActions("work", listOf(open)))), states, { action, _, _ -> ran += action }, DIRECT, SilentLogger)

        automation.start()
        automation.stop()
        states.set("work", true)
        assertEquals(emptyList<ModeAction>(), ran)
    }

    private class FakeSource(private val lists: List<ModeActions>) : ModeSource<ModeActions> {
        override fun all() = lists
        override fun observe(listener: () -> Unit) = AutoCloseable {}
    }

    private class FakeStates(private val states: MutableMap<String, Boolean>) : ModeStates {
        private var listener: () -> Unit = {}
        override fun isActive(modeId: String) = states[modeId]
        override fun observe(listener: () -> Unit): AutoCloseable {
            this.listener = listener
            return AutoCloseable { this.listener = {} }
        }

        fun set(modeId: String, active: Boolean) {
            states[modeId] = active
            listener()
        }
    }

    private companion object {
        val DIRECT = Executor { it.run() }
    }
}
