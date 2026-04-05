package com.example.mutlabocnotes

import io.ktor.server.config.ApplicationConfig
import org.flywaydb.core.Flyway

// Запускает инфраструктурный процесс при старте приложения.
object FlywayRunner {

    // Реализует шаг «migrate» в рамках текущего процесса.
    fun migrate(config: ApplicationConfig) {
        val enabled = config.propertyOrNull("flyway.enabled")
            ?.getString()
            ?.toBooleanStrictOrNull()
            ?: false

        if (!enabled) return

        val jdbcUrl = config.property("db.jdbcUrl").getString()
        val username = config.property("db.username").getString()
        val password = config.property("db.password").getString()

        val locations = config.propertyOrNull("flyway.locations")
            ?.getList()
            ?.toTypedArray()
            ?: arrayOf("classpath:db/migration")

        val validateMigrationNaming = config.propertyOrNull("flyway.validateMigrationNaming")
            ?.getString()
            ?.toBooleanStrictOrNull()
            ?: true

        Flyway.configure()
            .dataSource(jdbcUrl, username, password)
            .locations(*locations)
            .validateMigrationNaming(validateMigrationNaming)
            .skipDefaultCallbacks(true)
            .sqlMigrationPrefix("V")
            .sqlMigrationSeparator("__")
            .sqlMigrationSuffixes(".sql")
            .load()
            .migrate()
    }

    // Реализует шаг «property or null» в рамках текущего процесса.
    private fun ApplicationConfig.propertyOrNull(path: String) =
        try {
            property(path)
        } catch (_: Exception) {
            null
        }
}