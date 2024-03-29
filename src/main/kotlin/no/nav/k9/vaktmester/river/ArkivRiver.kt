package no.nav.k9.vaktmester.river

import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.k9.rapid.behov.Behovsformat
import no.nav.k9.vaktmester.LateInitGauge
import no.nav.k9.vaktmester.db.ArkivRepository
import no.nav.k9.vaktmester.db.InFlightRepository
import no.nav.k9.vaktmester.håndter
import org.slf4j.LoggerFactory

internal class ArkivRiver(
    rapidsConnection: RapidsConnection,
    private val arkivRepository: ArkivRepository,
    private val inflightRepository: InFlightRepository
) : River.PacketListener {

    private val logger = LoggerFactory.getLogger(ArkivRiver::class.java)

    init {
        River(rapidsConnection).apply {
            validate { packet ->
                packet.vaktmesterOppgave()
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        if (packet[Behovsformat.BehovssekvensId].isMissingOrNull()) {
            logger.info("Mangler @behovssekvensId i behovsformat")
        }

        val meldingsinformasjon = packet.meldingsinformasjon()
        if (meldingsinformasjon.inFlight) return

        håndter(
            behovssekvensId = meldingsinformasjon.behovssekvensId,
            correlationId = meldingsinformasjon.correlationId,
            behovssekvens = packet.toJson()
        ) {
            arkivRepository.arkiverBehovssekvens(
                behovsid = meldingsinformasjon.behovssekvensId,
                behovssekvens = packet.toJson(),
                correlationId = meldingsinformasjon.correlationId
            )
            logger.info("Behovssekvens arkivert").also {
                arkiverteBehovssekvenserCounter.inc()
                sistArkivering.setToCurrentTime()
                meldingsinformasjon.behovsrekkefølge.forEach { behov ->
                    arkiverteBehovCounter.labels(behov).inc()
                }
            }
        }

        håndter(
            behovssekvensId = meldingsinformasjon.behovssekvensId,
            correlationId = meldingsinformasjon.correlationId,
            behovssekvens = packet.toJson(),
            håndterFeil = { logger.warn(it) }
        ) {
            inflightRepository.slett(meldingsinformasjon.behovssekvensId)
        }
    }

    private companion object {
        private val arkiverteBehovssekvenserCounter = Counter
            .build("arkiverteBehovssekvenser", "Antall arkiverte behovssekvenser")
            .register()

        private val arkiverteBehovCounter = Counter
            .build("arkiverteBehov", "Antall arkiverte behov")
            .labelNames("behov")
            .register()

        private val sistArkivering = LateInitGauge(
            Gauge
                .build("sistArkivering", "Siste tidspunkt en melding ble arkivert")
        )
    }
}
