package my.github.MrxSiN.modeevolved.security

import my.github.MrxSiN.modeevolved.compat.Host

/**
 * Who may open this module's exported screens: the Settings app, and nothing else.
 *
 * The editor and the compatibility page are exported only because Settings, a
 * different app, starts them. What they receive is input a person is about to
 * store on a Mode, or a report to show; what the editor returns is what Settings
 * stores. So the caller has to be known, and known from what the system recorded
 * about the launch, never from anything the intent itself claims: an extra, a
 * referrer (`Intent.EXTRA_REFERRER` is set by the sender) or a component name.
 *
 * Two records qualify, both written by the activity manager:
 * - the calling package, which the system sets when the activity was started for a result;
 * - the launching package, which the system reveals only when the launcher opted in
 *   with `ActivityOptions.setShareIdentityEnabled(true)`.
 *
 * Kept free of Android, so the decision itself is tested on the JVM.
 * ARCHITECTURE.md ("Threat model") explains why this is a package check rather
 * than a manifest permission.
 */
object SettingsCaller {

    val PACKAGE: String = Host.SETTINGS.processName

    /**
     * @param callingPackage `Activity.getCallingPackage()`.
     * @param launchedFromPackage `Activity.getLaunchedFromPackage()`.
     */
    fun isSettings(callingPackage: String?, launchedFromPackage: String?): Boolean =
        callingPackage == PACKAGE || launchedFromPackage == PACKAGE
}
