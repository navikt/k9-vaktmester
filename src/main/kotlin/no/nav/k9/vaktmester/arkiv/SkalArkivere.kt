package no.nav.k9.vaktmester.arkiv

import com.fasterxml.jackson.databind.node.TextNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.k9.rapid.behov.Behovsformat
import no.nav.k9.rapid.behov.Behovssekvens
import java.lang.IllegalStateException

internal const val Løsninger = "@løsninger"
private val StøttetTypeOgVersjon = Pair(
    TextNode(Behovsformat.BehovssekvensType), TextNode(Behovsformat.BehovssekvensVersjon)
)

internal fun erBehovssekvens(jsonMessage: JsonMessage): Boolean {
    jsonMessage.interestedIn(Behovsformat.Type, Behovsformat.Versjon)
    val typeOgVersjon = Pair(
        jsonMessage[Behovsformat.Type], jsonMessage[Behovsformat.Versjon]
    )

    return if (typeOgVersjon == StøttetTypeOgVersjon) {
        Behovssekvens.demandedKeys.forEach { jsonMessage.demandKey(it) }
        Behovssekvens.demandedValues.forEach { (key, value) -> jsonMessage.demandValue(key, value) }
        jsonMessage.interestedIn(Løsninger, Behovsformat.SistEndret)
        true
    } else {
        jsonMessage.requireValue(Behovsformat.Type, StøttetTypeOgVersjon.first.asText())
        jsonMessage.requireValue(Behovsformat.Versjon, StøttetTypeOgVersjon.first.asText())
        false
    }
}

internal fun JsonMessage.arkiverHvisLøsningerOgBehovErLike() {
    if (!erBehovssekvens(this)) return

    val behov = get(Behovsformat.Behov)

    behov.fieldNames().forEach {
        require("$Løsninger.$it") { node ->
            if (node.isMissingOrNull()) {
                throw IllegalStateException("Mangler løsning på behov: $it")
            }
        }
    }
}
