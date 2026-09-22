package my.github.MrxSiN.modeevolved.dex

import java.io.File

import org.luckypray.dexkit.DexKitBridge

import my.github.MrxSiN.modeevolved.core.ContractLog
import my.github.MrxSiN.modeevolved.core.Logger
import my.github.MrxSiN.modeevolved.core.Resolution

/**
 * Finds a hooked class by name, and by what it looks like when the name has gone.
 *
 * Every hook here reaches into a private part of another app, and a quarterly
 * release renames and moves those. A name is tried first, so a matching Android
 * build costs exactly what it did before: one `loadClass`. Only when that fails
 * is the host's dex searched, with [DexKit](https://github.com/LuckyPray/DexKit),
 * for a class in the same package declaring the same methods.
 *
 * A search that finds more than one candidate finds nothing: hooking the wrong
 * class inside Settings is worse than losing the feature, which fails safe and
 * says so in the compatibility check.
 */
class DexClassFinder(
    override val classLoader: ClassLoader,
    private val search: DexSearch,
    private val logger: Logger,
    override val contracts: ContractLog = ContractLog(),
) : ClassFinder {

    override fun find(hooked: HookedClass): Class<*> {
        runCatching { classLoader.loadClass(hooked.knownName) }.getOrNull()?.let {
            contracts.record(hooked.knownName, Resolution.BY_NAME)
            return it
        }

        logger.warn("${hooked.knownName} is not in this build; searching its dex for what replaced it")
        val found = search.use(classLoader) { locate(it, hooked) }
        if (found == null) {
            contracts.record(hooked.knownName, Resolution.MISSING)
            throw ClassNotFoundException("No ${hooked.knownName}, and nothing in ${hooked.inPackage} declaring ${hooked.declaring}")
        }
        contracts.record(hooked.knownName, Resolution.BY_SIGNATURE)
        logger.info("${hooked.knownName} is now ${found.name}")
        return found
    }

    private fun locate(bridge: DexKitBridge, hooked: HookedClass): Class<*>? = bridge
        .findClass {
            searchPackages(hooked.inPackage)
            matcher { hooked.declaring.forEach { method -> addMethod { name(method) } } }
        }
        .singleOrNull()
        ?.getInstance(classLoader)
}

/**
 * A search over the loaded app's own dex files.
 *
 * Opening one parses every dex the app has, which costs a second of CPU and a
 * burst of memory, so nothing opens one until a class has actually gone
 * missing, and each one is closed as soon as its question is answered.
 *
 * DexKit's native library is loaded by absolute path. `System.loadLibrary`
 * searches the library path of the process it runs in, and that process here is
 * Settings, which has never heard of this module.
 *
 * @param nativeLibraryDir this module's own library directory, from its ApplicationInfo.
 */
class DexSearch(private val nativeLibraryDir: String, private val logger: Logger) {

    private var loaded: Boolean? = null

    /** Runs [block] against a search of [classLoader]'s code, or returns null when one cannot be opened. */
    fun <T> use(classLoader: ClassLoader, block: (DexKitBridge) -> T): T? {
        if (!loadLibrary()) return null
        return runCatching { DexKitBridge.create(classLoader, true).use(block) }
            .onFailure { logger.warn("Could not search the dex", it) }
            .getOrNull()
    }

    /** Loads the native library once per process; a failure is remembered so it is not retried. */
    private fun loadLibrary(): Boolean = loaded ?: runCatching {
        System.load(File(nativeLibraryDir, LIBRARY).absolutePath)
        true
    }
        .onFailure { logger.warn("Could not load $LIBRARY; classes will only be found by name", it) }
        .getOrDefault(false)
        .also { loaded = it }

    private companion object {
        const val LIBRARY = "libdexkit.so"
    }
}
