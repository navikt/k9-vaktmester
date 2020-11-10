package no.nav.k9.vaktmester.arkiv

import com.fasterxml.jackson.databind.node.TextNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.k9.rapid.behov.Behovsformat
import no.nav.k9.vaktmester.db.ArkivRepository
import org.slf4j.LoggerFactory
import org.slf4j.MDC

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

    private fun withMDC(context: Map<String, String>, block: () -> Unit) {
        val contextMap = MDC.getCopyOfContextMap() ?: emptyMap()
        try {
            MDC.setContextMap(contextMap + context)
            block()
        } finally {
            MDC.setContextMap(contextMap)
        }
    }

    private companion object {
        private const val BehovssekvensIdKey = "behovssekvens_id"
        private const val CorrelationIdKey = "correlation_id"
    }
}

internal fun JsonMessage.getString(key: String) = get(key).also {
    if (it.isMissingOrNull()) throw IllegalStateException("Mangler $key")
    if (it !is TextNode) throw IllegalStateException("$key må være String")
}.let { requireNotNull(it.asText()) }

internal fun JsonMessage.behovssekvensId() = getString(Behovsformat.Id)
internal fun JsonMessage.correlationId() = getString(Behovsformat.CorrelationId)
