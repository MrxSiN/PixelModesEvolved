package my.github.MrxSiN.modeevolved.compat

import java.io.File

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Every feature's contract, checked against every recorded Pixel build.
 *
 * The descriptions under `src/test/resources/hosts/<build>/` are what
 * `scripts/capture-host-fixture.sh` read off a phone on that build: names only.
 * A contract a change gets wrong fails here, naming the feature, the host and the
 * member, before the build ever reaches a phone.
 */
class HostCompatibilityTest {

    private val builds: List<File> = checkNotNull(javaClass.classLoader?.getResource("hosts")) { "No recorded builds" }
        .let { File(it.toURI()) }
        .listFiles(File::isDirectory).orEmpty()
        .sortedBy(File::getName)

    private fun hostsOf(build: File): List<HostDescriptor.DescribedHost> = Host.entries.map { host ->
        val file = File(build, fileName(host))
        check(file.isFile) { "${build.name} has no description of ${host.processName}: $file" }
        HostDescriptor.read(file.readText()).also { check(it.build == build.name && it.host == host) { "$file describes ${it.build} ${it.host}" } }
    }

    @Test
    fun `every verified build is recorded for both hosts`() {
        val recorded = builds.map(File::getName).toSet()
        val unrecorded = VerifiedBuilds.builds - recorded
        assertTrue("Verified but never captured: $unrecorded (scripts/capture-host-fixture.sh)", unrecorded.isEmpty())
        builds.forEach(::hostsOf)
    }

    @Test
    fun `every feature's required members are there on every recorded build`() {
        val broken = builds.flatMap(::hostsOf).mapNotNull { described ->
            val report = ContractAnalyzer(described).analyze(described.host)
            report.describeBroken().takeIf { it.isNotEmpty() }?.let { "${described.build}:\n$it" }
        }
        if (broken.isNotEmpty()) fail("Features that would not install:\n" + broken.joinToString("\n"))
    }

    @Test
    fun `every structural fallback would find exactly the class it stands in for`() {
        val problems = builds.flatMap(::hostsOf).flatMap { described ->
            val structural = HostDescriptor.Scope.of(FeatureContracts.apisOf(described.host)).structural
            structural.mapNotNull { api ->
                val found = described.located[api.knownName]
                    ?: return@mapNotNull "${described.build} [${described.host.processName}] did not search for ${api.id}; capture again"
                "${described.build} [${described.host.processName}] ${api.id} by ${api.locator} finds $found".takeUnless { found == listOf(api.knownName) }
            }
        }
        if (problems.isNotEmpty()) fail("Structural fallbacks that would be ambiguous or wrong:\n" + problems.joinToString("\n"))
    }

    companion object {
        /** The file each host's description is kept in, beside the others of its build. */
        fun fileName(host: Host): String = "${host.processName}.txt"
    }
}
