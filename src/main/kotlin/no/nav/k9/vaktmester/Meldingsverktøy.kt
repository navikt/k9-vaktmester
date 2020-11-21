package no.nav.k9.vaktmester

import no.nav.k9.vaktmester.river.Løsninger
import org.json.JSONObject

internal fun String.fjernLøsningPå(løsning: String) : String {
    val json = JSONObject(this)
    json.getJSONObject(Løsninger).remove(løsning)
    return json.toString()
}

internal fun String.skalFjerneLøsningPåHentPersonopplysninger() : Boolean {
    val json = JSONObject(this)
    return json.getString("@id") == "01EQNZ85BXJ5GBA5AGCDG3MSWY" &&
           json.getString("@sistEndret") == "2020-11-21T18:00:15.293Z"

}
