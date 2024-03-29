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
    env: Environment) {

    private val logger = LoggerFactory.getLogger(RyddeService::class.java)
    private val topic = env.hentRequiredEnv("KAFKA_RAPID_TOPIC")

    private val ignorerteMeldinger = Meldinger.ignorerteMeldinger
    private val behovPåVent = Meldinger.behovPåVent
    private val fjernLøsning = Meldinger.fjernLøsning
    private val fjernBehov = Meldinger.fjernBehov
    private val leggTilLøsning = Meldinger.leggTilLøsning
    private val nyeMeldinger = Meldinger.nyeMeldinger

    internal fun rydd() {
        val skalRepublisereNå = arbeidstider.skalRepublisereNå()
        uløsteBehovGauge.clear()

        håndterNyeMeldinger()

        logger.info("Håndterer in flights")
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
                        val meldingsinformasjon = inFlight.somMeldingsinformasjon()
                        val uløstBehov = meldingsinformasjon.uløstBehov()

                        val påVent = håndterPåVent(
                            behovssekvensId = inFlight.behovssekvensId,
                            uløstBehov = uløstBehov
                        )

                        when (påVent) {
                            true -> {
                                //secureLogger.info("PacketPåVent=${inFlight.behovssekvens}")
                            }
                            false -> if (skalRepublisereNå) {
                                logger.info("Republiserer behovssekvens. Uløst behov $uløstBehov sist endret ${meldingsinformasjon.sistEndret}")
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

    private fun håndterPåVent(
        behovssekvensId: String,
        uløstBehov: String) : Boolean {
        var gaugeLabel = uløstBehov
        val behovetErPåVent = behovPåVent.contains(uløstBehov).also { if (it) {
            logger.info("Behovet $gaugeLabel er satt på vent.")
        }}
        val behovssekvensenErPåVent = behovPåVent.contains(behovssekvensId).also { if (it) {
            gaugeLabel = "${behovssekvensId}@${uløstBehov}"
            logger.info("Behovssekvens $gaugeLabel er satt på vent.")
        }}
        return (behovetErPåVent || behovssekvensenErPåVent).also { påVent ->
            uløsteBehovGauge.inc(gaugeLabel, "$påVent")
        }
    }

    private fun håndterNyeMeldinger() {
        logger.info("Håndterer nye meldinger")
        nyeMeldinger.filter { it.erAktuell() }.forEach { nyMelding ->
            håndter(
                behovssekvensId = nyMelding.id,
                correlationId = nyMelding.correlationId,
                behovssekvens = nyMelding.behovssekvens) {
                val erArkivert = arkivRepository.hentArkivMedId(nyMelding.id).isNotEmpty()
                val erInFlight = inFlightRepository.erInFlight(nyMelding.id)
                when {
                    !erArkivert && !erInFlight -> {
                        logger.info("Melding hverken arkivert eller in flight. Legges til i in flight.")
                        inFlightRepository.lagreInFlightBehov(
                            behovsid = nyMelding.id,
                            behovssekvens = nyMelding.behovssekvens,
                            correlationId = nyMelding.correlationId,
                            sistEndret = nyMelding.sistEndret
                        )
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

        val skalFjerneBehov = fjernBehov.firstOrNull {
            it.id == behovssekvensId && it.sistEndret.isEqual(sistEndret)
        }

        val skalLeggeTilLøsning = leggTilLøsning.firstOrNull {
            it.id == behovssekvensId && it.sistEndret.isEqual(sistEndret)
        }

        return when {
            skalFjerneLøsning != null -> behovssekvens.fjernLøsningPå(skalFjerneLøsning.løsning).also {
                logger.warn("Fjerner løsningen på ${skalFjerneLøsning.løsning}")
                secureLogger.warn("FjernetLøsningPacket=${it}")
            }
            skalFjerneBehov != null -> behovssekvens.fjernBehov(skalFjerneBehov.behov).also {
                logger.warn("Fjerner behov på ${skalFjerneBehov.behov}")
                secureLogger.warn("FjernetBehovPacket=${it}")
            }
            skalLeggeTilLøsning != null -> behovssekvens.leggTilLøsning(skalLeggeTilLøsning.behov, skalLeggeTilLøsning.løsningsbeskrivelse).also {
                logger.warn("Legger til løsning på ${skalLeggeTilLøsning.behov} med løsningsbeskrivelse ${skalLeggeTilLøsning.løsningsbeskrivelse}")
                secureLogger.warn("LagtTilLøsningPacket=${it}")
            }
            else -> behovssekvens
        }
    }

    private companion object {
        private val secureLogger = LoggerFactory.getLogger("tjenestekall")

        private val sistRepublisering = LateInitGauge(Gauge
            .build("sistRepublisering", "Sist tidspunkt en melding ble republisert")
        )

        private val uløsteBehovGauge = LateInitGauge(Gauge
            .build("uloesteBehov", "Hvilke behov er uløst akkurat nå?")
            .labelNames("uloesteBehov", "paaVent")
        )

        private const val RYDD_MELDINGER_ELDRE_ENN_MINUTTER = 30L
        private const val MAX_ANTALL_Å_HENTE = 50
    }
}
