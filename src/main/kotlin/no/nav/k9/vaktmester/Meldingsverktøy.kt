package no.nav.k9.vaktmester

import no.nav.k9.vaktmester.river.Løsninger
import org.json.JSONObject

internal fun String.fjernLøsningPå(løsning: String) : String {
    val json = JSONObject(this)
    json.getJSONObject(Løsninger).remove(løsning)
    return json.toString()
}