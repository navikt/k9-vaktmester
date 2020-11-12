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
                packet.vaktmesterOppgave()
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        val meldingsinformasjon = packet.meldingsinformasjon()
        if (meldingsinformasjon.skalArkivers) return

        meldingsinformasjon.håndter {
            arkivRepository.hentArkivMedId(meldingsinformasjon.behovssekvensId).doIfEmpty {
                inFlightRepository.lagreInFlightBehov(
                    behovsid = meldingsinformasjon.behovssekvensId,
                    behovssekvens = packet.toJson(),
                    sistEndret = meldingsinformasjon.sistEndret,
                    correlationId = meldingsinformasjon.correlationId
                )
                logger.info("Lagret in flight behovsseksvens med id ${meldingsinformasjon.behovssekvensId}")
            }
        }
    }
}

private fun List<Arkiv>.doIfEmpty(task: () -> Unit) = when (isEmpty()) {
    true -> task()
    else -> {}
}
