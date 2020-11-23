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
        period = Duration.ofMinutes(15).toMillis(),
    ) {
        logger.info("Starter ryddejobb")
        ryddejobbSistStartet.setToCurrentTime()
        ryddeService.rydd()
        ryddejobbSistFerdig.setToCurrentTime()
    }

    internal fun stop() {
        timer.cancel()
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(RyddeScheduler::class.java)
        private val ryddejobbSistStartet: Gauge = Gauge
            .build("ryddejobbSistStartet", "Sist tidspunkt ryddejobben startet")
            .register()
        private val ryddejobbSistFerdig: Gauge = Gauge
            .build("ryddejobbSistFerdig", "Sist tidspunkt ryddejobben var ferdig")
            .register()
    }
}
