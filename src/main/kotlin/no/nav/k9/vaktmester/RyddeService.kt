package no.nav.k9.vaktmester

import io.prometheus.client.Gauge
import no.nav.k9.rapid.river.Environment
import no.nav.k9.rapid.river.hentRequiredEnv
import no.nav.k9.vaktmester.db.ArkivRepository
import no.nav.k9.vaktmester.db.InFlightRepository
import no.nav.k9.vaktmester.river.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.ZonedDateTime

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
    private val behovPåVent = Meldinger.behovPåVent
    private val fjernLøsning = Meldinger.fjernLøsning

    internal fun rydd() {
        val skalRepublisereNå = arbeidstider.skalRepublisereNå()
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
                        val meldingsinformasjon = inFlight.somMeldingsinformasjon()
                        val uløstBehov = meldingsinformasjon.uløstBehov()
                        uløsteBehovGauge.labels(uløstBehov).safeInc()

                        when (behovPåVent.contains(uløstBehov)) {
                            true -> logger.info("Behovet $uløstBehov er satt på vent")
                            false -> if (skalRepublisereNå) {
                                logger.info("Republiserer behovssekvens. Uløst behov $uløstBehov")
                                republiser(
                                    behovssekvensId = inFlight.behovssekvensId,
                                    behovssekvens = inFlight.behovssekvens,
                                    sistEndret = meldingsinformasjon.sistEndret
                                )
                            }
                        }
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
        sistEndret: ZonedDateTime) {
        val behovssekvensSomSkalRepubliseres = utledBehovssekvensSomSkalRepubliseres(
            behovssekvensId = behovssekvensId,
            behovssekvens = behovssekvens,
            sistEndret = sistEndret
        )
        kafkaProducer.send(ProducerRecord(topic, behovssekvensId, behovssekvensSomSkalRepubliseres))
        sistRepublisering.setToCurrentTime()
    }

    private fun utledBehovssekvensSomSkalRepubliseres(
        behovssekvensId: String,
        behovssekvens: String,
        sistEndret: ZonedDateTime
    ) : String {
        val skalFjerneLøsning = fjernLøsning.firstOrNull {
            it.id == behovssekvensId && it.sistEndret.isEqual(sistEndret)
        }

        return when (skalFjerneLøsning) {
            null -> behovssekvens
            else -> behovssekvens.fjernLøsningPå(skalFjerneLøsning.løsning).also {
                logger.warn("Fjerner løsningen på ${skalFjerneLøsning.løsning}")
            }
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
