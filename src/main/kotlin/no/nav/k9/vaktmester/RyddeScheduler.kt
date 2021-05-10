package no.nav.k9.vaktmester

import io.prometheus.client.Gauge
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.concurrent.fixedRateTimer

internal class RyddeScheduler(
    private val ryddeService: RyddeService,
) {

    private val timer = fixedRateTimer(
        name = "ryddejobb",
        initialDelay = Duration.ofMinutes(2).toMillis(),
        period = Duration.ofMinutes(2).toMillis(),
    ) {
        logger.info("Starter ryddejobb")
        ryddejobbSistStartet.setToCurrentTime()
        ryddeService.rydd()
        ryddejobbSistFerdig.setToCurrentTime()
        logger.info("Ryddejobb ferdig")
    }

    internal fun stop() {
        timer.cancel()
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(RyddeScheduler::class.java)
        private val ryddejobbSistStartet = LateInitGauge(Gauge
            .build("ryddejobbSistStartet", "Sist tidspunkt ryddejobben startet")
        )
        private val ryddejobbSistFerdig = LateInitGauge(Gauge
            .build("ryddejobbSistFerdig", "Sist tidspunkt ryddejobben var ferdig")
        )
    }
}
