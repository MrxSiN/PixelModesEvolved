package my.github.MrxSiN.modeevolved.compat

/**
 * The Pixel builds, as `Build.ID` names them, this module was checked on by hand.
 *
 * Each has a description of both hosts under `src/test/resources/hosts/<build>/`, and
 * `HostCompatibilityTest` checks every feature's contract against it, so a change here
 * that breaks a verified build fails CI before it reaches a phone. Nothing is refused on
 * any other build: there each feature is resolved on its own and the compatibility page
 * says what it found.
 */
object VerifiedBuilds {

    val builds: Set<String> = setOf(
        "CP3A.261005.005",
    )

    fun isVerified(build: String): Boolean = build in builds
}
