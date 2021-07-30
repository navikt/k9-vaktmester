package no.nav.k9.apis

import no.nav.k9.vaktmester.Meldinger
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime

internal class NyMeldingTest {

    @Test
    fun `ser om melding fortsatt er aktuell`() {
        val nå = ZonedDateTime.now()
        assertTrue(nå.somNyMelding().erAktuell())
        assertTrue(nå.minusHours(5).minusMinutes(59).somNyMelding().erAktuell())
        assertFalse(nå.minusHours(6).somNyMelding().erAktuell())
        assertFalse(nå.minusDays(5).somNyMelding().erAktuell())
    }

    private companion object {
        private fun ZonedDateTime.somNyMelding() = Meldinger.NyMelding(
            id = "1",
            sistEndret = this,
            correlationId = "2",
            behovssekvens = "3"
        )
    }
}