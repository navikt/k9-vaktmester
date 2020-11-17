package no.nav.k9.vaktmester.river

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.k9.vaktmester.db.ArkivRepository
import no.nav.k9.vaktmester.db.InFlightRepository
import no.nav.k9.vaktmester.håndter
import no.nav.k9.vaktmester.incArkivertBehovssekvens
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

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
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
            logger.info("Behovssekvens arkivert").also { incArkivertBehovssekvens() }
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
}
