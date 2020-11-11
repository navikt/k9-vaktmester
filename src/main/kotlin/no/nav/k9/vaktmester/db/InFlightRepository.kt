package no.nav.k9.vaktmester.db

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.helse.dusseldorf.ktor.health.HealthCheck
import no.nav.helse.dusseldorf.ktor.health.Healthy
import no.nav.helse.dusseldorf.ktor.health.UnHealthy
import java.sql.Timestamp
import java.time.ZonedDateTime
import javax.sql.DataSource

internal class InFlightRepository(
    private val dataSource: DataSource
) : HealthCheck {

    internal fun lagreInFlightBehov(behovsid: String, behovssekvens: String, sistEndret: ZonedDateTime): Boolean {
        val query = queryOf(LAGRE_BEHOV_QUERY, behovsid, behovssekvens, Timestamp.from(sistEndret.toInstant())).asUpdate
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
        private const val LAGRE_BEHOV_QUERY = "INSERT INTO IN_FLIGHT(BEHOVSSEKVENSID, BEHOVSSEKVENS, SIST_ENDRET) VALUES (?, to_json(?::json), ?) ON CONFLICT DO NOTHING"
        private const val HEALTH_QUERY = "SELECT 1"
    }
}
