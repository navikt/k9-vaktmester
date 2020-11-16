package no.nav.k9.vaktmester.db

import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.helse.dusseldorf.ktor.health.HealthCheck
import no.nav.helse.dusseldorf.ktor.health.Healthy
import no.nav.helse.dusseldorf.ktor.health.UnHealthy
import org.intellij.lang.annotations.Language
import java.time.ZonedDateTime
import javax.sql.DataSource

internal class ArkivRepository(
    private val dataSource: DataSource
) : HealthCheck {

    internal fun arkiverBehovssekvens(behovsid: String, behovssekvens: String, correlationId: String) {
        val lagreArkivQuery = queryOf(LAGRE_ARKIV_QUERY, behovsid, behovssekvens, correlationId).asUpdate
        val slettInFlightQuery = queryOf(InFlightRepository.SLETT_QUERY, behovsid).asUpdate

        using(sessionOf(dataSource)) { session ->
            session.transaction { transaction ->
                transaction.run(lagreArkivQuery)
                transaction.run(slettInFlightQuery)
            }
        }
    }

    internal fun hentArkivMedId(id: String): List<Arkiv> {
        return using(sessionOf(dataSource)) { session ->
            val query = queryOf(HENT_ARKIV_QUERY, id)
            return@using session.run(
                query.map { it.somArkiv() }.asList
            )
        }
    }

    override suspend fun check() = kotlin.runCatching {
        using(sessionOf(dataSource)) { session ->
            session.run(queryOf(HEALTH_QUERY).asExecute)
        }
    }.fold(
        onSuccess = { Healthy(ArkivRepository::class.java.simpleName, "OK") },
        onFailure = { UnHealthy(ArkivRepository::class.java.simpleName, "Feil: ${it.message}") }
    )

    private companion object {
        @Language("PostgreSQL")
        private const val LAGRE_ARKIV_QUERY = """
            INSERT INTO ARKIV(BEHOVSSEKVENSID, BEHOVSSEKVENS, CORRELATION_ID)
            VALUES (?, to_json(?::json), ?)
            ON CONFLICT DO NOTHING
        """
        @Language("PostgreSQL")
        private const val HENT_ARKIV_QUERY = """
            SELECT BEHOVSSEKVENS, BEHOVSSEKVENSID, ARKIVERINGSTIDSPUNKT, CORRELATION_ID
            FROM ARKIV
            WHERE BEHOVSSEKVENSID = ?
        """
        private const val HEALTH_QUERY = "SELECT 1"
    }

    private fun Row.somArkiv() = Arkiv(
        behovssekvens = string("behovssekvens"),
        behovssekvensid = string("behovssekvensid"),
        arkiveringstidspunkt = zonedDateTime("arkiveringstidspunkt"),
        correlationId = string("correlation_id")
    )
}

internal data class Arkiv(
    val behovssekvens: String,
    val behovssekvensid: String,
    val arkiveringstidspunkt: ZonedDateTime,
    val correlationId: String
)
