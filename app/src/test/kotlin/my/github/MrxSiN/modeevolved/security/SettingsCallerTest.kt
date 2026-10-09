package my.github.MrxSiN.modeevolved.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Who the exported editor and compatibility page answer. */
class SettingsCallerTest {

    @Test
    fun `Settings starting a screen for a result is let in`() {
        assertTrue(SettingsCaller.isSettings(callingPackage = "com.android.settings", launchedFromPackage = null))
    }

    @Test
    fun `Settings sharing its identity is let in`() {
        assertTrue(SettingsCaller.isSettings(callingPackage = null, launchedFromPackage = "com.android.settings"))
    }

    @Test
    fun `a caller the system did not record as Settings is turned away`() {
        // Shell, another app, or anyone who did not share an identity at all.
        assertFalse(SettingsCaller.isSettings(callingPackage = null, launchedFromPackage = null))
        assertFalse(SettingsCaller.isSettings(callingPackage = "com.android.shell", launchedFromPackage = null))
        assertFalse(SettingsCaller.isSettings(callingPackage = "com.example.attacker", launchedFromPackage = "com.example.attacker"))
    }

    @Test
    fun `a name that only looks like Settings is turned away`() {
        assertFalse(SettingsCaller.isSettings(callingPackage = "com.android.settings.evil", launchedFromPackage = null))
        assertFalse(SettingsCaller.isSettings(callingPackage = "com.android.Settings", launchedFromPackage = null))
        assertFalse(SettingsCaller.isSettings(callingPackage = "", launchedFromPackage = " com.android.settings"))
    }
}
