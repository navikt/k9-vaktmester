package no.nav.k9.vaktmester.river

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.k9.vaktmester.db.Arkiv
import no.nav.k9.vaktmester.db.ArkivRepository
import no.nav.k9.vaktmester.db.InFlightRepository
import org.slf4j.LoggerFactory

internal class InFlightRiver(
    rapidsConnection: RapidsConnection,
    private val inFlightRepository: InFlightRepository,
    private val arkivRepository: ArkivRepository
) : River.PacketListener {

    private val logger = LoggerFactory.getLogger(InFlightRiver::class.java)

    init {
        River(rapidsConnection).apply {
            validate { packet ->
                packet.requireUløsteBehov()
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
            arkivRepository.hentArkivMedId(behovssekvensId).doIfEmpty {
                inFlightRepository.lagreInFlightBehov(
                    behovsid = behovssekvensId,
                    behovssekvens = packet.toJson(),
                    sistEndret = packet.sistEndret(),
                    correlationId = correlationId
                )
                logger.info("Lagret in flight behovsseksvens med id $behovssekvensId")
            }
        }
    }
}

private fun List<Arkiv>.doIfEmpty(task: () -> Unit) = when (isEmpty()) {
    true -> task()
    else -> {}
}
