package no.nav.k9.vaktmester

import no.nav.k9.rapid.behov.Behovsformat.Behov
import no.nav.k9.rapid.behov.Behovsformat.Behovsrekkefølge
import no.nav.k9.vaktmester.river.Løsninger
import org.json.JSONArray
import org.json.JSONObject

internal fun String.fjernLøsningPå(løsning: String) : String {
    val json = JSONObject(this)
    json.getJSONObject(Løsninger).remove(løsning)
    return json.toString()
}

internal fun String.fjernBehovPå(behov: String) : String {
    val json = JSONObject(this)
    json.getJSONObject(Behov).remove(behov)
    json.getJSONArray(Behovsrekkefølge)
    return json.toString()
}