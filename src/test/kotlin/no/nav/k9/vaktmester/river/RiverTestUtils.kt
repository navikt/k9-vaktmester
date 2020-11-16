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
import no.nav.k9.rapid.behov.Behov
import no.nav.k9.rapid.behov.Behovsformat
import no.nav.k9.rapid.behov.Behovssekvens
import no.nav.k9.vaktmester.db.InFlight
import no.nav.k9.vaktmester.db.somInFlight
import org.skyscreamer.jsonassert.JSONAssert
import java.time.ZonedDateTime
import java.util.UUID
import javax.sql.DataSource

internal fun correlationId() = "${UUID.randomUUID()}-${UUID.randomUUID()}-${UUID.randomUUID()}".substring(0, 100).also {
    require(it.length == 100)
}

internal fun nyBehovssekvens(
    id: String,
    behov: Map<String, String?>,
    løsninger: Map<String, String?>,
    sistEndret: ZonedDateTime? = null
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
    if (sistEndret != null) {
        jsonMessage[Behovsformat.SistEndret] = sistEndret
    }
    return jsonMessage
}

internal fun assertBehovssekvenserLike(b1: String, b2: String) {
    JSONAssert.assertEquals(
        settJsonFeltTomt(b1, "system_read_count"),
        settJsonFeltTomt(b2, "system_read_count"),
        true
    )
}

internal fun settJsonFeltTomt(json: String, feltnavn: String): String {
    val jsonMessage = JsonMessage(json, MessageProblems(""))
    jsonMessage[feltnavn] = ""
    return jsonMessage.toJson()
}

internal fun DataSource.hentInFlightMedId(id: String): List<InFlight> {
    return using(sessionOf(this)) { session ->
        val query = queryOf("SELECT BEHOVSSEKVENSID, BEHOVSSEKVENS, SIST_ENDRET, CORRELATION_ID, OPPRETTETTIDSPUNKT FROM IN_FLIGHT WHERE BEHOVSSEKVENSID = ?", id)
        return@using session.run(
            query.map { it.somInFlight() }.asList
        )
    }
}

internal fun String.sistEndret() =
    ZonedDateTime.parse(
        objectMapper
            .readTree(this)
            .at("/${Behovsformat.SistEndret}")
            .toString()
            .replace("\"".toRegex(), "")
    )
internal val objectMapper = jacksonObjectMapper()
    .registerModule(JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
