package no.nav.k9.testutils

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import javax.sql.DataSource

fun testDataSource(embeddedPostgres: EmbeddedPostgres): HikariDataSource {
    val hikariConfig = HikariConfig().apply {
        this.jdbcUrl = embeddedPostgres.getJdbcUrl("postgres", "postgres")
        maximumPoolSize = 3
        minimumIdle = 1
        idleTimeout = 10001
        connectionTimeout = 1000
        maxLifetime = 30001
    }
    return HikariDataSource(hikariConfig)
}

internal fun DataSource.cleanAndMigrate() {
    Flyway
        .configure()
        .dataSource(this)
        .load()
        .also {
            it.clean()
            it.migrate()
        }
}
