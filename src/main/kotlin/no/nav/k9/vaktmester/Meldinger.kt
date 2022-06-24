package no.nav.k9.vaktmester

import no.nav.k9.rapid.behov.Behovsformat
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.lang.Thread.currentThread
import java.time.ZonedDateTime

internal object Meldinger {
    private val logger = LoggerFactory.getLogger(Meldinger::class.java)
    private val filnavn = System.getenv("MELDINGER_FILNAVN")

    @JvmStatic
    internal val ignorerteMeldinger : Map<String, String> = when (filnavn) {
        null -> emptyMap()
        else -> JSONObject("ignorer".resourcePath().fraResources()).toMap()
            .mapValues { it.value.toString() }.also {
                logger.info("Ignorerer ${it.size} meldinger")
            }
    }

    @JvmStatic
    internal val behovPåVent : Set<String> = when (filnavn) {
        null -> emptySet()
        else -> JSONArray("behovPåVent".resourcePath().fraResources()).map {
            it.toString()
        }.toSet().also {
            logger.info("Behov satt på vent $it")
        }
    }

    @JvmStatic
    internal val fjernLøsning : Set<FjernLøsning> = when (filnavn) {
        null -> emptySet()
        else -> JSONArray("fjernLøsning".resourcePath().fraResources())
            .map { it as JSONObject }
            .map { FjernLøsning(
                id = it.behovssekvensId(),
                sistEndret = ZonedDateTime.parse(it.getString("@sistEndret")),
                løsning = it.getString("løsning")!!
            )}
            .toSet().also {
                logger.info("Fjerner løsning på ${it.size} meldinger")
            }
    }

    @JvmStatic
    internal val fjernBehov : Set<FjernBehov> = when (filnavn) {
        null -> emptySet()
        else -> JSONArray("fjernBehov".resourcePath().fraResources())
                .map { it as JSONObject }
                .map { FjernBehov(
                        id = it.behovssekvensId(),
                        sistEndret = ZonedDateTime.parse(it.getString("@sistEndret")),
                        behov = it.getString("behov")!!
                )}
                .toSet().also {
                    logger.info("Fjerner behov på ${it.size} meldinger")
                }
    }

    @JvmStatic
    internal val leggTilLøsning : Set<LeggTilLøsning> = when (filnavn) {
        null -> emptySet()
        else -> JSONArray("leggTilLøsning".resourcePath().fraResources())
            .map { it as JSONObject }
            .map { LeggTilLøsning(
                id = it.behovssekvensId(),
                sistEndret = ZonedDateTime.parse(it.getString("@sistEndret")),
                løsningsbeskrivelse = it.getString("løsningsbeskrivelse")!!,
                behov = it.getString("behov")!!
            )}
            .toSet().also {
                logger.info("Legger til løsning for ${it.size} meldinger")
            }
    }

    @JvmStatic
    internal val nyeMeldinger : Set<NyMelding> = when (filnavn) {
        null -> emptySet()
        else -> JSONArray("nyeMeldinger".resourcePath().fraResources())
            .map { it as JSONObject }
            .map { NyMelding(
                id = it.behovssekvensId(),
                sistEndret = ZonedDateTime.parse(it.getString("@sistEndret")),
                correlationId = it.getString("@correlationId")!!,
                behovssekvens = it.toString()
            )}
            .toSet()
            .also {
                logger.info("Publiserer ${it.size} nye meldinger")
            }
    }

    private fun String.resourcePath() = "meldinger/$this/$filnavn"

    private fun String.fraResources() = requireNotNull(currentThread().contextClassLoader.getResource(this)) {
        "Finner ikke JSON-fil på resource path '$this'"
    }.readText(charset = Charsets.UTF_8)

    private fun JSONObject.behovssekvensId() = when(has(Behovsformat.BehovssekvensId)) {
        true -> getString(Behovsformat.BehovssekvensId)!!
        false -> getString(Behovsformat.Id)!!
    }

    internal interface MeldingId {
        val id: String
        val sistEndret: ZonedDateTime
    }

    internal data class FjernLøsning(
        override val id: String,
        override val sistEndret: ZonedDateTime,
        internal val løsning: String) : MeldingId

    internal data class FjernBehov(
        override val id: String,
        override val sistEndret: ZonedDateTime,
        internal val behov: String) : MeldingId

    internal data class LeggTilLøsning(
        override val id: String,
        override val sistEndret: ZonedDateTime,
        internal val behov: String,
        internal val løsningsbeskrivelse: String) : MeldingId

    internal data class NyMelding(
        override val id: String,
        override val sistEndret: ZonedDateTime,
        internal val correlationId: String,
        internal val behovssekvens: String) : MeldingId {
            internal fun erAktuell() = ZonedDateTime.now().minusHours(6).isBefore(sistEndret)
        }
}