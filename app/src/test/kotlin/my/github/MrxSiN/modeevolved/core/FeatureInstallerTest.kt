package my.github.MrxSiN.modeevolved.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Each feature installs only when its host has what it requires, and fails alone. */
class FeatureInstallerTest {

    private val recorded = mutableListOf<FeatureHealth>()

    @Test
    fun `a feature missing a required member is not installed, and says which`() {
        var installed = false
        val installer = FeatureInstaller(SilentLogger, recorded::add) { listOf("ZenModeFragment.getMode()") }

        installer.install(Feature.MODE_PAGE) { installed = true }

        assertFalse(installed)
        assertEquals(listOf(FeatureHealth(Feature.MODE_PAGE, ok = false, detail = "Missing ZenModeFragment.getMode()")), recorded)
    }

    @Test
    fun `a feature that throws costs itself and not the next`() {
        val installer = FeatureInstaller(SilentLogger, recorded::add)

        installer.install(Feature.MODE_PAGE) { error("moved") }
        installer.install(Feature.MODES_LIST) { }

        assertEquals(
            listOf(FeatureHealth(Feature.MODE_PAGE, ok = false, detail = "moved"), FeatureHealth(Feature.MODES_LIST, ok = true)),
            recorded,
        )
    }
}
