package no.nav.k9.vaktmester

import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.lang.Thread.currentThread

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
                id = it.getString("@id")!!,
                sistEndret = it.getString("@sistEndret")!!,
                løsning = it.getString("løsning")!!
            )}
            .toSet().also {
                logger.info("Fjerner løsning på ${it.size} meldinger")
            }
    }

    private fun String.resourcePath() = "meldinger/$this/$filnavn"

    private fun String.fraResources() = requireNotNull(currentThread().contextClassLoader.getResource(this)) {
        "Finner ikke JSON-fil på resource path '$this'"
    }.readText(charset = Charsets.UTF_8)

    internal interface MeldingId {
        val id: String
        val sistEndret: String
    }

    internal data class FjernLøsning(
        override val id: String,
        override val sistEndret: String,
        internal val løsning: String) : MeldingId
}