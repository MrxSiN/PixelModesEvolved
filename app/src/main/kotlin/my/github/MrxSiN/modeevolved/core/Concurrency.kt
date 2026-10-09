package my.github.MrxSiN.modeevolved.core

import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/*
 * The concurrency rule, for the whole module (ARCHITECTURE.md, "Concurrency"):
 *
 * - An event source runs only while something needs it, and stops when nothing does.
 * - A change notification says "look again", never "here is one more thing to do": a burst
 *   of them becomes one look at the latest state ([CoalescingTask]).
 * - Expensive work that answers for the latest state replaces any request still waiting,
 *   rather than queueing behind it ([LatestOnly]).
 */

/**
 * Runs [task] on [executor] once per burst of [request]s.
 *
 * A request made while a run is still waiting to start joins that run. One made
 * after the run has started schedules another, so the state [task] reads is never
 * older than the last request. Requests may come from any thread.
 */
class CoalescingTask(private val executor: Executor, private val task: () -> Unit) {

    private val pending = AtomicBoolean(false)

    fun request() {
        if (!pending.compareAndSet(false, true)) return
        executor.execute {
            // Cleared before running, so a change that lands during the run is looked at again.
            pending.set(false)
            task()
        }
    }
}

/**
 * Runs work on [executor] for which only the newest request matters.
 *
 * A request that a newer one supersedes before it starts is skipped. One superseded
 * while it runs can ask `isLatest` before it acts on its answer, and drop it.
 */
class LatestOnly(private val executor: Executor) {

    private val latest = AtomicLong()

    fun submit(work: (isLatest: () -> Boolean) -> Unit) {
        val ticket = latest.incrementAndGet()
        val isLatest = { latest.get() == ticket }
        executor.execute { if (isLatest()) work(isLatest) }
    }
}
