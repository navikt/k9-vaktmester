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

    internal fun republiserGamleUarkiverteMeldinger() {
        inFlightRepository.hentAlleInFlights(Duration.ofMinutes(RYDD_MELDINGER_ELDRE_ENN_MINUTTER), MAX_ANTALL_Å_HENTE).forEach { inFlight ->
            withMDC(behovssekvensId = inFlight.behovssekvensId, correlationId = inFlight.correlationId) {
                val arkiv = arkivRepository.hentArkivMedId(inFlight.behovssekvensId)

                if (arkiv.isEmpty()) {
                    logger.info("Republiserer behovssekvens")
                    kafkaProducer.send(ProducerRecord(topic, inFlight.behovssekvensId, inFlight.behovssekvens))
                } else {
                    logger.warn("Sletter behov som er blitt arkivert, men ikke slettet")
                    inFlightRepository.slett(inFlight.behovssekvensId)
                }
            }
        }
    }

    internal companion object {
        internal const val RYDD_MELDINGER_ELDRE_ENN_MINUTTER = 30L
        internal const val MAX_ANTALL_Å_HENTE = 20
    }
}
