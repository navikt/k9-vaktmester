package no.nav.k9.vaktmester.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.k9.config.Environment
import no.nav.k9.config.hentRequiredEnv
import org.flywaydb.core.Flyway
import javax.sql.DataSource
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil.createHikariDataSourceWithVaultIntegration as createDataSource

internal class DataSourceBuilder(private val env: Environment) {

    private val hikariConfig = HikariConfig().apply {
        jdbcUrl = String.format(
            "jdbc:postgresql://%s:%s/%s",
            env.hentRequiredEnv("DATABASE_HOST"),
            env.hentRequiredEnv("DATABASE_PORT").removePrefix("_"),
            env.hentRequiredEnv("DATABASE_NAME")
        )
        maximumPoolSize = 3
        minimumIdle = 1
        idleTimeout = 10001
        connectionTimeout = 1000
        maxLifetime = 30001
    }

    fun getDataSource(role: Role = Role.User): HikariDataSource =
        createDataSource(
            hikariConfig,
            env.hentRequiredEnv("DATABASE_VAULT_MOUNT_PATH"),
            role.asRole(env.hentRequiredEnv("DATABASE_NAME"))
        )

    fun migrateAsAdmin() {
        runMigration(getDataSource(Role.Admin), "SET ROLE \"${Role.Admin.asRole(env.hentRequiredEnv("DATABASE_NAME"))}\"")
    }

    private fun runMigration(dataSource: DataSource, initSql: String? = null) =
        Flyway.configure()
            .dataSource(dataSource)
            .initSql(initSql)
            .load()
            .migrate()

    enum class Role {
        Admin, User, ReadOnly;

        fun asRole(databaseName: String) = "$databaseName-${name.toLowerCase()}"
    }
}
