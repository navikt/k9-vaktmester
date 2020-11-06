package no.nav.k9.vaktmester.db

import com.zaxxer.hikari.HikariConfig
import no.nav.k9.config.Environment
import no.nav.k9.config.hentRequiredEnv
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import javax.sql.DataSource
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil.createHikariDataSourceWithVaultIntegration as createDataSource

internal class DataSourceBuilder(private val env: Environment) {

    private val hikariConfig = HikariConfig().apply {
        jdbcUrl = String.format(
            "jdbc:postgresql://%s:%s/%s",
            env.hentRequiredEnv("DATABASE_HOST"),
            env.hentRequiredEnv("DATABASE_PORT"),
            env.hentRequiredEnv("DATABASE_DATABASE")
        )
        maximumPoolSize = 3
        minimumIdle = 1
        idleTimeout = 10001
        connectionTimeout = 1000
        maxLifetime = 30001
    }

    private fun getDataSource(role: Role = Role.User) =
        createDataSource(
            hikariConfig,
            env.hentRequiredEnv("VAULT_MOUNT_PATH"),
            role.asRole(env.hentRequiredEnv("DATABASE_DATABASE"))
        )

    internal fun build(): DataSource = kotlin.runCatching {
        getDataSource()
    }.fold(
        onSuccess = { it },
        onFailure = { cause ->
            "Feil ved opprettelse av DataSource".let { error ->
                secureLogger.error(error, cause)
                throw IllegalStateException("$error. Se secure logs for detaljer")
            }
        }
    )

    enum class Role {
        Admin, User, ReadOnly;

        fun asRole(databaseName: String) = "$databaseName-${name.toLowerCase()}"
    }

    private companion object {
        private val secureLogger = LoggerFactory.getLogger("tjenestekall")
    }
}

internal fun DataSource.migrate() {
    Flyway.configure()
        .dataSource(this)
        .load()
        .migrate()
}
