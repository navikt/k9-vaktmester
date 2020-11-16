package no.nav.k9.vaktmester

import java.time.Duration
import kotlin.concurrent.fixedRateTimer

internal class RyddInFlightScheduler(
    private val ryddeService: RepubliseringService,
) {

    private val timer = fixedRateTimer(
        name = "rydd_inflight",
        initialDelay = Duration.ofMinutes(2).toMillis(),
        period = Duration.ofMinutes(15).toMillis(),
    ) {
        ryddeService.republiserGamleUarkiverteMeldinger()
    }

    internal fun stop() {
        timer.cancel()
    }
}
