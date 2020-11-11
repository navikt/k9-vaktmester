package no.nav.k9.vaktmester.river

import com.fasterxml.jackson.databind.node.TextNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.k9.rapid.behov.Behovsformat
import no.nav.k9.rapid.behov.Behovssekvens
import org.slf4j.MDC
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.stream.Stream
import java.util.stream.StreamSupport

internal const val Løsninger = "@løsninger"
internal const val BehovssekvensIdKey = "behovssekvens_id"
internal const val CorrelationIdKey = "correlation_id"

private val StøttetTypeOgVersjon = Pair(
    TextNode(Behovsformat.BehovssekvensType), TextNode(Behovsformat.BehovssekvensVersjon)
)

internal fun erBehovssekvens(jsonMessage: JsonMessage): Boolean {
    jsonMessage.interestedIn(Behovsformat.Type, Behovsformat.Versjon)
    val typeOgVersjon = Pair(
        jsonMessage[Behovsformat.Type], jsonMessage[Behovsformat.Versjon]
    )

    return if (typeOgVersjon == StøttetTypeOgVersjon) {
        Behovssekvens.demandedKeys.forEach { jsonMessage.demandKey(it) }
        Behovssekvens.demandedValues.forEach { (key, value) -> jsonMessage.demandValue(key, value) }
        jsonMessage.interestedIn(Løsninger, Behovsformat.SistEndret)
        true
    } else {
        jsonMessage.requireValue(Behovsformat.Type, StøttetTypeOgVersjon.first.asText())
        jsonMessage.requireValue(Behovsformat.Versjon, StøttetTypeOgVersjon.first.asText())
        false
    }
}

internal fun JsonMessage.requireLikeLøsningerSomBehov() {
    if (!erBehovssekvens(this)) return

    val behov = get(Behovsformat.Behov)

    behov.fieldNames().forEach {
        require("$Løsninger.$it") { node ->
            if (node.isMissingOrNull()) {
                throw IllegalStateException("Mangler løsning på behov: $it")
            }
        }
    }
}

internal fun JsonMessage.requireUløsteBehov() {
    if (!erBehovssekvens(this)) return

    val behov = get(Behovsformat.Behov)
    val løsninger = get(Løsninger)
    val alleBehovHarLøsning = behov.fieldNames().stream().allMatch { beh ->
        løsninger.fieldNames().stream().anyMatch { løsning ->
            løsning == beh
        }
    }
    if (alleBehovHarLøsning) {
        løsninger.fieldNames().forEach {
            rejectKey("$Løsninger.$it")
        }
    }
}

internal fun JsonMessage.getString(key: String) = get(key).also {
    if (it.isMissingOrNull()) throw IllegalStateException("Mangler $key")
    if (it !is TextNode) throw IllegalStateException("$key må være String")
}.let { requireNotNull(it.asText()) }

internal val ISO8601 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX")

internal fun JsonMessage.behovssekvensId() = getString(Behovsformat.Id)
internal fun JsonMessage.correlationId() = getString(Behovsformat.CorrelationId)
internal fun JsonMessage.sistEndret() = ZonedDateTime.parse(getString(Behovsformat.SistEndret), ISO8601)

internal fun withMDC(context: Map<String, String>, block: () -> Unit) {
    val contextMap = MDC.getCopyOfContextMap() ?: emptyMap()
    try {
        MDC.setContextMap(contextMap + context)
        block()
    } finally {
        MDC.setContextMap(contextMap)
    }
}

private fun Iterator<String>.stream(): Stream<String> {
    val iterable = Iterable { this }
    return StreamSupport.stream(iterable.spliterator(), false)
}
