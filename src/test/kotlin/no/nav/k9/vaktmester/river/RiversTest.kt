package no.nav.k9.vaktmester.river

import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.k9.ApplicationContext
import no.nav.k9.registerApplicationContext
import no.nav.k9.testutils.ApplicationContextExtension
import no.nav.k9.testutils.cleanAndMigrate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ApplicationContextExtension::class)
internal class RiversTest(
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
    fun `like løsninger som behov lagres i arkiv, men ikke in_flight`() {
        val behov1 = "behov1"
        val behov2 = "behov2"
        val behov = mapOf(
            behov1 to "{}",
            behov2 to "{}"
        )
        val id = "01EKW89QKK5YZ0XW2QQYS0TB8D"
        val behovssekvens = nyBehovssekvens(
            id = id,
            behov = behov,
            løsninger = behov
        ).toJson()

        rapid.sendTestMessage(behovssekvens)

        val arkiv = applicationContext.arkivRepository.hentArkivMedId(id)[0]

        assertBehovssekvenserLike(behovssekvens, arkiv.behovssekvens)
        assertThat(arkiv.arkiveringstidspunkt).isNotNull()
        assertThat(arkiv.correlationId).isNotNull()

        val inFlight = applicationContext.dataSource.hentInFlightMedId(id)
        assertThat(inFlight).isEmpty()
    }

    @Test
    fun `ulike løsninger som behov lagres i in_flight, men ikke arkiv`() {
        val behov1 = "behov1"
        val behov2 = "behov2"
        val behov = mapOf(
            behov1 to "{}",
            behov2 to "{}"
        )
        val id = "01BX5ZZKBKACTAV9WEVGEMMVS0"
        val behovssekvens = nyBehovssekvens(
            id = id,
            behov = behov,
            løsninger = mapOf(behov1 to "{}")
        )
        val behovssekvensJson = behovssekvens.toJson()

        rapid.sendTestMessage(behovssekvensJson)

        val arkiv = applicationContext.arkivRepository.hentArkivMedId(id)
        assertThat(arkiv).hasSize(0)

        val inFlight = applicationContext.dataSource.hentInFlightMedId(id)

        assertThat(inFlight).hasSize(1)
        assertBehovssekvenserLike(behovssekvensJson, inFlight[0].behovssekvens)

        assertThat(inFlight[0].sistEndret).isEqualTo(behovssekvensJson.sistEndret())
    }

    @Test
    fun `mangler løsninger håndteres som at det ikke er noen løsninger`() {
        val behov1 = "behov1"
        val behov = mapOf(
            behov1 to "{}",
        )
        val id = "01EQGPJTTARPE2P3V9MFHGV5G4"
        val behovssekvens = nyBehovssekvens(
            id = id,
            behov = behov,
            løsninger = null
        )
        val behovssekvensJson = behovssekvens.toJson()

        rapid.sendTestMessage(behovssekvensJson)

        val arkiv = applicationContext.arkivRepository.hentArkivMedId(id)
        assertThat(arkiv).hasSize(0)

        val inFlight = applicationContext.dataSource.hentInFlightMedId(id)

        assertThat(inFlight).hasSize(1)
        assertBehovssekvenserLike(behovssekvensJson, inFlight[0].behovssekvens)

        assertThat(inFlight[0].sistEndret).isEqualTo(behovssekvensJson.sistEndret())
    }

    @Test
    fun `oppdaterer rad i inflight dersom sistendret er nyere`() {
        val behov1 = "behov1"
        val behov2 = "behov2"
        val behov = mapOf(
            behov1 to "{}",
            behov2 to "{}"
        )
        val idGammel = "01BX5ZZKBKACTAV9WEVGEMMVS0"
        val løsninger = mapOf(behov1 to "{}")
        val behovssekvensGammel = nyBehovssekvens(
            id = idGammel,
            behov = behov,
            løsninger = løsninger
        )
        val idEksist = "01EPZ073912ZX2566ESEGPATG1"
        val eksisterendeSekvens = nyBehovssekvens(
            id = idEksist,
            behov = mapOf(
                "eksisterende" to "{}"
            ),
            løsninger = løsninger
        )

        val behovssekvensGammelJson = behovssekvensGammel.toJson()
        rapid.sendTestMessage(eksisterendeSekvens.toJson())
        rapid.sendTestMessage(behovssekvensGammelJson)

        val ds = applicationContext.dataSource
        val inFlightGammel = ds.hentInFlightMedId(idGammel)
        val inFlightEksisterende = ds.hentInFlightMedId(idEksist)

        assertThat(inFlightGammel).hasSize(1)
        assertThat(inFlightEksisterende).hasSize(1)
        assertBehovssekvenserLike(eksisterendeSekvens.toJson(), inFlightEksisterende[0].behovssekvens)
        assertBehovssekvenserLike(behovssekvensGammelJson, inFlightGammel[0].behovssekvens)

        val nyeBehov = mapOf(
            behov1 to "{}",
            behov2 to "{}",
            "behov3" to "{}"
        )
        val behovssekvensOppdatertSistEndret = nyBehovssekvens(
            id = idGammel,
            behov = nyeBehov,
            løsninger = løsninger
        )

        rapid.sendTestMessage(behovssekvensOppdatertSistEndret.toJson())

        val inFlightOppdatertGammel = ds.hentInFlightMedId(idGammel)
        val inFlightEksisterendeEtterOppdatering = ds.hentInFlightMedId(idEksist)

        assertThat(inFlightOppdatertGammel).hasSize(1)
        assertBehovssekvenserLike(behovssekvensOppdatertSistEndret.toJson(), inFlightOppdatertGammel[0].behovssekvens)
        // Skal være lik som før
        assertBehovssekvenserLike(eksisterendeSekvens.toJson(), inFlightEksisterendeEtterOppdatering[0].behovssekvens)
    }

    @Test
    fun `lagrer ingenting i inflight dersom id og sistendret er eldre enn lagret`() {
        val behov1 = "behov1"
        val behov2 = "behov2"
        val behov = mapOf(
            behov1 to "{}",
            behov2 to "{}"
        )
        val id = "01BX5ZZKBKACTAV9WEVGEMMVS0"
        val eldsteBehovssekvens = nyBehovssekvens(
            id = id,
            behov = mapOf(
                behov2 to "{}"
            ),
            løsninger = mapOf()
        )
        val nyesteBehovssekvens = nyBehovssekvens(
            id = id,
            behov = behov,
            løsninger = mapOf(behov1 to "{}")
        )

        rapid.sendTestMessage(nyesteBehovssekvens.toJson())
        rapid.sendTestMessage(eldsteBehovssekvens.toJson())

        val inFlight = applicationContext.dataSource.hentInFlightMedId(id)

        assertBehovssekvenserLike(inFlight[0].behovssekvens, nyesteBehovssekvens.toJson())
        assertThat(inFlight[0].sistEndret).isEqualTo(nyesteBehovssekvens.toJson().sistEndret())
    }

    @Test
    fun `sletter inflights som blir arkivert`() {
        val behov1 = "behov1"
        val behov2 = "behov2"
        val behov = mapOf(
            behov1 to "{}",
            behov2 to "{}"
        )
        val id = "01EKW89QKK5YZ0XW2QQYS0TB8D"
        val uløstBehovssekvens = nyBehovssekvens(
            id = id,
            behov = behov,
            løsninger = mapOf(behov1 to "{}")
        ).toJson()

        rapid.sendTestMessage(uløstBehovssekvens)
        val uløstInFlight = applicationContext.dataSource.hentInFlightMedId(id)

        assertThat(uløstInFlight).hasSize(1)

        val løstBehovssekvens = nyBehovssekvens(
            id = id,
            behov = behov,
            løsninger = behov
        ).toJson()

        rapid.sendTestMessage(løstBehovssekvens)
        val løstInFlight = applicationContext.dataSource.hentInFlightMedId(id)

        assertThat(løstInFlight).isEmpty()
        val arkiv = applicationContext.arkivRepository.hentArkivMedId(id)
        assertThat(arkiv).hasSize(1)
    }
}
