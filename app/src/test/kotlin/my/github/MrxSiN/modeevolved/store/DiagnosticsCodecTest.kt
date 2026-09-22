package my.github.MrxSiN.modeevolved.store

import org.junit.Assert.assertEquals
import org.junit.Test

import my.github.MrxSiN.modeevolved.core.Contract
import my.github.MrxSiN.modeevolved.core.ContractLog
import my.github.MrxSiN.modeevolved.core.Feature
import my.github.MrxSiN.modeevolved.core.FeatureHealth
import my.github.MrxSiN.modeevolved.core.Resolution
import my.github.MrxSiN.modeevolved.diagnostics.DiagnosticsReport
import my.github.MrxSiN.modeevolved.diagnostics.DiagnosticsReportCodec

class DiagnosticsCodecTest {

    private val found = Diagnostics(
        features = listOf(
            FeatureHealth(Feature.MODE_PAGE, ok = true),
            FeatureHealth(Feature.CALENDAR_FILTER, ok = false, detail = "No such method meetsAttendee"),
        ),
        contracts = listOf(
            Contract("Preference.setTitle(CharSequence)", Resolution.BY_NAME),
            Contract("ZenModeFragment", Resolution.BY_SIGNATURE),
            Contract("CalendarTracker.meetsAttendee(EventInfo, int, String)", Resolution.MISSING),
        ),
        framework = "Vector 2.2",
        apiVersion = 102,
    )

    @Test
    fun `what one process found round trips`() {
        assertEquals(found, DiagnosticsCodec.decode(DiagnosticsCodec.encode(found)))
    }

    @Test
    fun `anything another build knows and this one does not is dropped`() {
        val text = """
            {"features":[{"feature":"modePage","ok":true},{"feature":"quickSettings","ok":false}],
             "contracts":[{"name":"A","resolution":"BY_NAME"},{"name":"B","resolution":"TELEPATHY"}]}
        """.trimIndent()
        val decoded = DiagnosticsCodec.decode(text)
        assertEquals(listOf(FeatureHealth(Feature.MODE_PAGE, ok = true)), decoded.features)
        assertEquals(listOf(Contract("A", Resolution.BY_NAME)), decoded.contracts)
    }

    @Test
    fun `nothing stored reads as nothing checked, not as everything failed`() {
        assertEquals(Diagnostics(), DiagnosticsCodec.decode(null))
        assertEquals(Diagnostics(), DiagnosticsCodec.decode(""))
        assertEquals(Diagnostics(), DiagnosticsCodec.decode("not json"))
    }

    @Test
    fun `the two processes add up without either losing its half`() {
        val settings = Diagnostics(
            features = listOf(FeatureHealth(Feature.MODE_PAGE, ok = true)),
            contracts = listOf(Contract("Preference.getKey()", Resolution.BY_NAME)),
            framework = "Vector 2.2",
            apiVersion = 102,
        )
        val system = Diagnostics(
            features = listOf(FeatureHealth(Feature.TRIGGER_AUTOMATION, ok = true)),
            contracts = listOf(Contract("NotificationManagerService.onBootPhase(int)", Resolution.BY_NAME)),
        )
        val both = settings + system
        assertEquals(2, both.features.size)
        assertEquals(2, both.contracts.size)
        assertEquals("Vector 2.2", both.framework)
        assertEquals(102, both.apiVersion)
    }

    @Test
    fun `the report crossing to this module's own app keeps every field`() {
        val report = DiagnosticsReport(
            moduleVersion = "0.0.1",
            moduleActive = true,
            androidRelease = "17",
            sdkInt = 37,
            buildId = "CP3A.260905.009",
            settingsVersion = "35",
            frameworkName = "Vector",
            frameworkVersion = "2.2",
            apiVersion = 102,
            features = found.features,
            contracts = found.contracts,
        )
        assertEquals(report, DiagnosticsReportCodec.decode(DiagnosticsReportCodec.encode(report)))
    }

    @Test
    fun `a report counts what resolved, what moved and what went`() {
        val report = DiagnosticsReport(features = found.features, contracts = found.contracts)
        assertEquals(2, report.resolved)
        assertEquals(1, report.bySignature)
        assertEquals(1, report.failed)
        assertEquals(1, report.featuresFailed)
    }

    @Test
    fun `a member that cannot be found is recorded missing and still fails its caller`() {
        val log = ContractLog()
        runCatching { log.resolve("Gone.method()") { error("no such method") } }
        log.resolve("Here.method()") { "found" }

        assertEquals(
            listOf(Contract("Gone.method()", Resolution.MISSING), Contract("Here.method()", Resolution.BY_NAME)),
            log.contracts,
        )
    }
}
