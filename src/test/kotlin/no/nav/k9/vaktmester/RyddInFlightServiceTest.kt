package no.nav.k9.vaktmester

import io.mockk.confirmVerified
import io.mockk.verify
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.k9.ApplicationContext
import no.nav.k9.registerApplicationContext
import no.nav.k9.testutils.ApplicationContextExtension
import no.nav.k9.testutils.cleanAndMigrate
import no.nav.k9.vaktmester.river.hentInFlightMedId
import no.nav.k9.vaktmester.river.nyBehovssekvens
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ApplicationContextExtension::class)
internal class RyddInFlightServiceTest(
    private val applicationContext: ApplicationContext
) {
    private var rapid = TestRapid().also {
        it.registerApplicationContext(applicationContext)
    }

    @BeforeEach
    fun reset() {
        applicationContext.dataSource.cleanAndMigrate()
        rapid.reset()
    }

    @Test
    internal fun `sletter inflights som har blitt lagret i arkivet`() {
        val id = "01EKW89QKK5YZ0XW2QQYS0TB8D"
        sendTestbehovMedId(id)

        val ds = applicationContext.dataSource
        val inFlights = ds.hentInFlightMedId(id)

        assertThat(inFlights).hasSize(1)

        // TODO: insert i arkiv

        applicationContext.ryddInFlightService.rydd()

        val oppdatertInFlight = ds.hentInFlightMedId(id)
        assertThat(oppdatertInFlight).isEmpty()

        // TODO: Assert ingenting republisert
    }

    @Test
    internal fun `sletter og republiserer inflights som ikke er lagret i arkivet`() {
        val id = "01BX5ZZKBKACTAV9WEVGEMMVS0"
        sendTestbehovMedId(id)

        val ds = applicationContext.dataSource
        val inFlights = ds.hentInFlightMedId(id)

        assertThat(inFlights).hasSize(1)

        applicationContext.ryddInFlightService.rydd()

        val oppdatertInFlight = ds.hentInFlightMedId(id)
        assertThat(oppdatertInFlight).isEmpty()

        verify(exactly = 1) {
            applicationContext.kafkaProducer.send(any())
        }
        confirmVerified(applicationContext.kafkaProducer)
    }

    private fun sendTestbehovMedId(id: String) {
        val behov = mapOf("behov" to "{}")
        val behovssekvens = nyBehovssekvens(
            id = id,
            behov = behov,
            løsninger = mapOf()
        ).toJson()
        rapid.sendTestMessage(behovssekvens)
    }
}
