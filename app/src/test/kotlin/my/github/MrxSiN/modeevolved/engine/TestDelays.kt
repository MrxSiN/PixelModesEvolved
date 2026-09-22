package my.github.MrxSiN.modeevolved.engine

/**
 * [Delays] driven by hand, so a ten-minute wait costs a test nothing.
 *
 * Work runs when [advance] passes its due time, in the order it was scheduled,
 * the same way a handler's queue does.
 */
class TestDelays : Delays {

    private var now = 0L
    private val scheduled = mutableListOf<Task>()

    private class Task(val dueAt: Long, val task: () -> Unit) {
        var cancelled = false
    }

    /** How many waits are still outstanding: what a test asserts was cancelled. */
    val pending: Int get() = scheduled.count { !it.cancelled }

    override fun after(millis: Long, task: () -> Unit): AutoCloseable {
        val entry = Task(now + millis, task).also { scheduled += it }
        return AutoCloseable { entry.cancelled = true }
    }

    /** Moves the clock on by [millis] and runs everything that falls due. */
    fun advance(millis: Long) {
        now += millis
        while (true) {
            val due = scheduled.firstOrNull { !it.cancelled && it.dueAt <= now } ?: return
            scheduled -= due
            due.task()
        }
    }
}
