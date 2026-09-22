package my.github.MrxSiN.modeevolved.dex

import my.github.MrxSiN.modeevolved.core.ContractLog

/**
 * One class this module reaches into, described so it can still be found after it moves.
 *
 * @property knownName what it was called in the Android build this was written against.
 * @property inPackage where to look when that name has gone.
 * @property declaring methods it declares, by name. These are the names the hooks use
 * anyway, so a build that renamed them has already taken the feature away; what a search
 * on them finds is a class that was renamed or moved while keeping its shape, which is
 * what a quarterly release usually does.
 */
data class HookedClass(val knownName: String, val inPackage: String, val declaring: List<String>)

/**
 * Where this module gets the private members it depends on.
 *
 * Classes the SDK guarantees are loaded straight from [classLoader]. Everything
 * that lives inside another app goes through here, which both finds it and
 * writes it down: [contracts] is what the compatibility page lists, and what
 * says whether a member was found under its own name, under another one, or not
 * at all.
 */
interface ClassFinder {

    val classLoader: ClassLoader

    /** What was looked for and what became of it, for the compatibility page. */
    val contracts: ContractLog

    /** The class [hooked] names, or the one that replaced it. Throws when neither can be found. */
    fun find(hooked: HookedClass): Class<*>

    /** The class [name], recorded as a contract. For classes that live in the host, not the SDK. */
    fun load(name: String): Class<*> = contracts.resolve(name) { classLoader.loadClass(name) }

    /** Records [name] and returns what [lookup] found, or records it missing and rethrows. */
    fun <T : Any> member(name: String, lookup: () -> T): T = contracts.resolve(name, lookup)
}

/** A finder that only knows names: what the module uses where the dex cannot be searched. */
class NamedClassFinder(
    override val classLoader: ClassLoader,
    override val contracts: ContractLog = ContractLog(),
) : ClassFinder {

    override fun find(hooked: HookedClass): Class<*> = load(hooked.knownName)
}

/** The classes inside the Settings app that this module reaches into. */
object SettingsClasses {

    private const val MODES = "com.android.settings.notification.modes"

    /** The page each Mode opens, whose refreshes this module draws its rows after. */
    val modeFragment = HookedClass(
        knownName = "$MODES.ZenModeFragment",
        inPackage = MODES,
        declaring = listOf("onUpdatedZenModeState", "getMode"),
    )

    /** The base every Modes page shares, including the list, and where its refreshes are declared. */
    val modesFragmentBase = HookedClass(
        knownName = "$MODES.ZenModesFragmentBase",
        inPackage = MODES,
        declaring = listOf("onUpdatedZenModeState", "onStart", "onStop"),
    )

    /** SettingsLib's Mode, the value the page hands around. */
    val zenMode = HookedClass(
        knownName = "com.android.settingslib.notification.modes.ZenMode",
        inPackage = "com.android.settingslib.notification.modes",
        declaring = listOf("setCustomModeConditionId", "getRule"),
    )

    /** The Material 3 Expressive connected button group, which draws the AND and OR choice. */
    val segmentedButton = HookedClass(
        knownName = "com.android.settingslib.widget.SegmentedButtonPreference",
        inPackage = "com.android.settingslib.widget",
        declaring = listOf("setUpButton", "setCheckedIndex"),
    )
}
