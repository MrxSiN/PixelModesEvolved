package my.github.MrxSiN.modeevolved.compat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

import my.github.MrxSiN.modeevolved.action.SystemSetting

import my.github.MrxSiN.modeevolved.core.Feature

/** The contracts themselves: complete, attributable, and within the resolver's policy. */
class FeatureContractsTest {

    /** Every value the two catalogs declare, read the way a person reads the files. */
    private val declared: List<HostApi> = listOf(SettingsApis, SystemServerApis).flatMap { catalog ->
        catalog.javaClass.declaredFields
            .filter { HostApi::class.java.isAssignableFrom(it.type) }
            .map { field -> field.isAccessible = true; field.get(catalog) as HostApi }
    }

    @Test
    fun `every feature declares its contract`() {
        assertEquals(Feature.entries.toSet(), FeatureContracts.all.map { it.feature }.toSet())
        assertEquals(Feature.entries.size, FeatureContracts.all.size)
    }

    @Test
    fun `every hooked feature requires at least one member, or says why it has none`() {
        // Trigger automation stands on public APIs; its one private member only adds to it. So do the
        // system settings changed through Settings.System, Settings.Secure or a public manager.
        val publicSettings = SystemSetting.entries.map { it.feature } - setOf(
            Feature.SETTING_BRIGHTNESS, Feature.SETTING_RINGER_MODE, Feature.SETTING_RING_VOLUME, Feature.SETTING_NOTIFICATION_VOLUME,
            Feature.SETTING_NFC, Feature.SETTING_HOTSPOT,
        )
        val withoutRequirements = FeatureContracts.all.filter { it.required.isEmpty() }.map { it.feature }
        assertEquals(listOf(Feature.TRIGGER_AUTOMATION) + publicSettings, withoutRequirements)
    }

    @Test
    fun `each member belongs to its feature's host`() {
        for (contract in FeatureContracts.all) {
            for (api in contract.all + contract.all.filterIsInstance<MethodApi>().flatMap { it.fallbacks }) {
                assertEquals("${api.id} in ${contract.feature}", contract.host, api.host)
            }
        }
    }

    @Test
    fun `every declared member is some feature's dependency`() {
        val used = FeatureContracts.all.flatMap(FeatureContract::all).toSet()
        val unused = declared.filterNot { it in used }.map { it.id }
        assertTrue("Declared but no feature depends on them: $unused", unused.isEmpty())
    }

    @Test
    fun `every dependency is declared in a catalog`() {
        val undeclared = FeatureContracts.all.flatMap(FeatureContract::all).filterNot { it in declared }.map { it.id }
        assertTrue("Used but declared in no catalog: $undeclared", undeclared.isEmpty())
    }

    @Test
    fun `ids name one member each, per host`() {
        for (host in Host.entries) {
            val duplicates = declared.filter { it.host == host }.distinct().groupBy { it.id }.filterValues { it.size > 1 }.keys
            assertTrue("$host ids shared by different members: $duplicates", duplicates.isEmpty())
        }
    }

    @Test
    fun `a member is required or optional for a feature, never both`() {
        for (contract in FeatureContracts.all) {
            assertTrue(contract.feature.name, contract.required.intersect(contract.optional.toSet()).isEmpty())
        }
    }

    @Test
    fun `a member's classes are part of the same contract`() {
        for (contract in FeatureContracts.all) {
            val classes = contract.all.filterIsInstance<ClassApi>().toSet()
            for (member in contract.all.filterIsInstance<MemberApi>()) {
                assertTrue("${contract.feature} names ${member.id} but not ${member.owner.id}", member.owner in classes)
                val parameters = (member as? MethodApi)?.parameters ?: (member as? ConstructorApi)?.parameters.orEmpty()
                for (type in parameters.filterIsInstance<ClassApi>()) {
                    assertTrue("${contract.feature} names ${member.id} but not its parameter ${type.id}", type in classes)
                }
            }
        }
    }

    @Test
    fun `system_server classes are found by name only`() {
        val structural = declared.filterIsInstance<ClassApi>().filter { it.host == Host.SYSTEM_SERVER && it.locator != Locator.ByName }
        assertTrue("system_server does not search its dex: $structural", structural.isEmpty())
    }

    @Test
    fun `a dex search stays inside the class's own package and names a shape`() {
        for (api in declared.filterIsInstance<ClassApi>()) {
            val locator = api.locator as? Locator.DexSearch ?: continue
            assertEquals(api.id, api.knownName.substringBeforeLast('.'), locator.inPackage)
            assertTrue(api.id, locator.declaring.size >= 2)
        }
    }

    @Test
    fun `an ancestor is searched for from a class in the same host`() {
        for (api in declared.filterIsInstance<ClassApi>()) {
            val locator = api.locator as? Locator.Ancestor ?: continue
            assertEquals(api.id, api.host, locator.of.host)
        }
    }

    @Test
    fun `a fallback is the same method on the same class`() {
        for (api in declared.filterIsInstance<MethodApi>()) {
            for (fallback in api.fallbacks) {
                assertEquals(api.id, api.owner, fallback.owner)
                assertEquals(api.id, api.name, fallback.name)
            }
        }
    }
}
