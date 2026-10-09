package my.github.MrxSiN.modeevolved.core

import java.util.concurrent.Executor

import org.junit.Assert.assertEquals
import org.junit.Test

/** Coalescing change notifications, and letting the newest request replace stale ones. */
class ConcurrencyTest {

    /** Runs what it is given only when told to, like a busy thread. */
    private class QueuedExecutor : Executor {
        val queue = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) {
            queue.addLast(command)
        }

        fun drain() {
            while (queue.isNotEmpty()) queue.removeFirst().run()
        }
    }

    private val executor = QueuedExecutor()

    @Test
    fun `a burst of requests is one run`() {
        var runs = 0
        val task = CoalescingTask(executor) { runs++ }

        repeat(5) { task.request() }
        assertEquals(1, executor.queue.size)
        executor.drain()

        assertEquals(1, runs)
    }

    @Test
    fun `a request made while running is not lost`() {
        var runs = 0
        lateinit var task: CoalescingTask
        task = CoalescingTask(executor) {
            runs++
            if (runs == 1) task.request()
        }

        task.request()
        executor.drain()

        assertEquals(2, runs)
    }

    @Test
    fun `only the newest waiting request runs`() {
        val ran = mutableListOf<Int>()
        val latest = LatestOnly(executor)

        (1..3).forEach { request -> latest.submit { ran += request } }
        executor.drain()

        assertEquals(listOf(3), ran)
    }

    @Test
    fun `a request superseded while running can drop its answer`() {
        val latest = LatestOnly(executor)
        var stillLatest: Boolean? = null

        latest.submit { isLatest ->
            latest.submit { }
            stillLatest = isLatest()
        }
        executor.queue.removeFirst().run()

        assertEquals(false, stillLatest)
    }
}
