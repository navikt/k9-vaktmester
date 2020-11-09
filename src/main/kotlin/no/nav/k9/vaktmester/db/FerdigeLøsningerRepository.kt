package no.nav.k9.vaktmester.db

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.helse.dusseldorf.ktor.health.HealthCheck
import no.nav.helse.dusseldorf.ktor.health.Healthy
import no.nav.helse.dusseldorf.ktor.health.UnHealthy
import javax.sql.DataSource

internal class FerdigeLøsningerRepository(
    private val dataSource: DataSource
) : HealthCheck {
    private val healthQuery = queryOf("SELECT 1").asExecute

    override suspend fun check() = kotlin.runCatching {
        using(sessionOf(dataSource)) { session ->
            session.run(healthQuery)
        }
    }.fold(
        onSuccess = { Healthy(FerdigeLøsningerRepository::class.java.simpleName, "OK") },
        onFailure = { UnHealthy(FerdigeLøsningerRepository::class.java.simpleName, "Feil: ${it.message}") }
    )
}
