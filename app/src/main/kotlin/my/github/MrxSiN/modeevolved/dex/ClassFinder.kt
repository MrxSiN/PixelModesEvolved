package my.github.MrxSiN.modeevolved.dex

import my.github.MrxSiN.modeevolved.compat.ClassApi
import my.github.MrxSiN.modeevolved.compat.Locator
import my.github.MrxSiN.modeevolved.core.Resolution

/** A host class, and how it was found. */
data class Located(val type: Class<*>, val resolution: Resolution)

/**
 * Finds the host classes the contracts name.
 *
 * The contract says what a class is and what may stand in for it ([ClassApi.locator]);
 * a finder is the policy for how far to go looking. Member lookups sit above this, in
 * [HostResolver], so every finder answers them the same way.
 */
interface ClassFinder {

    val classLoader: ClassLoader

    /** [api]'s class, or what legitimately replaced it, or null when neither can be found. */
    fun locate(api: ClassApi): Located?

    /**
     * Every class [api]'s structural locator matches in this host, by name, or null when
     * this finder does not search. What a host description records, so CI can see that
     * a fallback would still find exactly one class.
     */
    fun candidates(api: ClassApi): List<String>?

    /** A class by its name alone, for describing the host; null when it has none. */
    fun byName(name: String): Class<*>? = classLoader.loadOrNull(name)
}

/**
 * A finder that only knows names: system_server's, where searching the boot jars'
 * dex would cost every boot.
 */
class NamedClassFinder(override val classLoader: ClassLoader) : ClassFinder {

    override fun locate(api: ClassApi): Located? = classLoader.loadOrNull(api.knownName)?.let { Located(it, Resolution.BY_NAME) }

    override fun candidates(api: ClassApi): List<String>? = null
}

internal fun ClassLoader.loadOrNull(name: String): Class<*>? = runCatching { loadClass(name) }.getOrNull()

/**
 * The highest class above [start] that declares [locator]'s method, walking up only while each
 * class still has [Locator.Ancestor.sharing]. Null when none does.
 */
internal fun ancestorOf(start: Class<*>, locator: Locator.Ancestor, parameters: Array<Class<*>>): Class<*>? {
    var found: Class<*>? = null
    var walking: Class<*>? = start
    while (walking != null) {
        val type = walking
        if (runCatching { type.getMethod(locator.sharing) }.isFailure) break
        if (runCatching { type.getDeclaredMethod(locator.declaring, *parameters) }.isSuccess) found = type
        walking = type.superclass
    }
    return found
}
