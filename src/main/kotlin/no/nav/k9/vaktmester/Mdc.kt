package no.nav.k9.vaktmester

import org.slf4j.MDC

private const val BehovssekvensIdKey = "behovssekvens_id"
private const val CorrelationIdKey = "correlation_id"

internal fun withMDC(behovssekvensId: String, correlationId: String, block: () -> Unit) {
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
