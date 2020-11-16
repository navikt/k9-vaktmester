package no.nav.k9.vaktmester

import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.lang.Thread.currentThread

internal object Ignorer {
    private val logger = LoggerFactory.getLogger(Ignorer::class.java)
    private val ignorerResourcePath = System.getenv("IGNORER_RESOURCE_PATH")
    private val ignorerJson = when (ignorerResourcePath) {
        null -> JSONObject()
        else -> JSONObject(ignorerResourcePath.fraResources())
    }

    @JvmStatic
    internal val ignorerteMeldinger : Map<String, String> = ignorerJson.toMap()
        .mapValues { it.toString() }.also {
            logger.info("Ignorerer ${it.size} meldinger")
        }

    private fun String.fraResources() = requireNotNull(currentThread().contextClassLoader.getResource(this)) {
        "Finner ikke JSON-fil med ignorerte meldinger på '$this'"
    }.readText(charset = Charsets.UTF_8)
}