package no.nav.k9.vaktmester

import io.prometheus.client.Gauge
import no.nav.k9.rapid.river.Environment
import no.nav.k9.rapid.river.hentRequiredEnv
import no.nav.k9.vaktmester.db.ArkivRepository
import no.nav.k9.vaktmester.db.InFlightRepository
import no.nav.k9.vaktmester.river.uløstBehov
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.time.Duration

internal class RyddeService(
    private val inFlightRepository: InFlightRepository,
    private val arkivRepository: ArkivRepository,
    private val kafkaProducer: KafkaProducer<String, String>,
    private val arbeidstider: Arbeidstider,
    env: Environment
) {

    private val logger = LoggerFactory.getLogger(RyddeService::class.java)
    private val topic = env.hentRequiredEnv("KAFKA_RAPID_TOPIC")
    private val ignorerteMeldinger = Meldinger.ignorerteMeldinger

    internal fun rydd() {
        uløsteBehovGauge.clear()
        inFlightRepository.hentAlleInFlights(Duration.ofMinutes(RYDD_MELDINGER_ELDRE_ENN_MINUTTER), MAX_ANTALL_Å_HENTE).forEach { inFlight ->
            håndter(
                behovssekvensId = inFlight.behovssekvensId,
                correlationId = inFlight.correlationId,
                behovssekvens = inFlight.behovssekvens
            ) {
                when {
                    ignorerteMeldinger.containsKey(inFlight.behovssekvensId) -> {
                        logger.warn("Sletter behovssekvens: ${ignorerteMeldinger[inFlight.behovssekvensId]}")
                        inFlightRepository.slett(inFlight.behovssekvensId)
                    }
                    arkivRepository.hentArkivMedId(inFlight.behovssekvensId).isEmpty() -> {
                        val uløstBehov = inFlight.uløstBehov()
                        uløsteBehovGauge.labels(uløstBehov).safeInc()
                        republiser(
                            behovssekvensId = inFlight.behovssekvensId,
                            behovssekvens = inFlight.behovssekvens,
                            uløstBehov = uløstBehov!!
                        )
                    }
                    else -> {
                        logger.warn("Sletter behovssekvens som allerede er arkivert")
                        inFlightRepository.slett(inFlight.behovssekvensId)
                    }
                }
            }
        }
    }

    private fun republiser(
        behovssekvensId: String,
        behovssekvens: String,
        uløstBehov: String) {
        if (arbeidstider.skalRepublisereNå()) {
            logger.info("Republiserer behovssekvens. Uløst behov: $uløstBehov")
            kafkaProducer.send(ProducerRecord(topic, behovssekvensId, behovssekvens))
            sistRepublisering.setToCurrentTime()
        }
    }

    private companion object {
        private val sistRepublisering: Gauge = Gauge
            .build("sistRepublisering", "Sist tidspunkt en melding ble republisert")
            .register()

        private val uløsteBehovGauge: Gauge = Gauge
            .build("uloesteBehov", "Hvilke behov er uløst akkurat nå?")
            .labelNames("uloesteBehov")
            .register()

        private const val RYDD_MELDINGER_ELDRE_ENN_MINUTTER = 30L
        private const val MAX_ANTALL_Å_HENTE = 50
    }
}
