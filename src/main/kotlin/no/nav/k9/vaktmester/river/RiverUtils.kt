package no.nav.k9.vaktmester.river

import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.k9.rapid.behov.Behovsformat
import no.nav.k9.rapid.river.requireArray
import no.nav.k9.rapid.river.requireObject
import no.nav.k9.rapid.river.requireText
import no.nav.k9.vaktmester.db.Arkiv
import org.slf4j.MDC
import java.time.ZonedDateTime

internal const val Løsninger = "@løsninger"
private const val BehovssekvensIdKey = "behovssekvens_id"
private const val CorrelationIdKey = "correlation_id"

internal data class Meldingsinformasjon(
    internal val sistEndret: ZonedDateTime,
    private val behov: ObjectNode,
    private val løsninger: ObjectNode,
    internal val behovssekvensId: String,
    internal val correlationId: String,
    internal val behovsrekkefølge: List<String>
) {
    internal val skalArkivers = løsninger.fieldNames().asSequence().toList().containsAll(behov.fieldNames().asSequence().toList())
    internal val inFlight = !skalArkivers
    internal fun håndter(block: () -> Unit) = withMDC(
        mapOf(
            BehovssekvensIdKey to behovssekvensId,
            CorrelationIdKey to correlationId
        )
    ) { block() }
}

internal fun JsonMessage.vaktmesterOppgave() {
    require(Behovsformat.Id) { it.requireText() }
    require(Behovsformat.CorrelationId) { it.requireText() }
    require(Behovsformat.SistEndret) { ZonedDateTime.parse(it.asText()) }
    require(Behovsformat.Behov) { it.requireObject() }
    require(Løsninger) { it.requireObject() }
    require(Behovsformat.Behovsrekkefølge) { it.requireArray() }
}

internal fun JsonMessage.meldingsinformasjon() = Meldingsinformasjon(
    sistEndret = ZonedDateTime.parse(get(Behovsformat.SistEndret).asText()),
    behov = get(Behovsformat.Behov) as ObjectNode,
    løsninger = get(Løsninger) as ObjectNode,
    behovssekvensId = get(Behovsformat.Id).asText(),
    correlationId = get(Behovsformat.CorrelationId).asText(),
    behovsrekkefølge = (get(Behovsformat.Behovsrekkefølge) as ArrayNode).map { it.asText()!! }
)

internal fun List<Arkiv>.doIfEmpty(task: () -> Unit) = when (isEmpty()) {
    true -> task()
    else -> {}
}

private fun withMDC(context: Map<String, String>, block: () -> Unit) {
    val contextMap = MDC.getCopyOfContextMap() ?: emptyMap()
    try {
        MDC.setContextMap(contextMap + context)
        block()
    } finally {
        MDC.setContextMap(contextMap)
    }
}
