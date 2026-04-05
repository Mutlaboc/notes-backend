package com.example.mutlabocnotes.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.ApplicationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory

// Синглтон с общими функциями и константами модуля.
object DatabaseFactory {

    private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)

    private lateinit var database: Database

    // Реализует шаг «init» в рамках текущего процесса.
    fun init(config: ApplicationConfig) {
        val hikariConfig = HikariConfig().apply {
            driverClassName = config.property("db.driverClassName").getString()
            jdbcUrl = config.property("db.jdbcUrl").getString()
            username = config.property("db.username").getString()
            password = config.property("db.password").getString()

            maximumPoolSize = config.property("db.maximumPoolSize").getString().toInt()
            minimumIdle = config.property("db.minimumIdle").getString().toInt()

            connectionTimeout = config.property("db.connectionTimeoutMs").getString().toLong()
            idleTimeout = config.property("db.idleTimeoutMs").getString().toLong()
            maxLifetime = config.property("db.maxLifetimeMs").getString().toLong()

            isAutoCommit = config.property("db.autoCommit").getString().toBoolean()

            transactionIsolation = "TRANSACTION_READ_COMMITTED"
            poolName = "notes-hikari-pool"
        }

        val dataSource = HikariDataSource(hikariConfig)
        database = Database.connect(dataSource)

        // Fail fast: сразу убеждаемся, что БД реально доступна
        transaction(database) {
            val result = exec("SELECT 1;") { rs ->
                rs.next()
                rs.getInt(1)
            }

            require(result == 1) {
                "Database test query failed: expected 1, got $result"
            }
        }

        logger.info("Database connection initialized successfully")
    }

    // Реализует шаг «test connection» в рамках текущего процесса.
    fun testConnection(): String {
        check(::database.isInitialized) {
            "DatabaseFactory is not initialized"
        }

        return transaction(database) {
            exec("SELECT current_database(), version();") { rs ->
                rs.next()
                val dbName = rs.getString(1)
                val version = rs.getString(2)
                "db=$dbName; version=$version"
            } ?: error("Database test query returned no rows")
        }
    }

    // Реализует шаг «db query» в рамках текущего процесса.
    suspend fun <T> dbQuery(block: () -> T): T =
        withContext(Dispatchers.IO) {
            transaction(database) {
                block()
            }
        }
}