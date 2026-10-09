package my.github.MrxSiN.modeevolved.action

import java.lang.management.ManagementFactory

import org.junit.Assume.assumeTrue
import org.junit.Test

import my.github.MrxSiN.modeevolved.core.SilentLogger

/**
 * Host-JVM cost of one Mode on/off pair inside [SettingClaims], with a backend that does nothing:
 * the time this module spends before and after the framework calls. Runs only with BENCH=1.
 */
class SettingClaimsBenchmark {

    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    @Test
    fun bench() {
        assumeTrue(System.getenv("BENCH") == "1")
        val cases = linkedMapOf(
            "zero settings" to emptyList(),
            "one switch" to listOf(SystemSettingAction(SystemSetting.AUTO_ROTATE, 0)),
            "one percent" to listOf(SystemSettingAction(SystemSetting.MEDIA_VOLUME, 40)),
            "five mixed" to listOf(
                SystemSettingAction(SystemSetting.AUTO_ROTATE, 0), SystemSettingAction(SystemSetting.BRIGHTNESS, 30),
                SystemSettingAction(SystemSetting.RINGER_MODE, 1), SystemSettingAction(SystemSetting.MEDIA_VOLUME, 40),
                SystemSettingAction(SystemSetting.BLUETOOTH, 0),
            ),
        )
        println("case | ns per on+off pair, median of 15 (min-max) | bytes per pair | backend calls per pair")
        for ((name, actions) in cases) run(name, actions, overlap = false)
        run("overlap: two modes, same five", cases.getValue("five mixed"), overlap = true)
    }

    private fun run(name: String, actions: List<SystemSettingAction>, overlap: Boolean) {
        var calls = 0L
        val backend = object : SettingBackend {
            val values = IntArray(SystemSetting.entries.size)
            override fun read(setting: SystemSetting): Int { calls++; return values[setting.ordinal] }
            override fun write(setting: SystemSetting, native: Int): Int { calls++; values[setting.ordinal] = native; return native }
            override fun toNative(setting: SystemSetting, value: Int) = value
        }
        // Storage is a string write in system_server; measured apart from the arbitration.
        val claims = SettingClaims(backend, SilentLogger) {}
        val other = actions.map { it.copy(value = if (it.setting.value == SettingValue.PERCENT) 70 else it.value xor 1) }
        fun pair() {
            if (overlap) claims.activate("b", other)
            claims.activate("a", actions)
            claims.deactivate("a")
            if (overlap) claims.deactivate("b")
        }
        repeat(20_000) { pair() }
        val runs = LongArray(15)
        var bytes = 0L
        calls = 0
        for (run in runs.indices) {
            val allocated = threads.getThreadAllocatedBytes(Thread.currentThread().id)
            val start = System.nanoTime()
            repeat(ROUNDS) { pair() }
            runs[run] = (System.nanoTime() - start) / ROUNDS
            bytes = (threads.getThreadAllocatedBytes(Thread.currentThread().id) - allocated) / ROUNDS
        }
        runs.sort()
        println("$name | ${runs[7]} (${runs.first()}-${runs.last()}) | $bytes | ${calls / (runs.size * ROUNDS)}")
    }

    private companion object {
        const val ROUNDS = 5_000
    }
}
