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
    internal fun `like løsninger som behov lagres i arkiv, men ikke in_flight`() {
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

        JSONAssert.assertEquals(
            settJsonFeltTomt(behovssekvens, "system_read_count"),
            settJsonFeltTomt(arkiv.behovssekvens, "system_read_count"),
            true
        )
        assertThat(arkiv.arkiveringstidspunkt).isNotNull()
        assertThat(arkiv.correlationId).isNotNull()

        val inFlight = hentInFlightMedId(id)
        assertThat(inFlight).isEmpty()
    }

    @Test
    internal fun `ulike løsninger som behov lagres i in_flight, men ikke arkiv`() {
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
        JSONAssert.assertEquals(
            settJsonFeltTomt(behovssekvensJson, "system_read_count"),
            settJsonFeltTomt(inFlight[0].behovssekvens, "system_read_count"),
            true
        )

        val sistEndret = objectMapper
            .readTree(behovssekvensJson)
            .at("/${Behovsformat.SistEndret}")
            .toString()
            .replace("\"".toRegex(), "")

        assertThat(inFlight[0].sistEndret).isEqualTo(ZonedDateTime.parse(sistEndret))
    }

    @Test
    internal fun `oppdaterer rad i inflight dersom sistendret er endret`() {
        val behov1 = "behov1"
        val behov2 = "behov2"
        val behov = mapOf(
            behov1 to "{}",
            behov2 to "{}"
        )
        val id = "01BX5ZZKBKACTAV9WEVGEMMVS0"
        val løsninger = mapOf(behov1 to "{}")
        val behovssekvensGammel = nyBehovssekvens(
            id = id,
            behov = behov,
            løsninger = løsninger
        )

        val behovssekvensGammelJson = behovssekvensGammel.toJson()
        rapid.sendTestMessage(behovssekvensGammelJson)

        val inFlight = hentInFlightMedId(id)

        assertThat(inFlight).hasSize(1)

        JSONAssert.assertEquals(
            settJsonFeltTomt(behovssekvensGammelJson, "system_read_count"),
            settJsonFeltTomt(inFlight[0].behovssekvens, "system_read_count"),
            true
        )

        val nyeBehov = mapOf(
            behov1 to "{}",
            behov2 to "{}",
            "behov3" to "{}"
        )
        val behovssekvensOppdatertSistEndret = nyBehovssekvens(
            id = id,
            behov = nyeBehov,
            løsninger = løsninger
        )

        rapid.sendTestMessage(behovssekvensOppdatertSistEndret.toJson())

        val inFlights = hentInFlightMedId(id)

        assertThat(inFlights).hasSize(1)

        JSONAssert.assertEquals(
            settJsonFeltTomt(inFlights[0].behovssekvens, "system_read_count"),
            settJsonFeltTomt(behovssekvensOppdatertSistEndret.toJson(), "system_read_count"),
            true
        )
    }

    @Test
    internal fun `gjør ingenting dersom id og sistendret ikke er endret`() {
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
        rapid.sendTestMessage(behovssekvensJson)

        val inFlight = hentInFlightMedId(id)

        assertThat(inFlight).hasSize(1)
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

    internal companion object {
        fun løsningsJsonPointer(behov: String) = "/@løsninger/$behov"

        private fun nyBehovssekvens(
            id: String,
            behov: Map<String, String?>,
            løsninger: Map<String, String?>
        ): JsonMessage {
            val sekvens = Behovssekvens(
                id = id,
                correlationId = UUID.randomUUID().toString(),
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
