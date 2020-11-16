package no.nav.k9.vaktmester

import org.slf4j.LoggerFactory
import org.slf4j.MDC

private const val BehovssekvensIdKey = "behovssekvens_id"
private const val CorrelationIdKey = "correlation_id"
private val secureLogger = LoggerFactory.getLogger("tjenestekall")

private fun withMDC(
    behovssekvensId: String,
    correlationId: String,
    block: () -> Unit) {
    val contextMap = MDC.getCopyOfContextMap() ?: emptyMap()
    try {
        MDC.setContextMap(contextMap + mapOf(
            BehovssekvensIdKey to behovssekvensId,
            CorrelationIdKey to correlationId
        ))
        block()
    } finally {
        MDC.setContextMap(contextMap)
    }
}

internal fun håndter(
    behovssekvensId: String,
    correlationId: String,
    behovssekvens: String,
    håndterFeil: (feil: String) -> Unit = {
        throw IllegalStateException(it)
    },
    block: () -> Unit) = runCatching {
    withMDC(
        behovssekvensId = behovssekvensId,
        correlationId = correlationId) {
        block()
    }
}.fold(
    onSuccess = {},
    onFailure = { cause ->
        val feil = "håndter kastet exception ${cause::class.simpleName}"
        secureLogger.error("$feil. ErrorPacket=$behovssekvens", cause)
        håndterFeil("$feil. Se sikker log for mer detaljer.")
    }
)
