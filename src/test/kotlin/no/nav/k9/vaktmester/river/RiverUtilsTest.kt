package no.nav.k9.vaktmester.river

import no.nav.k9.rapid.behov.Behovsformat
import no.nav.k9.vaktmester.db.InFlight
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime

internal class RiverUtilsTest {

    @Test
    fun `finner første uløste behov`() {
        val behov1 = "behov1"
        val behov2 = "behov2"
        val behov3 = "behov3"

        val inFlight = lagInFlight(
            løsninger = mapOf(behov1 to "{}"),
            behov1, behov2, behov3
        )

        assertThat(inFlight.somMeldingsinformasjon().uløstBehov()).isEqualTo(behov2)
    }

    @Test
    fun `ingen løste behov gir første i rekkefølgen`() {
        val behov1 = "behov1"
        val behov2 = "behov2"
        val behov3 = "behov3"

        val inFlight = lagInFlight(
            løsninger = emptyMap(),
            behov1, behov2, behov3
        )

        assertThat(inFlight.somMeldingsinformasjon().uløstBehov()).isEqualTo(behov1)
    }

    @Test
    fun `alle behov er løst gir na`() {
        val behov1 = "behov1"
        val behov2 = "behov2"
        val behov3 = "behov3"

        val inFlight = lagInFlight(
            løsninger = mapOf(
                behov1 to "{}",
                behov2 to "{}",
                behov3 to "{}"
            ),
            behov1, behov2, behov3
        )

        assertThat(inFlight.somMeldingsinformasjon().uløstBehov()).isEqualTo("n/a")
    }

    @Test
    fun `ingen løsninger gir første behovet`() {
        assertThat(lagInFlight(
            løsninger = null,
            "behov1"
        ).somMeldingsinformasjon().uløstBehov()).isEqualTo("behov1")

        assertThat(lagInFlight(
            løsninger = null,
            "behov1", "behov2"
        ).somMeldingsinformasjon().uløstBehov()).isEqualTo("behov1")
    }

    private fun lagInFlight(løsninger: Map<String, String?>?, vararg behov: String): InFlight {
        val id = "01EKW89QKK5YZ0XW2QQYS0TB8D"
        val behovssekvens = nyBehovssekvens(
            id = id,
            behov = emptyMap(),
            løsninger = løsninger
        )
        behovssekvens[Behovsformat.Behovsrekkefølge] = behov.asList()
        return InFlight(
            behovssekvensId = id,
            behovssekvens = behovssekvens.toJson(),
            sistEndret = ZonedDateTime.now(),
            correlationId = "123",
            opprettettidspunkt = ZonedDateTime.now()
        )
    }
}
