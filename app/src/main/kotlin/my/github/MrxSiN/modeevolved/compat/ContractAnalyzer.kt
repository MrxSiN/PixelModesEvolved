package my.github.MrxSiN.modeevolved.compat

import my.github.MrxSiN.modeevolved.core.Feature

/**
 * Checks every feature's contract against a description of a host build.
 *
 * Member lookups follow the resolver's rules exactly (`HostResolver`): a hooked
 * method must be declared, with a body, on its owner; anything called or read may
 * be declared on the owner or any class above it; a fallback signature counts when
 * the preferred one is gone.
 */
class ContractAnalyzer(private val types: HostTypes) {

    fun analyze(host: Host): CompatibilityReport {
        val contracts = FeatureContracts.of(host)
        val results = contracts.flatMap(FeatureContract::all).distinct().associateWith(::check)
        return CompatibilityReport(host, contracts, results)
    }

    fun check(api: HostApi): ApiOutcome = when (api) {
        is ClassApi -> if (types.find(api.knownName) != null) ApiOutcome.RESOLVED else ApiOutcome.MISSING_CLASS
        is MethodApi -> checkMethod(api)
        is FieldApi -> member(api.owner) { type -> type.hierarchy().any { api.name in it.fields } }
        is ConstructorApi -> member(api.owner) { type -> names(api.parameters) in type.constructors }
    }

    private fun checkMethod(api: MethodApi): ApiOutcome {
        val own = member(api.owner) { type -> declares(type, api) }
        if (own != ApiOutcome.MISSING_MEMBER) return own
        return if (api.fallbacks.any { member(it.owner) { type -> declares(type, it) } == ApiOutcome.RESOLVED }) ApiOutcome.FALLBACK else own
    }

    private fun declares(type: HostType, api: MethodApi): Boolean {
        val parameters = names(api.parameters)
        val candidates = when (api.scope) {
            Scope.HOOK -> sequenceOf(type)
            Scope.CALL -> type.hierarchy()
        }
        return candidates.any { owner ->
            owner.methods.any { it.name == api.name && it.parameters == parameters && (api.scope == Scope.CALL || !it.isAbstract) }
        }
    }

    private fun member(owner: ClassApi, has: (HostType) -> Boolean): ApiOutcome {
        val type = types.find(owner.knownName) ?: return ApiOutcome.MISSING_CLASS
        return if (has(type)) ApiOutcome.RESOLVED else ApiOutcome.MISSING_MEMBER
    }

    private fun names(types: List<TypeRef>) = types.map(TypeRef::name)
}

enum class ApiOutcome(val isMet: Boolean, val label: String) {
    RESOLVED(true, "resolved"),
    FALLBACK(true, "met by a fallback signature"),
    MISSING_MEMBER(false, "member missing"),
    MISSING_CLASS(false, "class missing"),
}

/** What one host build means for each of its features. */
class CompatibilityReport(
    val host: Host,
    val contracts: List<FeatureContract>,
    val results: Map<HostApi, ApiOutcome>,
) {

    /** Required members each feature is missing. A feature with none is compatible. */
    val broken: Map<Feature, List<HostApi>> =
        contracts.associate { contract -> contract.feature to contract.required.filterNot { results.getValue(it).isMet } }
            .filterValues { it.isNotEmpty() }

    fun isCompatible(feature: Feature): Boolean = feature !in broken

    /** Optional members each feature is missing, which cost only what they add. */
    val degraded: Map<Feature, List<HostApi>> =
        contracts.associate { contract -> contract.feature to contract.optional.filterNot { results.getValue(it).isMet } }
            .filterValues { it.isNotEmpty() }

    /** One line per broken dependency: which feature, in which host, needs what, and what happened to it. */
    fun describeBroken(): String = broken.entries.joinToString("\n") { (feature, missing) ->
        missing.joinToString("\n") { "${feature.name} [${host.processName}] requires ${it.id}: ${results.getValue(it).label}" }
    }
}
