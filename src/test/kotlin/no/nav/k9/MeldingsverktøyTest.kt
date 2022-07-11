package no.nav.k9

import de.huxhorn.sulky.ulid.ULID
import no.nav.k9.vaktmester.fjernLøsningPå
import no.nav.k9.vaktmester.river.behovssekvensSomMeldingsinformasjon
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.time.ZonedDateTime
import no.nav.k9.vaktmester.fjernBehov
import no.nav.k9.vaktmester.leggTilLøsning
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows

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
                "@behovssekvensId": "1",
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
                "@behovssekvensId": "1",
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
    fun `Matching på meldinger basert på @behovssekvensId og @sistEndret`() {
        val sistEndret = "2020-11-21T18:00:15.293Z"
        val id = ULID().nextULID()

        val behovssekvens = """
            {
                "@behovssekvensId": "$id",
                "@correlationId": "2",
                "@sistEndret": "$sistEndret",
                "@behovsrekkefølge": ["Test"],
                "@behov": {
                    "Test": {}
                }
            }
        """.trimIndent()

        val meldingsinformasjon = behovssekvens.behovssekvensSomMeldingsinformasjon()

        assertTrue(meldingsinformasjon.behovssekvensId == id && meldingsinformasjon.sistEndret == ZonedDateTime.parse(sistEndret))
        assertFalse(meldingsinformasjon.behovssekvensId == id && meldingsinformasjon.sistEndret == ZonedDateTime.parse(sistEndret).plusSeconds(1))
        assertFalse(meldingsinformasjon.behovssekvensId == "2" && meldingsinformasjon.sistEndret == ZonedDateTime.parse(sistEndret))
    }

    @Test
    fun `Matching på meldinger basert på @id og @sistEndret`() {
        val sistEndret = "2020-11-21T18:00:15.293Z"
        val id = ULID().nextULID()

        val behovssekvens = """
            {
                "@behovssekvensId": "$id",
                "@correlationId": "2",
                "@sistEndret": "$sistEndret",
                "@behovsrekkefølge": ["Test"],
                "@behov": {
                    "Test": {}
                }
            }
        """.trimIndent()

        val meldingsinformasjon = behovssekvens.behovssekvensSomMeldingsinformasjon()

        assertTrue(meldingsinformasjon.behovssekvensId == id && meldingsinformasjon.sistEndret == ZonedDateTime.parse(sistEndret))
        assertFalse(meldingsinformasjon.behovssekvensId == id && meldingsinformasjon.sistEndret == ZonedDateTime.parse(sistEndret).plusSeconds(1))
        assertFalse(meldingsinformasjon.behovssekvensId == "2" && meldingsinformasjon.sistEndret == ZonedDateTime.parse(sistEndret))
    }

    @Test
    fun `Legge til løsning`() {
        val før = """
            {
                "@behovssekvensId": "1",
                "@correlationId": "2",
                "@behovsrekkefølge": ["Test", "Test2"],
                "@behov": {
                    "Test": {},
                    "Test2": {}
                },
                "@løsninger": {
                    "Test": {}
                }
            }
        """.trimIndent()

        assertThrows<IllegalArgumentException> {
            før.leggTilLøsning("Test", "Allerede løst")
        }

        assertThrows<IllegalArgumentException> {
            før.leggTilLøsning("404", "Inneholder ikke behovet")
        }

        val forventetEtter = """
            {
                "@behovssekvensId": "1",
                "@correlationId": "2",
                "@behovsrekkefølge": ["Test", "Test2"],
                "@behov": {
                    "Test": {},
                    "Test2": {}
                },
                "@løsninger": {
                    "Test": {},
                    "Test2": {
                        "løsningsbeskrivelse": "Løst i testen"
                    }
                }
            }
        """.trimIndent()

        val etter = før.leggTilLøsning("Test2", "Løst i testen")

        JSONAssert.assertEquals(forventetEtter, etter, true)
    }
}