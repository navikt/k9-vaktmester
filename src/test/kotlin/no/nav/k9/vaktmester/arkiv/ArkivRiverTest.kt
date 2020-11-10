package no.nav.k9.vaktmester.arkiv

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.k9.ApplicationContext
import no.nav.k9.rapid.behov.Behov
import no.nav.k9.rapid.behov.Behovssekvens
import no.nav.k9.registerApplicationContext
import no.nav.k9.testutils.ApplicationContextExtension
import no.nav.k9.testutils.cleanAndMigrate
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

@ExtendWith(ApplicationContextExtension::class)
internal class ArkivRiverTest(
    private val applicationContext: ApplicationContext
) {
    private var rapid = TestRapid().also {
        it.registerApplicationContext(applicationContext)
    }

    @BeforeEach
    fun reset() {
        applicationContext.dataSource.cleanAndMigrate()
        rapid.reset()
    }

    @Test
    internal fun `behandler behov med like løsninger som behov`() {
        val behov1 = "behov1"
        val behov2 = "behov2"
        val behov = mapOf(
            behov1 to "{}",
            behov2 to "{}"
        )
        val id = "01EKW89QKK5YZ0XW2QQYS0TB8D"
        val behovssekvens = nyBehovssekvens(
            id = id,
            behov = behov,
            løsninger = behov
        )

        rapid.sendTestMessage(behovssekvens)

        val arkiv = hentArkivMedId(id)
        JSONAssert.assertEquals(
            settJsonFeltTomt(behovssekvens, "system_read_count"),
            settJsonFeltTomt(arkiv!!, "system_read_count"),
            true
        )
    }

    private fun settJsonFeltTomt(json: String, feltnavn: String): String {
        val jsonMessage = JsonMessage(json, MessageProblems(""))
        jsonMessage[feltnavn] = ""
        return jsonMessage.toJson()
    }

    private fun hentArkivMedId(id: String): String? {
        return using(sessionOf(applicationContext.dataSource)) { session ->
            val query = queryOf("SELECT BEHOVSSEKVENS FROM ARKIV WHERE BEHOVSSEKVENSID = ?", id)
            return@using session.run(
                query.map { row ->
                    row.stringOrNull("BEHOVSSEKVENS")
                }.asSingle
            )
        }
    }

    internal companion object {
        fun løsningsJsonPointer(behov: String) = "/@løsninger/$behov"

        private fun nyBehovssekvens(
            id: String,
            behov: Map<String, String?>,
            løsninger: Map<String, String?>
        ): String {
            val sekvens = Behovssekvens(
                id = id,
                correlationId = UUID.randomUUID().toString(),
                behov = behov.entries.map {
                    Behov(
                        navn = it.key,
                        input = mapOf(
                            "hvasomhelst" to it.value
                        )
                    )
                }.toTypedArray()
            ).keyValue.second

            val jsonMessage = JsonMessage(sekvens, MessageProblems(""))

            jsonMessage[Løsninger] = løsninger
            return jsonMessage.toJson()
        }
    }
}
