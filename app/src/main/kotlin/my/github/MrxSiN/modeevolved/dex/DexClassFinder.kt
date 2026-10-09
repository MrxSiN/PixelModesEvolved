package my.github.MrxSiN.modeevolved.dex

import java.io.File

import org.luckypray.dexkit.DexKitBridge

import my.github.MrxSiN.modeevolved.compat.ClassApi
import my.github.MrxSiN.modeevolved.compat.Locator
import my.github.MrxSiN.modeevolved.core.Logger
import my.github.MrxSiN.modeevolved.core.Resolution

/**
 * Finds a hooked class by name, and by what it looks like when the name has gone.
 *
 * Every hook here reaches into a private part of another app, and a quarterly
 * release renames and moves those. A name is tried first, so a matching Android
 * build costs exactly what it did before: one `loadClass`. Only when that fails,
 * and only for a class whose contract describes a legitimate substitute, is a
 * structural search made: the host's dex, with [DexKit](https://github.com/LuckyPray/DexKit),
 * restricted to the class's own package and method shape, or the class's line
 * of ancestors.
 *
 * A search that finds more than one candidate finds nothing: hooking the wrong
 * class inside Settings is worse than losing the feature, which fails safe and
 * says so in the compatibility check.
 */
class DexClassFinder(
    override val classLoader: ClassLoader,
    private val search: DexSearch,
    private val logger: Logger,
) : ClassFinder {

    override fun locate(api: ClassApi): Located? {
        classLoader.loadOrNull(api.knownName)?.let { return Located(it, Resolution.BY_NAME) }
        if (api.locator == Locator.ByName) return null

        logger.warn("${api.knownName} is not in this build; searching for what replaced it")
        val candidates = candidates(api).orEmpty()
        val found = candidates.singleOrNull()?.let(classLoader::loadOrNull)
        if (found == null) {
            logger.warn("No single replacement for ${api.knownName}: found ${candidates.ifEmpty { listOf("nothing") }}")
            return null
        }
        logger.info("${api.knownName} is now ${found.name}")
        return Located(found, Resolution.BY_SIGNATURE)
    }

    override fun candidates(api: ClassApi): List<String>? = when (val locator = api.locator) {
        Locator.ByName -> null
        is Locator.DexSearch -> search.use(classLoader) { bridge ->
            bridge.findClass {
                searchPackages(locator.inPackage)
                matcher { locator.declaring.forEach { method -> addMethod { name(method) } } }
            }.map { it.name }
        }
        is Locator.Ancestor -> {
            val start = locate(locator.of)?.type
            val parameters = locator.parameters.map { classLoader.loadOrNull(it.name) }
            if (start == null || parameters.any { it == null }) emptyList()
            else listOfNotNull(ancestorOf(start, locator, parameters.filterNotNull().toTypedArray())?.name)
        }
    }
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
