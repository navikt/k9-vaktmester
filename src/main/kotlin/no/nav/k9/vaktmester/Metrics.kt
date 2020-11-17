package no.nav.k9.vaktmester

import io.prometheus.client.Counter
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private object Metrics {
    val logger: Logger = LoggerFactory.getLogger(Metrics::class.java)

    val arkivertBehovssekvens: Counter = Counter
        .build("arkivert_behovssekvens", "Arkiverte behovssekvens")
        .register()
}

private fun safeMetric(block: () -> Unit) = try {
    block()
} catch (cause: Throwable) {
    Metrics.logger.warn("Feil ved å rapportera metrics", cause)
}

internal fun incArkivertBehovssekvens() = safeMetric { Metrics.arkivertBehovssekvens.inc() }
