package no.nav.k9.vaktmester.db

import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.helse.dusseldorf.ktor.health.HealthCheck
import no.nav.helse.dusseldorf.ktor.health.Healthy
import no.nav.helse.dusseldorf.ktor.health.UnHealthy
import org.intellij.lang.annotations.Language
import java.sql.Timestamp
import java.time.Duration
import java.time.ZonedDateTime
import javax.sql.DataSource

internal class InFlightRepository(
    private val dataSource: DataSource
) : HealthCheck {

    internal fun lagreInFlightBehov(behovsid: String, behovssekvens: String, sistEndret: ZonedDateTime, correlationId: String): Boolean {
        val query = queryOf(
            LAGRE_BEHOV_QUERY,
            mapOf(
                "BEHOVSSEKVENSID" to behovsid,
                "BEHOVSSEKVENS" to behovssekvens,
                "SIST_ENDRET" to Timestamp.from(sistEndret.toInstant()),
                "CORRELATION_ID" to correlationId
            )
        ).asUpdate
        return using(sessionOf(dataSource)) { session ->
            session.run(query)
        } != 0
    }

    internal fun hentAlleInFlights(minimumAge: Duration, maxAntall: Int): List<InFlight> {
        val query = queryOf(HENT_ALT_FØR, ZonedDateTime.now().minus(minimumAge), maxAntall)

        return using(sessionOf(dataSource)) { session ->
            session.run(
                query.map { it.somInFlight() }.asList
            )
        }
    }

    internal fun slett(id: String): Boolean {
        val query = queryOf(SLETT_QUERY, id)
        val antall = using(sessionOf(dataSource)) { session ->
            session.run(query.asUpdate)
        }
        return antall != 0
    }

    override suspend fun check() = kotlin.runCatching {
        using(sessionOf(dataSource)) { session ->
            session.run(queryOf(HEALTH_QUERY).asExecute)
        }
    }.fold(
        onSuccess = { Healthy(ArkivRepository::class.java.simpleName, "OK") },
        onFailure = { UnHealthy(ArkivRepository::class.java.simpleName, "Feil: ${it.message}") }
    )

    internal companion object {
        @Language("PostgreSQL")
        internal const val HENT_ALT_FØR = """
            SELECT BEHOVSSEKVENSID, BEHOVSSEKVENS, SIST_ENDRET, CORRELATION_ID, OPPRETTETTIDSPUNKT
            FROM IN_FLIGHT WHERE SIST_ENDRET < ?
            ORDER BY SIST_ENDRET
            LIMIT ?
        """
        @Language("PostgreSQL")
        internal const val LAGRE_BEHOV_QUERY = """
            INSERT INTO IN_FLIGHT(BEHOVSSEKVENSID, BEHOVSSEKVENS, SIST_ENDRET, CORRELATION_ID)
                VALUES (:BEHOVSSEKVENSID, :BEHOVSSEKVENS ::jsonb, :SIST_ENDRET, :CORRELATION_ID)
            ON CONFLICT (BEHOVSSEKVENSID)
                DO UPDATE SET BEHOVSSEKVENS = :BEHOVSSEKVENS ::jsonb, SIST_ENDRET = :SIST_ENDRET
                WHERE IN_FLIGHT.SIST_ENDRET < :SIST_ENDRET
        """
        @Language("PostgreSQL")
        internal const val SLETT_QUERY = """
            DELETE FROM IN_FLIGHT WHERE BEHOVSSEKVENSID = ?
        """
        private const val HEALTH_QUERY = "SELECT 1"
    }
}

internal fun Row.somInFlight() = InFlight(
    behovssekvensId = string("behovssekvensid"),
    behovssekvens = string("behovssekvens"),
    sistEndret = zonedDateTime("sist_endret"),
    correlationId = string("correlation_id"),
    opprettettidspunkt = zonedDateTime("opprettettidspunkt")
)

internal data class InFlight(
    val behovssekvensId: String,
    val behovssekvens: String,
    val sistEndret: ZonedDateTime,
    val correlationId: String,
    val opprettettidspunkt: ZonedDateTime
)
