package my.github.MrxSiN.modeevolved.action

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a broadcast sent by the system process is allowed to be called. */
class IntentActionsTest {

    @Test
    fun `an app's own dotted action name is sendable`() {
        assertTrue(IntentActions.isSendable("com.example.modes.CHANGED"))
        assertTrue(IntentActions.isSendable("my.github.MrxSiN.modeevolved.MODE_CHANGED"))
        assertTrue(IntentActions.isSendable("net.dinglisch.android.tasker.ACTION_TASK"))
    }

    @Test
    fun `platform names are refused, whoever configured them`() {
        assertTrue(IntentActions.isWellFormed("android.intent.action.BOOT_COMPLETED"))
        assertFalse(IntentActions.isSendable("android.intent.action.BOOT_COMPLETED"))
        assertFalse(IntentActions.isSendable("com.android.settings.SOMETHING"))
        assertFalse(IntentActions.isSendable("com.google.android.gms.THING"))
    }

    @Test
    fun `a name that no intent filter could hold is refused`() {
        assertFalse(IntentActions.isSendable(""))
        assertFalse(IntentActions.isSendable("NOTDOTTED"))
        assertFalse(IntentActions.isSendable("com.example spaced.ACTION"))
        assertFalse(IntentActions.isSendable("com.example..ACTION"))
        assertFalse(IntentActions.isSendable("9com.example.ACTION"))
        assertFalse(IntentActions.isSendable("com.example." + "x".repeat(IntentActions.MAX_LENGTH)))
    }
}
