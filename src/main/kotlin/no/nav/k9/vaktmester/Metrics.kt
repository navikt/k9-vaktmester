package no.nav.k9.vaktmester

import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private object Metrics {
    val logger: Logger = LoggerFactory.getLogger(Metrics::class.java)
}

private fun safeMetric(block: () -> Unit) = try {
    block()
} catch (cause: Throwable) {
    Metrics.logger.warn("Feil ved rapportering av metrics", cause)
}

internal fun Counter.safeInc() = safeMetric { inc() }
internal fun Counter.Child.safeInc() = safeMetric { inc() }
internal fun Gauge.Child.safeInc() = safeMetric { inc() }
