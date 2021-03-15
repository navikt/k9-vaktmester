package no.nav.k9.vaktmester.river

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.k9.vaktmester.db.ArkivRepository
import no.nav.k9.vaktmester.db.InFlightRepository
import no.nav.k9.vaktmester.håndter

internal class InFlightRiver(
    rapidsConnection: RapidsConnection,
    private val inFlightRepository: InFlightRepository,
    private val arkivRepository: ArkivRepository
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { packet ->
                packet.vaktmesterOppgave()
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val meldingsinformasjon = packet.meldingsinformasjon()
        if (meldingsinformasjon.skalArkivers) return

        håndter(
            behovssekvensId = meldingsinformasjon.behovssekvensId,
            correlationId = meldingsinformasjon.correlationId,
            behovssekvens = packet.toJson()) {
            arkivRepository.hentArkivMedId(meldingsinformasjon.behovssekvensId).doIfEmpty {
                inFlightRepository.lagreInFlightBehov(
                    behovsid = meldingsinformasjon.behovssekvensId,
                    behovssekvens = packet.toJson(),
                    sistEndret = meldingsinformasjon.sistEndret,
                    correlationId = meldingsinformasjon.correlationId
                )
            }
        }
    }
}

