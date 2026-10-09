package my.github.MrxSiN.modeevolved.compat

/**
 * The minimum of a host build that the contracts need, written down as text.
 *
 * For every class a contract names, and each class above it: what it extends, and
 * only the members whose names some contract uses. Constructors only for classes a
 * constructor contract names. Then the classes the build does not have, and what
 * each structural [Locator] finds in it. Names only, never code, so a description
 * of a Google build can live beside the tests without shipping any of it.
 *
 * ```
 * build CP3A.260905.009
 * host com.android.settings
 * class com.android.settings.notification.modes.ZenModeFragment extends com.android.settings.notification.modes.ZenModeFragmentBase
 *   method onResume()
 * absent com.android.settings.SomethingRemoved
 * locate com.android.settings.notification.modes.ZenModeFragment com.android.settings.notification.modes.ZenModeFragment
 * ```
 */
object HostDescriptor {

    /** What a description of [host] has to cover: the classes, member names and constructor owners its contracts name. */
    class Scope(val classes: Set<String>, val members: Set<String>, val constructorOwners: Set<String>, val structural: List<ClassApi>) {
        companion object {
            fun of(apis: Collection<HostApi>): Scope {
                val classes = sortedSetOf<String>()
                val members = sortedSetOf<String>()
                val constructorOwners = sortedSetOf<String>()
                val structural = mutableListOf<ClassApi>()
                fun visit(api: HostApi) {
                    when (api) {
                        is ClassApi -> {
                            classes += api.knownName
                            if (api.locator != Locator.ByName) structural += api
                        }
                        is MethodApi -> {
                            classes += api.owner.knownName
                            members += api.name
                            api.parameters.filterIsInstance<ClassApi>().forEach(::visit)
                            api.fallbacks.forEach(::visit)
                        }
                        is FieldApi -> {
                            classes += api.owner.knownName
                            members += api.name
                        }
                        is ConstructorApi -> {
                            classes += api.owner.knownName
                            constructorOwners += api.owner.knownName
                        }
                    }
                }
                apis.forEach(::visit)
                return Scope(classes, members, constructorOwners, structural.distinct())
            }
        }
    }

    /**
     * Describes [host] as [types] sees it, sorted so two captures of one build are the same text.
     *
     * @param locate what each structural locator in [scope] finds, by known name; empty when the capture could not search.
     */
    fun write(build: String, host: Host, types: HostTypes, scope: Scope, locate: Map<String, List<String>>): String {
        val described = sortedMapOf<String, HostType>()
        val absent = sortedSetOf<String>()
        for (name in scope.classes) {
            val type = types.find(name)
            if (type == null) absent += name else type.hierarchy().forEach { described.putIfAbsent(it.name, it) }
        }
        return buildString {
            appendLine(HEADER)
            appendLine("$BUILD $build")
            appendLine("$HOST ${host.processName}")
            for (type in described.values) {
                appendLine("$CLASS ${type.name}" + type.superclass?.let { " $EXTENDS ${it.name}" }.orEmpty())
                // A class the host cannot fully read is kept with what it could.
                runCatching { type.fields.filter { it in scope.members }.sorted() }.getOrDefault(emptyList())
                    .forEach { appendLine("  $FIELD $it") }
                runCatching { type.methods.filter { it.name in scope.members }.map(::describe).sorted() }.getOrDefault(emptyList())
                    .forEach { appendLine("  $METHOD $it") }
                if (type.name in scope.constructorOwners) {
                    runCatching { type.constructors.map { it.joinToString(",", "(", ")") }.sorted() }.getOrDefault(emptyList())
                        .forEach { appendLine("  $CONSTRUCTOR $it") }
                }
            }
            absent.forEach { appendLine("$ABSENT $it") }
            for ((name, found) in locate.toSortedMap()) appendLine(listOf(LOCATE, name, *found.sorted().toTypedArray()).joinToString(" "))
        }
    }

    /** A written description, read back. */
    fun read(text: String): DescribedHost {
        var build = ""
        var host: Host? = null
        val classes = mutableListOf<ClassRecord>()
        val absent = mutableSetOf<String>()
        val located = mutableMapOf<String, List<String>>()

        for (line in text.lineSequence().map(String::trim).filter { it.isNotEmpty() && !it.startsWith("#") }) {
            val keyword = line.substringBefore(' ')
            val rest = line.substringAfter(' ')
            fun current() = requireNotNull(classes.lastOrNull()) { "A member outside a class: $line" }
            when (keyword) {
                BUILD -> build = rest
                HOST -> host = Host.of(rest)
                CLASS -> classes += ClassRecord(rest.substringBefore(' '), rest.substringAfter(" $EXTENDS ", "").ifEmpty { null })
                FIELD -> current().fields += rest
                METHOD -> current().methods += method(rest)
                CONSTRUCTOR -> current().constructors += parameters(rest)
                ABSENT -> absent += rest
                LOCATE -> rest.split(' ').let { located[it.first()] = it.drop(1) }
                else -> error("Unknown line in a host description: $line")
            }
        }
        return DescribedHost(build, requireNotNull(host) { "A host description names no host" }, classes, absent, located)
    }

    private fun describe(method: MethodShape): String =
        method.parameters.joinToString(",", "${method.name}(", ")") + if (method.isAbstract) " $ABSTRACT" else ""

    private fun method(text: String) = MethodShape(
        text.substringBefore('('),
        parameters(text.substringAfter('(').substringBefore(')')),
        isAbstract = text.endsWith(" $ABSTRACT"),
    )

    private fun parameters(text: String): List<String> = text.removePrefix("(").removeSuffix(")").split(',').filter(String::isNotEmpty)

    internal class ClassRecord(val name: String, val superName: String?) {
        val fields = mutableSetOf<String>()
        val methods = mutableListOf<MethodShape>()
        val constructors = mutableListOf<List<String>>()
    }

    /**
     * One recorded host build. Asked for a class it recorded neither way, it fails
     * rather than answer: the build was captured before a contract named that class,
     * so it has to be captured again.
     */
    class DescribedHost internal constructor(
        val build: String,
        val host: Host,
        records: List<ClassRecord>,
        private val absent: Set<String>,
        /** What each structural locator found in this build, by the class's known name. */
        val located: Map<String, List<String>>,
    ) : HostTypes {

        private val classes: Map<String, HostType> = records.associate { it.name to Described(it) }

        override fun find(name: String): HostType? {
            classes[name]?.let { return it }
            check(name in absent) { "$build does not record $name; capture ${host.processName} on $build again (scripts/capture-host-fixture.sh)" }
            return null
        }

        private inner class Described(private val record: ClassRecord) : HostType {
            override val name: String get() = record.name
            override val superclass: HostType? get() = record.superName?.let { classes[it] }
            override val fields: Set<String> get() = record.fields
            override val methods: List<MethodShape> get() = record.methods
            override val constructors: List<List<String>> get() = record.constructors
        }
    }

    const val HEADER = "# Generated by scripts/capture-host-fixture.sh: class and member names only, no host code."
    private const val BUILD = "build"
    private const val HOST = "host"
    private const val CLASS = "class"
    private const val EXTENDS = "extends"
    private const val FIELD = "field"
    private const val METHOD = "method"
    private const val CONSTRUCTOR = "constructor"
    private const val ABSTRACT = "abstract"
    private const val ABSENT = "absent"
    private const val LOCATE = "locate"
}
