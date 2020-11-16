package no.nav.k9.vaktmester

import no.nav.k9.rapid.river.Environment
import no.nav.k9.rapid.river.hentRequiredEnv
import no.nav.k9.vaktmester.db.ArkivRepository
import no.nav.k9.vaktmester.db.InFlightRepository
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.time.Duration

internal class RepubliseringService(
    private val inFlightRepository: InFlightRepository,
    private val arkivRepository: ArkivRepository,
    private val kafkaProducer: KafkaProducer<String, String>,
    env: Environment
) {

    private val logger = LoggerFactory.getLogger(RepubliseringService::class.java)
    private val topic = env.hentRequiredEnv("KAFKA_RAPID_TOPIC")
    private val ignorerteMeldinger = Ignorer.ignorerteMeldinger

    internal fun republiserGamleUarkiverteMeldinger() {
        inFlightRepository.hentAlleInFlights(Duration.ofMinutes(RYDD_MELDINGER_ELDRE_ENN_MINUTTER), MAX_ANTALL_Å_HENTE).forEach { inFlight ->
            håndter(
                behovssekvensId = inFlight.behovssekvensId,
                correlationId = inFlight.correlationId,
                behovssekvens = inFlight.behovssekvens) {
                when {
                    ignorerteMeldinger.containsKey(inFlight.behovssekvensId) -> {
                        logger.warn("Sletter behovssekvens: ${ignorerteMeldinger[inFlight.behovssekvensId]}")
                        inFlightRepository.slett(inFlight.behovssekvensId)
                    }
                    arkivRepository.hentArkivMedId(inFlight.behovssekvensId).isEmpty() -> {
                        logger.info("Republiserer behovssekvens")
                        kafkaProducer.send(ProducerRecord(topic, inFlight.behovssekvensId, inFlight.behovssekvens))
                    }
                    else -> {
                        logger.warn("Sletter behovssekvens som allerede er arkivert")
                        inFlightRepository.slett(inFlight.behovssekvensId)
                    }
                }
            }
        }
    }

    internal companion object {
        internal const val RYDD_MELDINGER_ELDRE_ENN_MINUTTER = 30L
        internal const val MAX_ANTALL_Å_HENTE = 20
    }
}
