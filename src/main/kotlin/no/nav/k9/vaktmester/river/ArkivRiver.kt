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
                packet.requireLikeLøsningerSomBehov()
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        val behovssekvensId = packet.behovssekvensId()
        val correlationId = packet.correlationId()

        withMDC(
            mapOf(
                BehovssekvensIdKey to behovssekvensId,
                CorrelationIdKey to correlationId
            )
        ) {
            arkivRepository.arkiverBehovssekvens(behovssekvensId, packet.toJson())
            logger.info("Behovssekvens arkivert")
        }
    }
}
