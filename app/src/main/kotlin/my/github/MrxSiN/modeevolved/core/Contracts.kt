package my.github.MrxSiN.modeevolved.core

/** How one private member this module depends on was found. */
enum class Resolution {

    /** Under the name this build was written against. */
    BY_NAME,

    /** Under another name, after a dex search matched what it still looks like. */
    BY_SIGNATURE,

    /** Not at all. Whatever needed it switched itself off. */
    MISSING,
    ;

    companion object {
        fun of(name: String): Resolution? = entries.firstOrNull { it.name == name }
    }
}

/** One class, method or field this module reaches for, and what became of it on this build. */
data class Contract(val name: String, val resolution: Resolution)

/**
 * Every private member this module depends on, in the order it was looked up.
 *
 * A failed hook already costs its own feature and says so. This says which
 * member went, which is what somebody needs in order to fix it after a
 * quarterly release — and it is the same list whether the build matched or not,
 * so it is also the answer to "what exactly does this module rely on".
 *
 * Written while hooks install, on one thread per process.
 */
class ContractLog {

    private val found = linkedMapOf<String, Contract>()

    val contracts: List<Contract> get() = found.values.toList()

    fun record(name: String, resolution: Resolution) {
        found[name] = Contract(name, resolution)
    }

    /**
     * Records and returns what [lookup] finds, or records it missing and rethrows.
     *
     * Rethrowing is the point: a member nobody can find still has to fail its own
     * feature rather than be quietly skipped and crash later somewhere else.
     */
    fun <T : Any> resolve(name: String, lookup: () -> T): T {
        val result = runCatching(lookup)
        record(name, if (result.isSuccess) Resolution.BY_NAME else Resolution.MISSING)
        return result.getOrThrow()
    }
}
