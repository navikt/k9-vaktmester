package no.nav.k9.vaktmester.db

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.helse.dusseldorf.ktor.health.HealthCheck
import no.nav.helse.dusseldorf.ktor.health.Healthy
import no.nav.helse.dusseldorf.ktor.health.UnHealthy
import org.intellij.lang.annotations.Language
import java.sql.Timestamp
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
        private const val LAGRE_BEHOV_QUERY = """
            INSERT INTO IN_FLIGHT(BEHOVSSEKVENSID, BEHOVSSEKVENS, SIST_ENDRET, CORRELATION_ID)
            VALUES (:BEHOVSSEKVENSID, :BEHOVSSEKVENS ::jsonb, :SIST_ENDRET, :CORRELATION_ID)
            ON CONFLICT (BEHOVSSEKVENSID) DO UPDATE SET BEHOVSSEKVENS = :BEHOVSSEKVENS ::jsonb, SIST_ENDRET = :SIST_ENDRET
        """
        private const val HEALTH_QUERY = "SELECT 1"
    }
}
