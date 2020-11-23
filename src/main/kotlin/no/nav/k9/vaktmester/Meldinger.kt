package no.nav.k9.vaktmester

import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.lang.Thread.currentThread

internal object Meldinger {
    private val logger = LoggerFactory.getLogger(Meldinger::class.java)
    private val filnavn = System.getenv("MELDINGER_FILNAVN")

    @JvmStatic
    internal val ignorerteMeldinger : Map<String, String> = when (filnavn) {
        null -> emptyMap()
        else -> JSONObject("meldinger/ignorer".resourcePath().fraResources()).toMap()
            .mapValues { it.value.toString() }.also {
                logger.info("Ignorerer ${it.size} meldinger")
            }
    }

    private fun String.resourcePath() = "meldinger/$this/$filnavn"

    private fun String.fraResources() = requireNotNull(currentThread().contextClassLoader.getResource(this)) {
        "Finner ikke JSON-fil med ignorerte meldinger på '$this'"
    }.readText(charset = Charsets.UTF_8)
}