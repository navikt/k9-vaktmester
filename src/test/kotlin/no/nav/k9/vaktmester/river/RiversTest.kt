package no.nav.k9.vaktmester.river

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.k9.ApplicationContext
import no.nav.k9.rapid.behov.Behov
import no.nav.k9.rapid.behov.Behovsformat
import no.nav.k9.rapid.behov.Behovssekvens
import no.nav.k9.registerApplicationContext
import no.nav.k9.testutils.ApplicationContextExtension
import no.nav.k9.testutils.cleanAndMigrate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.skyscreamer.jsonassert.JSONAssert
import java.time.ZonedDateTime
import java.util.UUID

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

        val inFlight = hentInFlightMedId(id)
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

        val inFlight = hentInFlightMedId(id)

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

        val inFlightGammel = hentInFlightMedId(idGammel)
        val inFlightEksisterende = hentInFlightMedId(idEksist)

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

        val inFlightOppdatertGammel = hentInFlightMedId(idGammel)
        val inFlightEksisterendeEtterOppdatering = hentInFlightMedId(idEksist)

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

        val inFlight = hentInFlightMedId(id)

        assertBehovssekvenserLike(inFlight[0].behovssekvens, nyesteBehovssekvens.toJson())
        assertThat(inFlight[0].sistEndret).isEqualTo(nyesteBehovssekvens.toJson().sistEndret())
    }

    private fun assertBehovssekvenserLike(b1: String, b2: String) {
        JSONAssert.assertEquals(
            settJsonFeltTomt(b1, "system_read_count"),
            settJsonFeltTomt(b2, "system_read_count"),
            true
        )
    }

    private fun settJsonFeltTomt(json: String, feltnavn: String): String {
        val jsonMessage = JsonMessage(json, MessageProblems(""))
        jsonMessage[feltnavn] = ""
        return jsonMessage.toJson()
    }

    private fun hentInFlightMedId(id: String): List<InFlight> {
        return using(sessionOf(applicationContext.dataSource)) { session ->
            val query = queryOf("SELECT BEHOVSSEKVENS, SIST_ENDRET FROM IN_FLIGHT WHERE BEHOVSSEKVENSID = ?", id)
            return@using session.run(
                query.map { row ->
                    InFlight(
                        behovssekvens = row.string("BEHOVSSEKVENS"),
                        sistEndret = row.zonedDateTime("SIST_ENDRET")
                    )
                }.asList
            )
        }
    }

    private fun String.sistEndret() =
        ZonedDateTime.parse(
            objectMapper
                .readTree(this)
                .at("/${Behovsformat.SistEndret}")
                .toString()
                .replace("\"".toRegex(), "")
        )

    private companion object {
        private fun correlationId() = "${UUID.randomUUID()}-${UUID.randomUUID()}-${UUID.randomUUID()}".substring(0, 100).also {
            require(it.length == 100)
        }
        fun løsningsJsonPointer(behov: String) = "/@løsninger/$behov"

        private fun nyBehovssekvens(
            id: String,
            behov: Map<String, String?>,
            løsninger: Map<String, String?>
        ): JsonMessage {
            val sekvens = Behovssekvens(
                id = id,
                correlationId = correlationId(),
                behov = behov.entries.map {
                    Behov(
                        navn = it.key,
                        input = mapOf(
                            "hvasomhelst" to it.value
                        )
                    )
                }.toTypedArray()
            ).keyValue.second

            val jsonMessage = JsonMessage(sekvens, MessageProblems(""))

            jsonMessage[Løsninger] = løsninger
            return jsonMessage
        }
    }
}

internal val objectMapper = jacksonObjectMapper()
    .registerModule(JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

data class InFlight(
    val behovssekvens: String,
    val sistEndret: ZonedDateTime
)
