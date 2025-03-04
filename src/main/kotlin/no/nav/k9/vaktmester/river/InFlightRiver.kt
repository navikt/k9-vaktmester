package no.nav.k9.vaktmester.river

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.k9.rapid.behov.Behovsformat
import no.nav.k9.vaktmester.db.ArkivRepository
import no.nav.k9.vaktmester.db.InFlightRepository
import no.nav.k9.vaktmester.håndter
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

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        if(packet[Behovsformat.BehovssekvensId].isMissingOrNull()) {
            logger.info("Mangler @behovssekvensId i behovsformat")
        }

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

