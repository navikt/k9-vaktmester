package no.nav.k9.vaktmester.river

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.k9.vaktmester.håndter
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

internal class OnPremTilAivenRiver(
    rapidsConnection: RapidsConnection,
    private val kafkaProducer: KafkaProducer<String, String>,
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
            val record = ProducerRecord(AivenTopic, meldingsinformasjon.behovssekvensId, packet.toJson())
            kafkaProducer.send(record).get().also {
                logger.info("Uløst behov=[${meldingsinformasjon.uløstBehov()}] publisert OnPrem er sendt til Aiven. Topic=${it.topic()}, Offset=[${it.offset()}], Partition=[${it.partition()}]")
            }
        }
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(OnPremTilAivenRiver::class.java)
        private const val AivenTopic = "omsorgspenger.k9-rapid-v2"
    }
}

