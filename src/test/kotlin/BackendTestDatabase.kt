package com.example.mutlabocnotes

import com.example.mutlabocnotes.database.DatabaseFactory
import io.ktor.server.config.MapApplicationConfig
import org.junit.AssumptionViolatedException
import org.testcontainers.containers.PostgreSQLContainer

object BackendTestDatabase {
    private val externalJdbcUrl = env("TEST_DB_JDBC_URL") ?: env("DB_JDBC_URL")
    private val externalUsername = env("TEST_DB_USERNAME") ?: env("DB_USERNAME")
    private val externalPassword = env("TEST_DB_PASSWORD") ?: env("DB_PASSWORD")

    private val postgres: PostgreSQLContainer<*>? by lazy {
        if (externalJdbcUrl != null) {
            null
        } else {
            PostgreSQLContainer("postgres:16-alpine").also { it.start() }
        }
    }

    fun initialize(includeJwt: Boolean = true): MapApplicationConfig {
        val config = testApplicationConfig(includeJwt)
        FlywayRunner.migrate(config)
        DatabaseFactory.init(config)
        return config
    }

    fun testApplicationConfig(includeJwt: Boolean = true): MapApplicationConfig {
        val settings = databaseSettings()
        val entries = mutableMapOf(
            "db.driverClassName" to settings.driverClassName,
            "db.jdbcUrl" to settings.jdbcUrl,
            "db.username" to settings.username,
            "db.password" to settings.password,
            "db.maximumPoolSize" to "3",
            "db.minimumIdle" to "1",
            "db.connectionTimeoutMs" to "10000",
            "db.idleTimeoutMs" to "600000",
            "db.maxLifetimeMs" to "1800000",
            "db.autoCommit" to "false",
            "flyway.enabled" to "true",
            "flyway.locations.size" to "1",
            "flyway.locations.0" to "classpath:db/migration",
            "flyway.validateMigrationNaming" to "true"
        )

        if (includeJwt) {
            entries += mapOf(
                "jwt.issuer" to "test-issuer",
                "jwt.audience" to "test-audience",
                "jwt.realm" to "test-realm",
                "jwt.secret" to "test-secret-that-is-long-enough-for-hmac",
                "jwt.accessTokenTtlSeconds" to "1800",
                "jwt.refreshTokenTtlSeconds" to "1209600"
            )
        }

        return MapApplicationConfig(*entries.map { it.key to it.value }.toTypedArray())
    }

    private fun databaseSettings(): DatabaseSettings {
        if (externalJdbcUrl != null) {
            return DatabaseSettings(
                driverClassName = "org.postgresql.Driver",
                jdbcUrl = externalJdbcUrl,
                username = externalUsername
                    ?: throw AssumptionViolatedException("TEST_DB_USERNAME or DB_USERNAME is required"),
                password = externalPassword
                    ?: throw AssumptionViolatedException("TEST_DB_PASSWORD or DB_PASSWORD is required")
            )
        }

        val container = try {
            postgres
        } catch (e: IllegalStateException) {
            throw AssumptionViolatedException("Docker is required for PostgreSQL integration tests", e)
        } ?: throw AssumptionViolatedException("PostgreSQL test database is not available")

        return DatabaseSettings(
            driverClassName = container.driverClassName,
            jdbcUrl = container.jdbcUrl,
            username = container.username,
            password = container.password
        )
    }

    private fun env(name: String): String? =
        System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }

    private data class DatabaseSettings(
        val driverClassName: String,
        val jdbcUrl: String,
        val username: String,
        val password: String
    )
}
