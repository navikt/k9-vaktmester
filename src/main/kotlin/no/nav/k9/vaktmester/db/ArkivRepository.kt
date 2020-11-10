package no.nav.k9.vaktmester.db

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.helse.dusseldorf.ktor.health.HealthCheck
import no.nav.helse.dusseldorf.ktor.health.Healthy
import no.nav.helse.dusseldorf.ktor.health.UnHealthy
import javax.sql.DataSource

internal class ArkivRepository(
    private val dataSource: DataSource
) : HealthCheck {

    internal fun arkiverBehovssekvens(behovssid: String, behovssekvens: String): Boolean {
        val query = queryOf(LAGRE_BEHOVSSEKSVENS_QUERY, behovssid, behovssekvens).asUpdate
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
        private const val LAGRE_BEHOVSSEKSVENS_QUERY =
            "INSERT INTO ARKIV(BEHOVSSEKVENSID, BEHOVSSEKVENS) VALUES (?, to_json(?::json)) ON CONFLICT DO NOTHING"
        private const val HEALTH_QUERY = "SELECT 1"
    }
}
