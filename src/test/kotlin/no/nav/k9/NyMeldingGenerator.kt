package no.nav.k9

import de.huxhorn.sulky.ulid.ULID
import no.nav.k9.rapid.behov.Behov
import no.nav.k9.rapid.behov.Behovssekvens
import java.util.*

internal object NyMeldingGenerator {
    @JvmStatic
    fun main(args: Array<String>) {
        val behovssekvens = Behovssekvens(
            id = ULID().nextULID(),
            correlationId = "${UUID.randomUUID()}",
            behov = arrayOf(Behov(navn = "Behov", input = mapOf(
                "versjon" to "1.0.0"
            ))
        ))

        println(behovssekvens.keyValue.second)
    }
}