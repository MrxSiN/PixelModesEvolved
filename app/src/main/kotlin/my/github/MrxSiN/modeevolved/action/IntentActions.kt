package my.github.MrxSiN.modeevolved.action

/**
 * What a broadcast action string may be.
 *
 * A broadcast this module sends leaves system_server, so every receiver sees
 * the system process as its sender and trusts it accordingly. Names the
 * platform defines are therefore refused outright: a Mode must not be able to
 * make an app believe the system announced a package change, a SIM state or a
 * boot. What is left is an app's own action name, which only that app gives
 * meaning to, which is exactly what an automation app listens for.
 *
 * The editor refuses these names as they are typed, and [IntentActionRunner]
 * refuses them again before sending, so a rule stored by an older build, or
 * written straight into `Settings.Secure`, is refused too.
 */
object IntentActions {

    /** Longer than any real action name, and short enough that nothing has to hold an unbounded string. */
    const val MAX_LENGTH = 255

    /** A Java-style dotted name, which is what an `<intent-filter>` can hold. */
    private val WELL_FORMED = Regex("""[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+""")

    /** Prefixes the platform and the apps shipped with it own. */
    private val RESERVED = listOf("android.", "com.android.", "com.google.android.")

    fun isWellFormed(action: String): Boolean = action.length <= MAX_LENGTH && WELL_FORMED.matches(action)

    /** Whether the platform, rather than an app, gives [action] its meaning. */
    fun isReserved(action: String): Boolean = RESERVED.any { action.startsWith(it) }

    /** Whether this module may send [action] at all. */
    fun isSendable(action: String): Boolean = isWellFormed(action) && !isReserved(action)
}
