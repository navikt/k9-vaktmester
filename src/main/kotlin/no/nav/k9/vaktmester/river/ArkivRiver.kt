package no.nav.k9.vaktmester.river

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.k9.vaktmester.db.ArkivRepository
import org.slf4j.LoggerFactory

internal class ArkivRiver(
    rapidsConnection: RapidsConnection,
    private val arkivRepository: ArkivRepository
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

        meldingsinformasjon.håndter {
            arkivRepository.arkiverBehovssekvens(
                behovsid = meldingsinformasjon.behovssekvensId,
                behovssekvens = packet.toJson(),
                correlationId = meldingsinformasjon.correlationId
            )
            logger.info("Behovssekvens arkivert")
        }
    }
}
