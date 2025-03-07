package no.nav.k9.vaktmester.river

import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.k9.rapid.behov.Behovsformat
import no.nav.k9.rapid.river.requireArray
import no.nav.k9.rapid.river.requireObject
import no.nav.k9.rapid.river.requireText
import no.nav.k9.vaktmester.db.Arkiv
import java.time.ZonedDateTime
import no.nav.k9.rapid.river.behovssekvensId
import no.nav.k9.vaktmester.db.InFlight

internal const val Løsninger = "@løsninger"
private val objectMapper = jacksonObjectMapper()

internal data class Meldingsinformasjon(
    internal val sistEndret: ZonedDateTime,
    internal val behov: ObjectNode,
    internal val løsninger: ObjectNode,
    internal val behovssekvensId: String,
    internal val correlationId: String,
    internal val behovsrekkefølge: List<String>
) {
    internal val skalArkivers = løsninger.fieldNamesList().containsAll(behov.fieldNamesList())
    internal val inFlight = !skalArkivers
}

internal fun JsonMessage.vaktmesterOppgave(): JsonMessage {
    require(Behovsformat.BehovssekvensId) { it.requireText() }
    require(Behovsformat.CorrelationId) { it.requireText() }
    require(Behovsformat.SistEndret) { ZonedDateTime.parse(it.asText()) }
    require(Behovsformat.Behov) { it.requireObject() }
    interestedIn(Løsninger)
    require(Behovsformat.Behovsrekkefølge) { it.requireArray() }
    return this
}

internal fun JsonMessage.meldingsinformasjon() = Meldingsinformasjon(
    sistEndret = ZonedDateTime.parse(get(Behovsformat.SistEndret).asText()),
    behov = get(Behovsformat.Behov) as ObjectNode,
    løsninger = when (get(Løsninger).isMissingOrNull()) {
        true -> objectMapper.createObjectNode()
        false -> get(Løsninger) as ObjectNode
    },
    behovssekvensId = behovssekvensId(),
    correlationId = get(Behovsformat.CorrelationId).asText(),
    behovsrekkefølge = (get(Behovsformat.Behovsrekkefølge) as ArrayNode).map { it.asText()!! }
)

internal fun List<Arkiv>.doIfEmpty(task: () -> Unit) = when (isEmpty()) {
    true -> task()
    else -> {}
}

internal fun ObjectNode.fieldNamesList(): List<String> = this.fieldNames().asSequence().toList()

internal fun InFlight.somMeldingsinformasjon() =
    behovssekvens.behovssekvensSomMeldingsinformasjon()

internal fun String.behovssekvensSomMeldingsinformasjon() =
    JsonMessage(this, MessageProblems(this), SimpleMeterRegistry())
        .vaktmesterOppgave()
        .meldingsinformasjon()

internal fun Meldingsinformasjon.uløstBehov(): String {
    val løsteBehov = løsninger.fieldNamesList()
    return behovsrekkefølge.firstOrNull {
        it !in løsteBehov
    } ?: "n/a"
}