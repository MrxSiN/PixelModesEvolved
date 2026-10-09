package my.github.MrxSiN.modeevolved.dex

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

import my.github.MrxSiN.modeevolved.compat.ClassApi
import my.github.MrxSiN.modeevolved.compat.ConstructorApi
import my.github.MrxSiN.modeevolved.compat.FeatureContract
import my.github.MrxSiN.modeevolved.compat.FieldApi
import my.github.MrxSiN.modeevolved.compat.HostApi
import my.github.MrxSiN.modeevolved.compat.MethodApi
import my.github.MrxSiN.modeevolved.compat.Scope
import my.github.MrxSiN.modeevolved.compat.TypeRef
import my.github.MrxSiN.modeevolved.core.ContractLog
import my.github.MrxSiN.modeevolved.core.Resolution

/** A contract member the running host does not have. */
class MissingHostApiException(val api: HostApi) : ReflectiveOperationException("No ${api.id} in ${api.host.processName}")

/**
 * Turns the contracts' declarations into the running host's classes and members.
 *
 * The one place hook code gets a private member from: it names a value from
 * `SettingsApis` or `SystemServerApis`, never a string, and this looks it up by
 * the same rules the offline check ([my.github.MrxSiN.modeevolved.compat.ContractAnalyzer])
 * applies to a recorded build. Every answer is kept, and written to [log] for the
 * compatibility page, so each member is looked up once per process.
 *
 * Used on one thread at a time: the one installing hooks, then the one that runs them.
 */
class HostResolver(private val finder: ClassFinder, val log: ContractLog = ContractLog()) {

    val classLoader: ClassLoader get() = finder.classLoader

    /** Resolved members by contract, or the failure that stopped each. */
    private val resolved = HashMap<HostApi, Result<Any>>()

    fun type(api: ClassApi): Class<*> = resolve(api) as Class<*>

    fun method(api: MethodApi): Method = resolve(api) as Method

    fun field(api: FieldApi): Field = resolve(api) as Field

    fun constructor(api: ConstructorApi): Constructor<*> = resolve(api) as Constructor<*>

    /** Whether [api] resolves here. */
    fun has(api: HostApi): Boolean = runCatching { resolve(api) }.isSuccess

    /**
     * Resolves everything [contract] names, so the compatibility page lists all of it,
     * and returns the required members this host is missing. Empty means the feature can install.
     */
    fun missing(contract: FeatureContract): List<HostApi> {
        contract.optional.forEach(::has)
        return contract.required.filterNot(::has)
    }

    private fun resolve(api: HostApi): Any = resolved.getOrPut(api) {
        runCatching {
            when (api) {
                is ClassApi -> locate(api)
                is MethodApi -> findMethod(api)
                is FieldApi -> record(api, walk(type(api.owner)) { it.getDeclaredField(api.name) })
                is ConstructorApi -> record(api, type(api.owner).getDeclaredConstructor(*parameters(api.parameters)))
            }
        }.onFailure { log.record(api.id, Resolution.MISSING) }
    }.getOrElse { throw if (it is MissingHostApiException && it.api == api) it else MissingHostApiException(api).apply { initCause(it) } }

    private fun locate(api: ClassApi): Class<*> {
        val located = finder.locate(api) ?: throw MissingHostApiException(api)
        log.record(api.id, located.resolution)
        return located.type
    }

    private fun findMethod(api: MethodApi): Method {
        lookup(api)?.let { return record(api, it) }
        for (fallback in api.fallbacks) {
            val method = runCatching { lookup(fallback) }.getOrNull() ?: continue
            log.record(api.id, Resolution.FALLBACK)
            return method.accessible()
        }
        throw MissingHostApiException(api)
    }

    /** [api] by the rule its scope sets, or null when the owner has no such method. */
    private fun lookup(api: MethodApi): Method? {
        val owner = type(api.owner)
        val parameters = parameters(api.parameters)
        return when (api.scope) {
            Scope.HOOK -> runCatching { owner.getDeclaredMethod(api.name, *parameters) }.getOrNull()?.takeUnless { Modifier.isAbstract(it.modifiers) }
            Scope.CALL -> runCatching { walk(owner) { it.getDeclaredMethod(api.name, *parameters) } }.getOrNull()
        }
    }

    private fun <T : java.lang.reflect.AccessibleObject> record(api: HostApi, member: T): T {
        log.record(api.id, Resolution.BY_NAME)
        return member.accessible()
    }

    private fun <T : java.lang.reflect.AccessibleObject> T.accessible(): T = apply { isAccessible = true }

    /** The first of [owner] and the classes above it for which [find] succeeds. */
    private fun <T> walk(owner: Class<*>, find: (Class<*>) -> T): T {
        var type: Class<*>? = owner
        var failure: Throwable? = null
        while (type != null) {
            val result = runCatching { find(type) }
            if (result.isSuccess) return result.getOrThrow()
            failure = failure ?: result.exceptionOrNull()
            type = type.superclass
        }
        throw checkNotNull(failure)
    }

    private fun parameters(types: List<TypeRef>): Array<Class<*>> = types.map { ref ->
        when (ref) {
            is ClassApi -> type(ref)
            else -> PRIMITIVES[ref.name] ?: classLoader.loadClass(ref.name)
        }
    }.toTypedArray()

    private companion object {
        val PRIMITIVES: Map<String, Class<*>> = listOf(
            Int::class.javaPrimitiveType, Long::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
            Float::class.javaPrimitiveType, Double::class.javaPrimitiveType, Byte::class.javaPrimitiveType,
            Short::class.javaPrimitiveType, Char::class.javaPrimitiveType,
        ).filterNotNull().associateBy(Class<*>::getName)
    }
}
