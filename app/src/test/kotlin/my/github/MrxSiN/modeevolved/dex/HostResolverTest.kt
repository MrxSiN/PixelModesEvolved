package my.github.MrxSiN.modeevolved.dex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

import my.github.MrxSiN.modeevolved.compat.ClassApi
import my.github.MrxSiN.modeevolved.compat.ConstructorApi
import my.github.MrxSiN.modeevolved.compat.FeatureContract
import my.github.MrxSiN.modeevolved.compat.FieldApi
import my.github.MrxSiN.modeevolved.compat.Host
import my.github.MrxSiN.modeevolved.compat.MethodApi
import my.github.MrxSiN.modeevolved.compat.Sdk
import my.github.MrxSiN.modeevolved.compat.Scope
import my.github.MrxSiN.modeevolved.core.Contract
import my.github.MrxSiN.modeevolved.core.Feature
import my.github.MrxSiN.modeevolved.core.Resolution

/** How a contract becomes a member of the running host, on plain JVM classes standing in for one. */
class HostResolverTest {

    @Suppress("unused")
    open class Base {
        protected val secret = 1
        open fun hooked() = Unit
        fun inherited() = Unit
    }

    @Suppress("unused")
    class Child(val value: Int) : Base() {
        override fun hooked() = Unit
        fun preferred(flag: Boolean) = flag
        fun older() = Unit
    }

    @Suppress("unused")
    abstract class Abstract {
        abstract fun hooked()
    }

    private val base = ClassApi(Base::class.java.name, Host.SETTINGS)
    private val child = ClassApi(Child::class.java.name, Host.SETTINGS)
    private val abstract = ClassApi(Abstract::class.java.name, Host.SETTINGS)
    private val gone = ClassApi("com.example.Gone", Host.SETTINGS)

    private val apis = HostResolver(NamedClassFinder(javaClass.classLoader!!))

    @Test
    fun `a hooked method must be declared by its owner, with a body`() {
        assertEquals(Child::class.java, apis.method(MethodApi(child, "hooked", scope = Scope.HOOK)).declaringClass)
        // Inherited only: hooking the base's copy would reach every sibling class too.
        assertFalse(apis.has(MethodApi(child, "inherited", scope = Scope.HOOK)))
        assertFalse(apis.has(MethodApi(abstract, "hooked", scope = Scope.HOOK)))
    }

    @Test
    fun `a called member may be declared anywhere above its owner`() {
        assertEquals(Base::class.java, apis.method(MethodApi(child, "inherited")).declaringClass)
        assertEquals(1, apis.field(FieldApi(child, "secret")).get(Child(0)))
        assertEquals(Child::class.java, apis.constructor(ConstructorApi(child, listOf(Sdk.INT))).declaringClass)
    }

    @Test
    fun `parameters are part of the signature`() {
        assertTrue(apis.has(MethodApi(child, "preferred", listOf(Sdk.BOOLEAN))))
        assertFalse(apis.has(MethodApi(child, "preferred", listOf(Sdk.INT))))
    }

    @Test
    fun `a fallback signature stands in for a missing one, and says so`() {
        val api = MethodApi(child, "renamed", fallbacks = listOf(MethodApi(child, "older")))

        assertEquals("older", apis.method(api).name)
        assertEquals(Contract(api.id, Resolution.FALLBACK), apis.log.contracts.single { it.name == api.id })
    }

    @Test
    fun `a missing member is recorded and fails whoever asked`() {
        val missing = MethodApi(gone, "anything")

        val error = assertThrows(MissingHostApiException::class.java) { apis.method(missing) }

        assertSame(missing, error.api)
        assertTrue(Contract(gone.id, Resolution.MISSING) in apis.log.contracts)
        assertTrue(Contract(missing.id, Resolution.MISSING) in apis.log.contracts)
    }

    @Test
    fun `each member is looked up once`() {
        val api = MethodApi(child, "inherited")
        assertSame(apis.method(api), apis.method(api))
    }

    @Test
    fun `a feature is gated on its required members only`() {
        val contract = FeatureContract(
            Feature.MODE_PAGE,
            required = listOf(child, MethodApi(child, "inherited"), MethodApi(child, "missing")),
            optional = listOf(gone),
        )

        assertEquals(listOf(MethodApi(child, "missing")), apis.missing(contract))
        // Optional members are still resolved, so the compatibility page lists them.
        assertTrue(Contract(gone.id, Resolution.MISSING) in apis.log.contracts)
    }

    @Test
    fun `a class is found by name, recorded as such`() {
        assertEquals(Base::class.java, apis.type(base))
        assertEquals(Contract(base.id, Resolution.BY_NAME), apis.log.contracts.single())
    }
}
