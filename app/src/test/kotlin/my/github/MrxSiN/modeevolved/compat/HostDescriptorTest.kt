package my.github.MrxSiN.modeevolved.compat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Writing a host down as names, reading it back, and checking contracts against it. */
class HostDescriptorTest {

    @Suppress("unused")
    open class Base {
        private val kept = 0
        private val dropped = 0
        open fun hooked() = Unit
        fun called(value: Int) = value
        fun unrelated() = Unit
    }

    @Suppress("unused")
    class Child(value: String) : Base() {
        override fun hooked() = Unit
    }

    @Suppress("unused")
    abstract class Abstract {
        abstract fun hooked()
    }

    private val child = ClassApi(Child::class.java.name, Host.SETTINGS)
    private val abstract = ClassApi(Abstract::class.java.name, Host.SETTINGS)
    private val gone = ClassApi("com.example.Gone", Host.SETTINGS, Locator.DexSearch("com.example", listOf("a", "b")))

    private val apis = listOf(
        child,
        abstract,
        gone,
        MethodApi(child, "hooked", scope = Scope.HOOK),
        MethodApi(child, "called", listOf(Sdk.INT)),
        MethodApi(abstract, "hooked", scope = Scope.HOOK),
        FieldApi(child, "kept"),
        ConstructorApi(child, listOf(Sdk.STRING)),
    )

    private val reflected = ReflectedHostTypes { name -> runCatching { Class.forName(name) }.getOrNull() }

    private fun described(): HostDescriptor.DescribedHost {
        val text = HostDescriptor.write("TEST.1", Host.SETTINGS, reflected, HostDescriptor.Scope.of(apis), mapOf(gone.knownName to listOf("com.example.A", "com.example.B")))
        return HostDescriptor.read(text)
    }

    @Test
    fun `only the members a contract names are written, with the classes above`() {
        val text = HostDescriptor.write("TEST.1", Host.SETTINGS, reflected, HostDescriptor.Scope.of(apis), emptyMap())

        assertTrue(text.contains("class ${Child::class.java.name} extends ${Base::class.java.name}"))
        assertTrue(text.contains("  field kept"))
        assertTrue(text.contains("  method called(int)"))
        assertTrue(text.contains("  method hooked() abstract"))
        assertTrue(text.contains("  constructor (java.lang.String)"))
        assertTrue(text.contains("absent com.example.Gone"))
        assertFalse(text.contains("dropped"))
        assertFalse(text.contains("unrelated"))
    }

    @Test
    fun `a description reads back as the host it describes`() {
        val host = described()

        assertEquals("TEST.1", host.build)
        assertEquals(Host.SETTINGS, host.host)
        assertEquals(Base::class.java.name, host.find(child.knownName)?.superclass?.name)
        assertNull(host.find(gone.knownName))
        assertEquals(listOf("com.example.A", "com.example.B"), host.located[gone.knownName])
    }

    @Test
    fun `a class the description never recorded asks for a new capture`() {
        val error = assertThrows(IllegalStateException::class.java) { described().find("com.example.Unrecorded") }
        assertTrue(error.message.orEmpty().contains("capture-host-fixture.sh"))
    }

    @Test
    fun `the analyzer applies the resolver's rules to a described host`() {
        val analyzer = ContractAnalyzer(described())

        assertEquals(ApiOutcome.RESOLVED, analyzer.check(MethodApi(child, "hooked", scope = Scope.HOOK)))
        assertEquals(ApiOutcome.RESOLVED, analyzer.check(MethodApi(child, "called", listOf(Sdk.INT))))
        assertEquals(ApiOutcome.MISSING_MEMBER, analyzer.check(MethodApi(child, "called", scope = Scope.HOOK)))
        assertEquals(ApiOutcome.MISSING_MEMBER, analyzer.check(MethodApi(child, "called", listOf(Sdk.STRING))))
        assertEquals(ApiOutcome.MISSING_MEMBER, analyzer.check(MethodApi(abstract, "hooked", scope = Scope.HOOK)))
        assertEquals(ApiOutcome.RESOLVED, analyzer.check(FieldApi(child, "kept")))
        assertEquals(ApiOutcome.RESOLVED, analyzer.check(ConstructorApi(child, listOf(Sdk.STRING))))
        assertEquals(ApiOutcome.MISSING_MEMBER, analyzer.check(ConstructorApi(child)))
        assertEquals(ApiOutcome.MISSING_CLASS, analyzer.check(MethodApi(gone, "a")))
        assertEquals(
            ApiOutcome.FALLBACK,
            analyzer.check(MethodApi(child, "called", listOf(Sdk.STRING), fallbacks = listOf(MethodApi(child, "called", listOf(Sdk.INT))))),
        )
    }
}
