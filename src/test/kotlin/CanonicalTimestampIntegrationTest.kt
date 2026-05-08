package com.example.mutlabocnotes

import com.example.mutlabocnotes.auth.AuthRepository
import com.example.mutlabocnotes.database.DatabaseFactory
import com.example.mutlabocnotes.homecards.HomeCardUpsertRequestDto
import com.example.mutlabocnotes.homecards.HomeCardsRepository
import com.example.mutlabocnotes.notes.CreateNoteRequestDto
import com.example.mutlabocnotes.notes.NoteCategory
import com.example.mutlabocnotes.notes.NotesRepository
import com.example.mutlabocnotes.notes.UpdateNoteRequestDto
import io.ktor.server.config.MapApplicationConfig
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.runBlocking
import org.junit.AssumptionViolatedException
import org.junit.BeforeClass
import org.testcontainers.containers.PostgreSQLContainer

@OptIn(ExperimentalUuidApi::class)
class CanonicalTimestampIntegrationTest {

    @BeforeTest
    fun resetRepositories() {
        notesRepository = NotesRepository()
        homeCardsRepository = HomeCardsRepository()
        authRepository = AuthRepository()
    }

    @Test
    fun noteCreateAndUpdateReturnCanonicalTimestamps() = runBlocking {
        val userId = createUser()

        val created = notesRepository.create(
            userId = userId,
            request = CreateNoteRequestDto(
                title = "Created note",
                content = "Server timestamps",
                category = NoteCategory.NOTES,
                coinCount = 3
            )
        )

        Thread.sleep(10)

        val updated = notesRepository.update(
            userId = userId,
            noteId = created.id,
            request = UpdateNoteRequestDto(
                title = "Updated note",
                content = "Still canonical",
                category = NoteCategory.NOTES,
                coinCount = 4
            )
        ) ?: error("Expected note update to return a note")

        assertTrue(created.createdAt > 0)
        assertTrue(created.updatedAt > 0)
        assertEquals(created.createdAt, updated.createdAt)
        assertTrue(updated.updatedAt >= created.updatedAt)
    }

    @Test
    fun homeCardCreateIgnoresForgedTimestamps() = runBlocking {
        val userId = UUID.fromString(createUser().toString())
        val forgedCreatedAt = 946684800000L
        val forgedUpdatedAt = 4102444800000L
        val beforeCreate = System.currentTimeMillis() - 5_000

        val created = homeCardsRepository.createForUser(
            userId = userId,
            request = HomeCardUpsertRequestDto(
                title = "Meter",
                section = "METERS",
                fields = emptyList(),
                note = "",
                links = emptyList(),
                createdAt = forgedCreatedAt,
                updatedAt = forgedUpdatedAt
            )
        )

        val afterCreate = System.currentTimeMillis() + 5_000

        assertNotEquals(forgedCreatedAt, created.createdAt)
        assertNotEquals(forgedUpdatedAt, created.updatedAt)
        assertTrue(created.createdAt in beforeCreate..afterCreate)
        assertTrue(created.updatedAt in beforeCreate..afterCreate)
    }

    @Test
    fun homeCardUpdatePreservesCreatedAtAndIgnoresForgedTimestamps() = runBlocking {
        val userId = UUID.fromString(createUser().toString())
        val created = homeCardsRepository.createForUser(
            userId = userId,
            request = HomeCardUpsertRequestDto(
                title = "Original",
                section = "OTHER",
                fields = emptyList(),
                note = "",
                links = emptyList(),
                createdAt = 946684800000L,
                updatedAt = 946684800000L
            )
        )

        Thread.sleep(10)

        val updated = homeCardsRepository.updateForUser(
            userId = userId,
            cardId = created.id,
            request = HomeCardUpsertRequestDto(
                title = "Updated",
                section = "DOCUMENTS",
                fields = emptyList(),
                note = "Changed",
                links = emptyList(),
                createdAt = 4102444800000L,
                updatedAt = 946684800000L
            )
        ) ?: error("Expected home-card update to return a card")

        assertEquals(created.createdAt, updated.createdAt)
        assertNotEquals(4102444800000L, updated.createdAt)
        assertNotEquals(946684800000L, updated.updatedAt)
        assertTrue(updated.updatedAt >= created.updatedAt)
    }

    private suspend fun createUser() =
        authRepository.createLocalUser(
            email = "user-${UUID.randomUUID()}@example.com",
            passwordHash = "hash",
            displayName = null
        ).id

    companion object {
        private val postgres = PostgreSQLContainer("postgres:16-alpine")

        private lateinit var notesRepository: NotesRepository
        private lateinit var homeCardsRepository: HomeCardsRepository
        private lateinit var authRepository: AuthRepository

        @JvmStatic
        @BeforeClass
        fun initializeDatabase() {
            try {
                postgres.start()
            } catch (e: IllegalStateException) {
                throw AssumptionViolatedException("Docker is required for PostgreSQL integration tests", e)
            }

            val config = MapApplicationConfig(
                "db.driverClassName" to postgres.driverClassName,
                "db.jdbcUrl" to postgres.jdbcUrl,
                "db.username" to postgres.username,
                "db.password" to postgres.password,
                "db.maximumPoolSize" to "3",
                "db.minimumIdle" to "1",
                "db.connectionTimeoutMs" to "10000",
                "db.idleTimeoutMs" to "600000",
                "db.maxLifetimeMs" to "1800000",
                "db.autoCommit" to "false",
                "flyway.enabled" to "true",
                "flyway.locations" to "classpath:db/migration",
                "flyway.validateMigrationNaming" to "true"
            )

            FlywayRunner.migrate(config)
            DatabaseFactory.init(config)
        }
    }
}
