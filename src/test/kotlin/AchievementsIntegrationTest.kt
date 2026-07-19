package com.example.mutlabocnotes

import com.example.mutlabocnotes.achievements.AchievementsSnapshotDto
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.decodeFromString

// Интеграционные тесты достижений: снапшот, merge метрик по max, идемпотентные анлоки.
class AchievementsIntegrationTest : BackendIntegrationTestSupport() {

    @Test
    fun snapshotIsEmptyForNewUser() = testApplication {
        startBackend()
        val user = registerUser()

        val response = client.get("/achievements") { bearerAuth(user.accessToken) }

        assertEquals(HttpStatusCode.OK, response.status)
        val snapshot = json.decodeFromString<AchievementsSnapshotDto>(response.bodyAsText())
        assertTrue(snapshot.metrics.isEmpty())
        assertTrue(snapshot.unlocks.isEmpty())
    }

    @Test
    fun achievementsRequireAuthentication() = testApplication {
        startBackend()

        assertEquals(HttpStatusCode.Unauthorized, client.get("/achievements").status)
    }

    @Test
    fun metricsMergeByMax_andRepeatedOperationIsNoOp() = testApplication {
        startBackend()
        val user = registerUser()

        val first = client.put("/achievements/metrics") {
            bearerAuth(user.accessToken)
            jsonBody("""{"operationId":"${UUID.randomUUID()}","metrics":[{"key":"notes_created","value":7},{"key":"focus_minutes","value":3}]}""")
        }
        assertEquals(HttpStatusCode.OK, first.status)

        // Отставшее устройство с меньшим счётчиком не откатывает прогресс,
        // выросший счётчик — обновляет.
        val second = client.put("/achievements/metrics") {
            bearerAuth(user.accessToken)
            jsonBody("""{"operationId":"${UUID.randomUUID()}","metrics":[{"key":"notes_created","value":5},{"key":"focus_minutes","value":10}]}""")
        }
        assertEquals(HttpStatusCode.OK, second.status)

        // Ретрай уже применённой операции (outbox) — no-op.
        val retriedOperation = UUID.randomUUID()
        repeat(2) {
            val retry = client.put("/achievements/metrics") {
                bearerAuth(user.accessToken)
                jsonBody("""{"operationId":"$retriedOperation","metrics":[{"key":"notes_created","value":100}]}""")
            }
            assertEquals(HttpStatusCode.OK, retry.status)
        }

        val snapshot = json.decodeFromString<AchievementsSnapshotDto>(
            client.get("/achievements") { bearerAuth(user.accessToken) }.bodyAsText()
        )
        val metrics = snapshot.metrics.associate { it.key to it.value }
        assertEquals(100L, metrics["notes_created"])
        assertEquals(10L, metrics["focus_minutes"])
    }

    @Test
    fun unlockIsIdempotent_byOperationAndByTier() = testApplication {
        startBackend()
        val user = registerUser()
        val operationId = UUID.randomUUID()

        repeat(2) {
            val response = client.post("/achievements/unlocks") {
                bearerAuth(user.accessToken)
                jsonBody("""{"operationId":"$operationId","achievementId":"notes_created","tier":"BRONZE","points":10,"unlockedAt":123}""")
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }

        // Та же ступень с другого устройства (другой operationId) — дубликат молча пропускается.
        val fromOtherDevice = client.post("/achievements/unlocks") {
            bearerAuth(user.accessToken)
            jsonBody("""{"operationId":"${UUID.randomUUID()}","achievementId":"notes_created","tier":"BRONZE","points":10,"unlockedAt":456}""")
        }
        assertEquals(HttpStatusCode.OK, fromOtherDevice.status)

        val snapshot = json.decodeFromString<AchievementsSnapshotDto>(
            client.get("/achievements") { bearerAuth(user.accessToken) }.bodyAsText()
        )
        val unlock = snapshot.unlocks.single()
        assertEquals("notes_created", unlock.achievementId)
        assertEquals("BRONZE", unlock.tier)
        assertEquals(10, unlock.points)
        assertEquals(123L, unlock.unlockedAt)
    }

    @Test
    fun unlockRejectsUnknownTier() = testApplication {
        startBackend()
        val user = registerUser()

        val response = client.post("/achievements/unlocks") {
            bearerAuth(user.accessToken)
            jsonBody("""{"operationId":"${UUID.randomUUID()}","achievementId":"notes_created","tier":"DIAMOND","points":10,"unlockedAt":1}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertApiError(response, "INVALID_REQUEST")
    }
}
