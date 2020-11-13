package no.nav.k9.vaktmester

import no.nav.k9.vaktmester.db.ArkivRepository
import no.nav.k9.vaktmester.db.InFlightRepository
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.time.Duration

internal class RyddInFlightService(
    private val inFlightRepository: InFlightRepository,
    private val arkivRepository: ArkivRepository,
    private val kafkaProducer: KafkaProducer<String, String>
) {

    private val logger = LoggerFactory.getLogger(RyddInFlightService::class.java)

    internal fun rydd() {
        inFlightRepository.hentAlleInFlights(Duration.ofMinutes(MINUTTER_TILBAKE_I_TID), 20).forEach {
            val arkiv = arkivRepository.hentArkivMedId(it.behovssekvensId)

            if (arkiv.isEmpty()) {
                logger.info("Republiserer behovssekvens med id: ${it.behovssekvensId}")
                kafkaProducer.send(ProducerRecord("", it.behovssekvensId, it.behovssekvens))
            }
            inFlightRepository.slett(it.behovssekvensId)
        }
    }

    internal companion object {
        internal const val MINUTTER_TILBAKE_I_TID = 30L
        internal const val MAX_ANTALL_Å_HENTE = 20
    }
}
