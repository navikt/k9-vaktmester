package no.nav.k9.vaktmester.river

import no.nav.k9.rapid.behov.Behovsformat
import no.nav.k9.vaktmester.db.InFlight
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime

internal class RiverUtilsTest {

    @Test
    internal fun `finner første uløste behov`() {
        val id = "01EKW89QKK5YZ0XW2QQYS0TB8D"
        val behov1 = "behov1"
        val behov2 = "behov2"
        val behov3 = "behov3"
        val behovssekvens = nyBehovssekvens(
            id = id,
            behov = emptyMap(),
            løsninger = mapOf(behov1 to "{}")
        )
        behovssekvens[Behovsformat.Behovsrekkefølge] = listOf(behov1, behov2, behov3)
        val inFlight = InFlight(
            behovssekvensId = id,
            behovssekvens = behovssekvens.toJson(),
            sistEndret = ZonedDateTime.now(),
            correlationId = "123",
            opprettettidspunkt = ZonedDateTime.now()
        )

        assertThat(inFlight.uløstBehov()).isEqualTo(behov2)
    }
}
