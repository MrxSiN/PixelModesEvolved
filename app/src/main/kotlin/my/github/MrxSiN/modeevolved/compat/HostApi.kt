package my.github.MrxSiN.modeevolved.compat

/**
 * A process this module runs in, and whose private code it depends on.
 *
 * Settings owns the Mode page and the stored rules; system_server owns the
 * automation. The two fail independently: a Settings build that moved a page
 * class costs the rows on that page, never a trigger.
 */
enum class Host(val processName: String) {
    SETTINGS("com.android.settings"),
    SYSTEM_SERVER("system_server"),
    ;

    companion object {
        fun of(processName: String): Host? = entries.firstOrNull { it.processName == processName }
    }
}

/** A type named in a signature: a host class this module resolves, or one the SDK guarantees. */
sealed interface TypeRef {

    /** The binary name, with `$` before a nested class, or a primitive's keyword. */
    val name: String
}

/** A type the platform SDK or the JVM guarantees, which never needs resolving. */
data class SdkType(override val name: String) : TypeRef

/**
 * One private dependency of a feature: a class, method, field or constructor inside a host.
 *
 * Kept free of Android and of reflection, so the same declaration is what the
 * running host resolves, what a recorded host build is checked against on a
 * desk, and what the compatibility page names.
 */
sealed interface HostApi {

    /** Stable and readable: what the compatibility page, the stored self-test and a fixture report say. */
    val id: String

    val host: Host
}

/**
 * How a class may be found when its known name has gone.
 *
 * This only describes what is a legitimate substitute. Whether and how it is
 * searched for is the resolver's policy (`DexClassFinder`): the name first, a
 * structural search only after that fails, and nothing at all when the search
 * is ambiguous.
 */
sealed interface Locator {

    /** By name only: a class that has no safe structural description. */
    data object ByName : Locator

    /** The one class in [inPackage] that declares every method in [declaring]. */
    data class DexSearch(val inPackage: String, val declaring: List<String>) : Locator

    /**
     * The highest class above [of] that declares [declaring], walking up only
     * while each class still has [sharing]: a base class, found from its subclass.
     */
    data class Ancestor(val of: ClassApi, val declaring: String, val parameters: List<TypeRef>, val sharing: String) : Locator
}

/** A class inside a host. */
data class ClassApi(
    /** The name in the build this module was written against. */
    val knownName: String,
    override val host: Host,
    val locator: Locator = Locator.ByName,
) : HostApi, TypeRef {

    override val name: String get() = knownName

    override val id: String get() = knownName

    val simpleName: String get() = knownName.substringAfterLast('.')
}

/** Where a member has to be declared for the use made of it. */
enum class Scope {

    /** Hooked: declared by the owner itself, with a body to intercept. A superclass's copy would hook every sibling. */
    HOOK,

    /** Called or read: declared by the owner or any class above it. */
    CALL,
}

/** A member of a host class. */
sealed interface MemberApi : HostApi {

    val owner: ClassApi

    override val host: Host get() = owner.host
}

data class MethodApi(
    override val owner: ClassApi,
    val name: String,
    val parameters: List<TypeRef> = emptyList(),
    val scope: Scope = Scope.CALL,
    /**
     * Other signatures that do the same job, tried in order when this one is
     * missing. A caller that uses one gets a method with its own parameter list.
     */
    val fallbacks: List<MethodApi> = emptyList(),
) : MemberApi {

    override val id: String get() = "${owner.simpleName}.$name(${parameters.joinToString { it.name.substringAfterLast('.') }})"
}

data class FieldApi(override val owner: ClassApi, val name: String) : MemberApi {

    override val id: String get() = "${owner.simpleName}.$name"
}

data class ConstructorApi(override val owner: ClassApi, val parameters: List<TypeRef> = emptyList()) : MemberApi {

    override val id: String get() = "${owner.simpleName}(${parameters.joinToString { it.name.substringAfterLast('.') }})"
}

/** Types the SDK guarantees, named once. */
object Sdk {
    val INT = SdkType("int")
    val FLOAT = SdkType("float")
    val EXECUTOR = SdkType("java.util.concurrent.Executor")
    val BOOLEAN = SdkType("boolean")
    val STRING = SdkType("java.lang.String")
    val COLLECTION = SdkType("java.util.Collection")
    val CHAR_SEQUENCE = SdkType("java.lang.CharSequence")
    val CONTEXT = SdkType("android.content.Context")
    val INTENT = SdkType("android.content.Intent")
    val URI = SdkType("android.net.Uri")
    val BUNDLE = SdkType("android.os.Bundle")
    val DRAWABLE = SdkType("android.graphics.drawable.Drawable")
    val MENU = SdkType("android.view.Menu")
    val MENU_INFLATER = SdkType("android.view.MenuInflater")
    val MENU_ITEM = SdkType("android.view.MenuItem")
    val USER_HANDLE = SdkType("android.os.UserHandle")
    val AUTOMATIC_ZEN_RULE = SdkType("android.app.AutomaticZenRule")
}
