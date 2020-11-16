package no.nav.k9.vaktmester

import io.mockk.Called
import io.mockk.clearMocks
import io.mockk.confirmVerified
import io.mockk.verify
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.k9.ApplicationContext
import no.nav.k9.registerApplicationContext
import no.nav.k9.testutils.ApplicationContextExtension
import no.nav.k9.testutils.cleanAndMigrate
import no.nav.k9.vaktmester.river.hentInFlightMedId
import no.nav.k9.vaktmester.river.nyBehovssekvens
import no.nav.k9.vaktmester.river.settJsonFeltTomt
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.skyscreamer.jsonassert.JSONCompare
import org.skyscreamer.jsonassert.JSONCompareMode
import org.skyscreamer.jsonassert.comparator.DefaultComparator
import java.time.ZonedDateTime
import no.nav.k9.testutils.ApplicationContextExtension.Companion.mockSend

@ExtendWith(ApplicationContextExtension::class)
internal class RepubliseringServiceTest(
    private val applicationContext: ApplicationContext
) {

    private val rapid = TestRapid().also {
        it.registerApplicationContext(applicationContext)
    }

    @BeforeEach
    fun reset() {
        applicationContext.dataSource.cleanAndMigrate()
        rapid.reset()
        clearMocks(applicationContext.kafkaProducer)
        applicationContext.kafkaProducer.mockSend()
    }

    @Test
    internal fun `republiserer gamle inflights som ikke er lagret i arkivet`() {
        val id = "01BX5ZZKBKACTAV9WEVGEMMVS0"
        val behovssekvens = lagOgSendBehov(id, ZonedDateTime.now().minusMinutes(31))

        applicationContext.republiseringService.republiserGamleUarkiverteMeldinger()

        verify(exactly = 1) {
            applicationContext.kafkaProducer.send(
                match {
                    JSONCompare.compareJSON(
                        settJsonFeltTomt(it.value(), "system_read_count"),
                        settJsonFeltTomt(behovssekvens, "system_read_count"),
                        DefaultComparator(JSONCompareMode.LENIENT)
                    ).passed()
                }
            )
        }
        confirmVerified(applicationContext.kafkaProducer)
    }

    @Test
    internal fun `sletter meldinger i inflight som er lagret i arkivet`() {
        val id = "01EKW89QKK5YZ0XW2QQYS0TB8D"
        val behovssekvens = lagOgSendBehov(id, ZonedDateTime.now().minusMinutes(32))

        val ds = applicationContext.dataSource
        val inFlights = ds.hentInFlightMedId(id)

        assertThat(inFlights).hasSize(1)

        applicationContext.arkivRepository.arkiverBehovssekvens(id, behovssekvens, "123")

        applicationContext.republiseringService.republiserGamleUarkiverteMeldinger()

        val oppdatertInFlight = ds.hentInFlightMedId(id)
        assertThat(oppdatertInFlight).isEmpty()

        verify {
            listOf(applicationContext.kafkaProducer) wasNot Called
        }

        confirmVerified(applicationContext.kafkaProducer)
    }

    @Test
    internal fun `rører ikke inflights yngre enn 30 min`() {
        val id = "01BX5ZZKBKACTAV9WEVGEMMVS0"
        lagOgSendBehov(id, ZonedDateTime.now().minusMinutes(10))

        val ds = applicationContext.dataSource
        val inFlights = ds.hentInFlightMedId(id)

        assertThat(inFlights).hasSize(1)

        applicationContext.republiseringService.republiserGamleUarkiverteMeldinger()

        val inFlightEtterRydding = ds.hentInFlightMedId(id)

        assertThat(inFlights[0]).isEqualToComparingFieldByFieldRecursively(inFlightEtterRydding[0])
        verify {
            listOf(applicationContext.kafkaProducer) wasNot Called
        }
        confirmVerified(applicationContext.kafkaProducer)
    }

    private fun lagOgSendBehov(id: String, sisteEndret: ZonedDateTime = ZonedDateTime.now()): String {
        val behov = mapOf("behov" to "{}")
        val behovssekvens = nyBehovssekvens(
            id = id,
            behov = behov,
            løsninger = mapOf(),
            sisteEndret
        ).toJson()
        rapid.sendTestMessage(behovssekvens)
        return behovssekvens
    }
}
