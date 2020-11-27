package no.nav.k9

import no.nav.k9.vaktmester.fjernLøsningPå
import no.nav.k9.vaktmester.river.behovssekvensSomMeldingsinformasjon
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.time.ZonedDateTime
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import no.nav.k9.vaktmester.fjernBehov

internal class MeldingsverktøyTest {

    @Test
    fun `Fjern løsning`() {
        val før = """
            {
                "@løsninger": {
                    "en": {},
                    "to": {}
                }
            }
        """.trimIndent()

        val forventetEtter = """
            {
                "@løsninger": {
                    "en": {}
                }
            }
        """.trimIndent()

        val etter = før.fjernLøsningPå("to")

        JSONAssert.assertEquals(forventetEtter, etter, true)
    }

    @Test
    fun `Fjern behov`() {
        val før = """
            {
                "@id": "1",
                "@correlationId": "2",
                "@behovsrekkefølge": ["Test", "Test2"],
                "@behov": {
                    "Test": {},
                    "Test2": {}
                }
            }
        """.trimIndent()

        val forventetEtter = """
            {
                "@id": "1",
                "@correlationId": "2",
                "@behovsrekkefølge": ["Test2"],
                "@behov": {
                    "Test2": {}
                }
            }
        """.trimIndent()

        val etter = før.fjernBehov("Test")

        JSONAssert.assertEquals(forventetEtter, etter, true)
    }

    @Test
    fun `Matching på meldinger basert på @id og @sistEndret`() {
        val sistEndret = "2020-11-21T18:00:15.293Z"

        val behovssekvens = """
            {
                "@id": "1",
                "@correlationId": "2",
                "@sistEndret": "$sistEndret",
                "@behovsrekkefølge": ["Test"],
                "@behov": {
                    "Test": {}
                }
            }
        """.trimIndent()

        val meldingsinformasjon = behovssekvens.behovssekvensSomMeldingsinformasjon()

        assertTrue(meldingsinformasjon.behovssekvensId == "1" && meldingsinformasjon.sistEndret == ZonedDateTime.parse(sistEndret))
        assertFalse(meldingsinformasjon.behovssekvensId == "1" && meldingsinformasjon.sistEndret == ZonedDateTime.parse(sistEndret).plusSeconds(1))
        assertFalse(meldingsinformasjon.behovssekvensId == "2" && meldingsinformasjon.sistEndret == ZonedDateTime.parse(sistEndret))
    }
}