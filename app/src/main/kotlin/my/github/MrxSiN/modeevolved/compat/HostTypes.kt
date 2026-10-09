package my.github.MrxSiN.modeevolved.compat

import java.lang.reflect.Modifier

/**
 * The classes of a host, as far as a contract check needs to see them.
 *
 * [ContractAnalyzer] reads a host through this rather than through a class
 * loader, so one check runs against the running host ([ReflectedHostTypes]) and,
 * on a desk, against a recorded description of a Pixel build ([HostDescriptor]).
 */
fun interface HostTypes {

    /** The class by binary name, or null when the host has none. */
    fun find(name: String): HostType?
}

/** One host class: what it extends and the members it declares itself. */
interface HostType {

    val name: String

    /** The class it extends, or null for `java.lang.Object` and interfaces. */
    val superclass: HostType?

    val fields: Set<String>

    val methods: List<MethodShape>

    /** Each constructor's parameters. */
    val constructors: List<List<String>>

    /** This class, then each class above it. */
    fun hierarchy(): Sequence<HostType> = generateSequence(this) { it.superclass }
}

/** A declared method: its name, its parameters' binary names, and whether it has a body. */
data class MethodShape(val name: String, val parameters: List<String>, val isAbstract: Boolean = false)

/**
 * The running host's classes, read by reflection. Each class is read once.
 *
 * @param load the host's class by binary name, or null when it has none.
 */
class ReflectedHostTypes(private val load: (String) -> Class<*>?) : HostTypes {

    private val types = HashMap<Class<*>, HostType>()

    override fun find(name: String): HostType? = load(name)?.let(::typeOf)

    fun typeOf(type: Class<*>): HostType = types.getOrPut(type) { Reflected(type) }

    private inner class Reflected(private val type: Class<*>) : HostType {
        override val name: String get() = type.name
        override val superclass: HostType? get() = type.superclass?.takeIf { it != Any::class.java }?.let(::typeOf)
        override val fields: Set<String> by lazy { type.declaredFields.mapTo(HashSet()) { it.name } }
        override val methods: List<MethodShape> by lazy {
            type.declaredMethods.map { MethodShape(it.name, it.parameterTypes.map(Class<*>::getName), Modifier.isAbstract(it.modifiers)) }
        }
        override val constructors: List<List<String>> by lazy {
            type.declaredConstructors.map { constructor -> constructor.parameterTypes.map(Class<*>::getName) }
        }
    }
}
