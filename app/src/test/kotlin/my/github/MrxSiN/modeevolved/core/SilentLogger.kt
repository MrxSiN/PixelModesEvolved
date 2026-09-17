package my.github.MrxSiN.modeevolved.core

/** A [Logger] for tests that discards everything. */
object SilentLogger : Logger {
    override fun info(message: String) = Unit
    override fun warn(message: String, error: Throwable?) = Unit
}
