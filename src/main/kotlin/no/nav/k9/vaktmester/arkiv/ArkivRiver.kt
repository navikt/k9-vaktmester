package no.nav.k9.vaktmester.arkiv

import com.fasterxml.jackson.databind.node.TextNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.k9.rapid.behov.Behovsformat
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
                packet.arkiverHvisLøsningerOgBehovErLike()
            }
        }.register(this)
    }

    // TODO: withMDC som i BehovssekvensPacketListener
    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        val behovssekvensId = packet.behovssekvensId()
        logger.info("Behovssekvens arkivert")

        arkivRepository.arkiverBehovssekvens(behovssekvensId, packet.toJson())
    }
}

internal fun JsonMessage.getString(key: String) = get(key).also {
    if (it.isMissingOrNull()) throw IllegalStateException("Mangler $key")
    if (it !is TextNode) throw IllegalStateException("$key må være String")
}.let { requireNotNull(it.asText()) }

internal fun JsonMessage.behovssekvensId() = getString(Behovsformat.Id)
