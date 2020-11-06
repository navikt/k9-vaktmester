package no.nav.k9

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import io.ktor.application.Application
import io.ktor.routing.routing
import no.nav.helse.dusseldorf.ktor.health.HealthRoute
import no.nav.helse.dusseldorf.ktor.health.HealthService
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.k9.config.Environment
import no.nav.k9.vaktmester.db.DataSourceBuilder
import no.nav.k9.vaktmester.db.InMemoryDb
import no.nav.k9.vaktmester.db.migrate
import javax.sql.DataSource

fun main() {
    // TODO: Fjern alt in memory stuff
    val inMemoryDb = InMemoryDb().build()
    val env = System.getenv().toMutableMap()
    env["DATABASE_HOST"] = "localhost"
    env["DATABASE_PORT"] = "${inMemoryDb.port}"
    env["DATABASE_DATABASE"] = "postgres"
    env["DATABASE_USERNAME"] = "postgres"
    env["DATABASE_PASSWORD"] = "postgres"
    env["DATABASE_VAULT_MOUNT_PATH"] = "test/postgres"

    val applicationContext = ApplicationContext.Builder(
        env = env,
        inMemoryDb = inMemoryDb
    ).build()
    RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(applicationContext.env))
        .withKtorModule { k9Vaktmester(applicationContext) }
        .build()
        .apply { registerApplicationContext(applicationContext) }
        .start()
}

internal fun RapidsConnection.registerApplicationContext(applicationContext: ApplicationContext) {
    register(object : RapidsConnection.StatusListener {
        override fun onStartup(rapidsConnection: RapidsConnection) {
            applicationContext.start()
        }
        override fun onShutdown(rapidsConnection: RapidsConnection) {
            applicationContext.stop()
        }
    })
}

internal fun Application.k9Vaktmester(applicationContext: ApplicationContext) {
    routing {
        HealthRoute(healthService = applicationContext.healthService)
    }
}

internal class ApplicationContext(
    val env: Environment,
    private val inMemoryDb: EmbeddedPostgres,
    val dataSource: DataSource,
    val healthService: HealthService,
) {

    internal fun start() {
        dataSource.migrate()
    }
    internal fun stop() {
        inMemoryDb.postgresDatabase.connection.close()
        inMemoryDb.close()
    }

    internal class Builder(
        var env: Environment? = null,
        val inMemoryDb: EmbeddedPostgres,
        var dataSource: DataSource? = null,
    ) {
        internal fun build(): ApplicationContext {
            val benyttetEnv = env ?: System.getenv()

            val benyttetDataSource = dataSource ?: DataSourceBuilder(benyttetEnv).build()

            return ApplicationContext(
                env = benyttetEnv,
                inMemoryDb = inMemoryDb,
                dataSource = benyttetDataSource,
                healthService = HealthService(
                    healthChecks = emptySet()
                )
            )
        }
    }
}
