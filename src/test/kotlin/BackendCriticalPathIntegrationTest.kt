package com.example.mutlabocnotes

import com.example.mutlabocnotes.api.ApiErrorCodes
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class BackendCriticalPathIntegrationTest : BackendIntegrationTestSupport() {

    @Test
    fun registerCreatesUserAndReturnsNormalizedAuthSession() = testApplication {
        startBackend()

        val response = client.post("/auth/register") {
            jsonBody(
                """
                {
                  "email":"  Register-${UUID.randomUUID()}@Example.COM  ",
                  "password":"$defaultPassword",
                  "displayName":"  Test User  "
                }
                """.trimIndent()
            )
        }
        val body = response.jsonObject()
        val user = body["user"]?.jsonObject ?: error("user missing")

        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(body["accessToken"]?.jsonPrimitive?.content?.isNotBlank() == true)
        assertTrue(body["refreshToken"]?.jsonPrimitive?.content?.isNotBlank() == true)
        assertEquals("Bearer", body["tokenType"]?.jsonPrimitive?.content)
        assertEquals("test user", user["displayName"]?.jsonPrimitive?.content?.lowercase())
        assertTrue(user["email"]?.jsonPrimitive?.content?.endsWith("@example.com") == true)
        assertNotNull(user["id"]?.jsonPrimitive?.content)
        assertTrue(user["bridgeUserKey"]?.jsonPrimitive?.content?.startsWith("local:") == true)
    }

    @Test
    fun duplicateRegisterReturnsConflictErrorDto() = testApplication {
        startBackend()

        val email = "duplicate-${UUID.randomUUID()}@example.com"
        registerUser(email = email)

        val response = client.post("/auth/register") {
            jsonBody("""{"email":"${email.uppercase()}","password":"$defaultPassword"}""")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertApiError(response, ApiErrorCodes.EMAIL_ALREADY_REGISTERED)
    }

    @Test
    fun loginAcceptsValidCredentialsAndRejectsInvalidCredentials() = testApplication {
        startBackend()

        val email = "login-${UUID.randomUUID()}@example.com"
        registerUser(email = email)

        val success = client.post("/auth/login") {
            jsonBody("""{"email":"${email.uppercase()}","password":"$defaultPassword"}""")
        }
        val successBody = success.jsonObject()

        assertEquals(HttpStatusCode.OK, success.status)
        assertTrue(successBody["accessToken"]?.jsonPrimitive?.content?.isNotBlank() == true)
        assertTrue(successBody["refreshToken"]?.jsonPrimitive?.content?.isNotBlank() == true)

        val failure = client.post("/auth/login") {
            jsonBody("""{"email":"$email","password":"wrong-password"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, failure.status)
        assertApiError(failure, ApiErrorCodes.INVALID_EMAIL_OR_PASSWORD)
    }

    @Test
    fun refreshRotatesRefreshTokenAndInvalidatesOldToken() = testApplication {
        startBackend()

        val session = registerUser()

        val refresh = client.post("/auth/refresh") {
            jsonBody("""{"refreshToken":"${session.refreshToken}"}""")
        }
        val refreshedBody = refresh.jsonObject()
        val rotatedRefreshToken = refreshedBody["refreshToken"]?.jsonPrimitive?.content
            ?: error("refreshToken missing")

        assertEquals(HttpStatusCode.OK, refresh.status)
        assertNotEquals(session.refreshToken, rotatedRefreshToken)

        val oldRefresh = client.post("/auth/refresh") {
            jsonBody("""{"refreshToken":"${session.refreshToken}"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, oldRefresh.status)
        assertApiError(oldRefresh, ApiErrorCodes.UNAUTHORIZED)
    }

    @Test
    fun logoutRevokesRefreshTokenAndKeepsIdempotentShape() = testApplication {
        startBackend()

        val session = registerUser()

        repeat(2) {
            val logout = client.post("/auth/logout") {
                jsonBody("""{"refreshToken":"${session.refreshToken}"}""")
            }

            assertEquals(HttpStatusCode.NoContent, logout.status)
        }

        val refresh = client.post("/auth/refresh") {
            jsonBody("""{"refreshToken":"${session.refreshToken}"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, refresh.status)
        assertApiError(refresh, ApiErrorCodes.UNAUTHORIZED)
    }

    @Test
    fun logoutRejectsBlankRefreshToken() = testApplication {
        startBackend()

        val response = client.post("/auth/logout") {
            jsonBody("""{"refreshToken":"   "}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertApiError(response, ApiErrorCodes.INVALID_REQUEST)
    }

    @Test
    fun authMeRequiresJwtAndReturnsCurrentUser() = testApplication {
        startBackend()

        val anonymous = client.get("/auth/me")

        assertEquals(HttpStatusCode.Unauthorized, anonymous.status)
        assertApiError(anonymous, ApiErrorCodes.UNAUTHORIZED)

        val session = registerUser(
            email = "me-${UUID.randomUUID()}@example.com",
            displayName = "Current User"
        )
        val response = client.get("/auth/me") {
            bearerAuth(session.accessToken)
        }
        val body = response.jsonObject()

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(session.user.id, body["id"]?.jsonPrimitive?.content)
        assertEquals(session.user.email, body["email"]?.jsonPrimitive?.content)
        assertEquals(session.user.bridgeUserKey, body["bridgeUserKey"]?.jsonPrimitive?.content)
    }

    @Test
    fun socialAuthNotReadyUsesUnifiedContract() = testApplication {
        startBackend()

        val google = client.post("/auth/social/google") {
            jsonBody("""{"idToken":"fake-google-token"}""")
        }

        assertEquals(HttpStatusCode.NotImplemented, google.status)
        assertApiError(google, "google_auth_not_configured")

        val yandex = client.post("/auth/social/yandex") {
            jsonBody("""{"accessToken":"fake-yandex-token"}""")
        }

        assertEquals(HttpStatusCode.NotImplemented, yandex.status)
        assertApiError(yandex, "yandex_auth_not_configured")
    }

    @Test
    fun notesFullCrudIsUserScopedAndPreservesResponseShape() = testApplication {
        startBackend()

        val owner = registerUser(email = "notes-owner-${UUID.randomUUID()}@example.com")
        val other = registerUser(email = "notes-other-${UUID.randomUUID()}@example.com")

        val create = client.post("/notes") {
            bearerAuth(owner.accessToken)
            jsonBody(
                """
                {
                  "title":"Groceries",
                  "content":"ignored for shopping",
                  "category":"SHOPPING",
                  "checklist":[{"text":" Milk ","isChecked":false}],
                  "deadlineMillis":1893456000000,
                  "repeatRule":"MONTHLY",
                  "coinCount":2,
                  "isCompleted":false
                }
                """.trimIndent()
            )
        }
        val created = create.jsonObject()
        val noteId = created["id"]?.jsonPrimitive?.content ?: error("note id missing")

        assertEquals(HttpStatusCode.Created, create.status)
        assertEquals("Groceries", created["title"]?.jsonPrimitive?.content)
        assertEquals("SHOPPING", created["category"]?.jsonPrimitive?.content)
        assertEquals("", created["content"]?.jsonPrimitive?.content)
        assertEquals("Milk", created["checklist"]?.jsonArray?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content)
        assertEquals("NONE", created["repeatRule"]?.jsonPrimitive?.content)
        assertNotNull(created["createdAt"]?.jsonPrimitive?.content)
        assertNotNull(created["updatedAt"]?.jsonPrimitive?.content)

        val list = client.get("/notes") {
            bearerAuth(owner.accessToken)
        }

        assertEquals(HttpStatusCode.OK, list.status)
        assertEquals(noteId, list.jsonObjectOrArrayFirstId())

        val byId = client.get("/notes/$noteId") {
            bearerAuth(owner.accessToken)
        }

        assertEquals(HttpStatusCode.OK, byId.status)
        assertEquals(noteId, byId.jsonObject()["id"]?.jsonPrimitive?.content)

        val otherUserRead = client.get("/notes/$noteId") {
            bearerAuth(other.accessToken)
        }

        assertEquals(HttpStatusCode.NotFound, otherUserRead.status)
        assertApiError(otherUserRead, ApiErrorCodes.NOTE_NOT_FOUND)

        val update = client.put("/notes/$noteId") {
            bearerAuth(owner.accessToken)
            jsonBody(
                """
                {
                  "title":"Updated task",
                  "content":"Task body",
                  "category":"TASKS",
                  "checklist":[{"text":"ignored","isChecked":true}],
                  "deadlineMillis":1893456000000,
                  "repeatRule":"WEEKLY",
                  "coinCount":3,
                  "isCompleted":true
                }
                """.trimIndent()
            )
        }
        val updated = update.jsonObject()

        assertEquals(HttpStatusCode.OK, update.status)
        assertEquals(noteId, updated["id"]?.jsonPrimitive?.content)
        assertEquals("TASKS", updated["category"]?.jsonPrimitive?.content)
        assertEquals("Task body", updated["content"]?.jsonPrimitive?.content)
        assertEquals(0, updated["checklist"]?.jsonArray?.size)
        assertEquals("WEEKLY", updated["repeatRule"]?.jsonPrimitive?.content)

        val delete = client.delete("/notes/$noteId") {
            bearerAuth(owner.accessToken)
        }

        assertEquals(HttpStatusCode.NoContent, delete.status)

        val afterDelete = client.get("/notes/$noteId") {
            bearerAuth(owner.accessToken)
        }

        assertEquals(HttpStatusCode.NotFound, afterDelete.status)
        assertApiError(afterDelete, ApiErrorCodes.NOTE_NOT_FOUND)
    }

    @Test
    fun notesAcceptFullRepeatRuleEnumForTasks() = testApplication {
        startBackend()

        val owner = registerUser(email = "repeat-rules-${UUID.randomUUID()}@example.com")

        listOf("NONE", "DAILY", "WEEKLY", "MONTHLY").forEach { repeatRule ->
            val create = client.post("/notes") {
                bearerAuth(owner.accessToken)
                jsonBody(
                    """
                    {
                      "title":"Task $repeatRule",
                      "category":"TASKS",
                      "deadlineMillis":1893456000000,
                      "repeatRule":"$repeatRule"
                    }
                    """.trimIndent()
                )
            }
            val created = create.jsonObject()

            assertEquals(HttpStatusCode.Created, create.status)
            assertEquals(repeatRule, created["repeatRule"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun notesCompletionRequiresAuthAndTogglesForOwner() = testApplication {
        startBackend()

        val session = registerUser()
        val create = client.post("/notes") {
            bearerAuth(session.accessToken)
            jsonBody("""{"title":"Ship tests","category":"TASKS","isCompleted":false}""")
        }
        val noteId = create.jsonObject()["id"]?.jsonPrimitive?.content ?: error("note id missing")

        assertEquals(HttpStatusCode.Created, create.status)
        assertEquals("false", create.jsonObject()["isCompleted"]?.jsonPrimitive?.content)

        val anonymous = client.patch("/notes/$noteId/completion") {
            jsonBody("""{"isCompleted":true}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, anonymous.status)
        assertApiError(anonymous, ApiErrorCodes.UNAUTHORIZED)

        val toggle = client.patch("/notes/$noteId/completion") {
            bearerAuth(session.accessToken)
            jsonBody("""{"isCompleted":true}""")
        }
        val toggled = toggle.jsonObject()

        assertEquals(HttpStatusCode.OK, toggle.status)
        assertEquals(noteId, toggled["id"]?.jsonPrimitive?.content)
        assertEquals("true", toggled["isCompleted"]?.jsonPrimitive?.content)
    }

    @Test
    fun notesCompletionIsOwnerScopedAndUsesUnifiedErrors() = testApplication {
        startBackend()

        val owner = registerUser(email = "owner-${UUID.randomUUID()}@example.com")
        val other = registerUser(email = "other-${UUID.randomUUID()}@example.com")
        val create = client.post("/notes") {
            bearerAuth(owner.accessToken)
            jsonBody("""{"title":"Private note","category":"TASKS"}""")
        }
        val noteId = create.jsonObject()["id"]?.jsonPrimitive?.content ?: error("note id missing")

        val otherUserResponse = client.patch("/notes/$noteId/completion") {
            bearerAuth(other.accessToken)
            jsonBody("""{"isCompleted":true}""")
        }

        assertEquals(HttpStatusCode.NotFound, otherUserResponse.status)
        assertApiError(otherUserResponse, ApiErrorCodes.NOTE_NOT_FOUND)

        val invalidId = client.patch("/notes/not-a-uuid/completion") {
            bearerAuth(owner.accessToken)
            jsonBody("""{"isCompleted":true}""")
        }

        assertEquals(HttpStatusCode.BadRequest, invalidId.status)
        assertApiError(invalidId, ApiErrorCodes.INVALID_NOTE_ID)

        val missing = client.patch("/notes/00000000-0000-0000-0000-000000000001/completion") {
            bearerAuth(owner.accessToken)
            jsonBody("""{"isCompleted":true}""")
        }

        assertEquals(HttpStatusCode.NotFound, missing.status)
        assertApiError(missing, ApiErrorCodes.NOTE_NOT_FOUND)
    }

    @Test
    fun homeCardsRequireAuthAndPreserveResponseShapeOnCreateAndUpdate() = testApplication {
        startBackend()

        val session = registerUser()
        val anonymous = client.post("/home-cards") {
            jsonBody(validHomeCardBody("Meter", "METERS"))
        }

        assertEquals(HttpStatusCode.Unauthorized, anonymous.status)
        assertApiError(anonymous, ApiErrorCodes.UNAUTHORIZED)

        val create = client.post("/home-cards") {
            bearerAuth(session.accessToken)
            jsonBody(validHomeCardBody("Meter", "METERS"))
        }
        val created = create.jsonObject()
        val cardId = created["id"]?.jsonPrimitive?.content ?: error("card id missing")
        val createdAt = created["createdAt"]?.jsonPrimitive?.content ?: error("createdAt missing")

        assertEquals(HttpStatusCode.Created, create.status)
        assertEquals("Meter", created["title"]?.jsonPrimitive?.content)
        assertEquals("METERS", created["section"]?.jsonPrimitive?.content)
        assertEquals("serial", created["fields"]?.jsonArray?.get(0)?.jsonObject?.get("key")?.jsonPrimitive?.content)
        assertEquals("https://example.com/first", created["links"]?.jsonArray?.get(0)?.jsonPrimitive?.content)

        val update = client.put("/home-cards/$cardId") {
            bearerAuth(session.accessToken)
            jsonBody(validHomeCardBody("Manuals", "DOCUMENTS"))
        }
        val updated = update.jsonObject()

        assertEquals(HttpStatusCode.OK, update.status)
        assertEquals(cardId, updated["id"]?.jsonPrimitive?.content)
        assertEquals("Manuals", updated["title"]?.jsonPrimitive?.content)
        assertEquals("DOCUMENTS", updated["section"]?.jsonPrimitive?.content)
        assertEquals(createdAt, updated["createdAt"]?.jsonPrimitive?.content)
        assertEquals("serial", updated["fields"]?.jsonArray?.get(0)?.jsonObject?.get("key")?.jsonPrimitive?.content)
        assertEquals("https://example.com/first", updated["links"]?.jsonArray?.get(0)?.jsonPrimitive?.content)
        assertNotNull(updated["updatedAt"]?.jsonPrimitive?.content)
    }

    @Test
    fun homeCardsUseUnifiedErrorsForIdsAndInvalidPayloads() = testApplication {
        startBackend()

        val session = registerUser()

        val invalidId = client.get("/home-cards/not-a-uuid") {
            bearerAuth(session.accessToken)
        }

        assertEquals(HttpStatusCode.BadRequest, invalidId.status)
        assertApiError(invalidId, ApiErrorCodes.INVALID_CARD_ID)

        val missing = client.delete("/home-cards/00000000-0000-0000-0000-000000000001") {
            bearerAuth(session.accessToken)
        }

        assertEquals(HttpStatusCode.NotFound, missing.status)
        assertApiError(missing, ApiErrorCodes.CARD_NOT_FOUND)

        val unknownField = client.post("/home-cards") {
            bearerAuth(session.accessToken)
            jsonBody(
                """
                {
                  "title":"Meter",
                  "section":"METERS",
                  "fields":[],
                  "note":"",
                  "links":[],
                  "unexpected":"value"
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.BadRequest, unknownField.status)
        assertApiError(unknownField, ApiErrorCodes.INVALID_REQUEST)

        val invalidSection = client.post("/home-cards") {
            bearerAuth(session.accessToken)
            jsonBody(validHomeCardBody("Bad section", "NOT_A_SECTION"))
        }

        assertEquals(HttpStatusCode.BadRequest, invalidSection.status)
        assertApiError(invalidSection, ApiErrorCodes.INVALID_REQUEST)

        val blankLink = client.post("/home-cards") {
            bearerAuth(session.accessToken)
            jsonBody(
                """
                {
                  "title":"Blank link",
                  "section":"OTHER",
                  "fields":[],
                  "note":"",
                  "links":["   "]
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.BadRequest, blankLink.status)
        assertApiError(blankLink, ApiErrorCodes.INVALID_REQUEST)
    }

    @Test
    fun homeCardsFullCrudIsUserScopedAndUsesServerTimestamps() = testApplication {
        startBackend()

        val owner = registerUser(email = "cards-owner-${UUID.randomUUID()}@example.com")
        val other = registerUser(email = "cards-other-${UUID.randomUUID()}@example.com")

        val create = client.post("/home-cards") {
            bearerAuth(owner.accessToken)
            jsonBody(validHomeCardBody("Meter", "METERS"))
        }
        val created = create.jsonObject()
        val cardId = created["id"]?.jsonPrimitive?.content ?: error("card id missing")
        val createdAt = created["createdAt"]?.jsonPrimitive?.content?.toLong() ?: error("createdAt missing")
        val updatedAt = created["updatedAt"]?.jsonPrimitive?.content?.toLong() ?: error("updatedAt missing")

        assertEquals(HttpStatusCode.Created, create.status)
        assertTrue(createdAt > 0)
        assertTrue(updatedAt > 0)

        val list = client.get("/home-cards") {
            bearerAuth(owner.accessToken)
        }

        assertEquals(HttpStatusCode.OK, list.status)
        assertEquals(cardId, list.jsonObjectOrArrayFirstId())

        val byId = client.get("/home-cards/$cardId") {
            bearerAuth(owner.accessToken)
        }

        assertEquals(HttpStatusCode.OK, byId.status)
        assertEquals(cardId, byId.jsonObject()["id"]?.jsonPrimitive?.content)

        val otherUserRead = client.get("/home-cards/$cardId") {
            bearerAuth(other.accessToken)
        }

        assertEquals(HttpStatusCode.NotFound, otherUserRead.status)
        assertApiError(otherUserRead, ApiErrorCodes.CARD_NOT_FOUND)

        val update = client.put("/home-cards/$cardId") {
            bearerAuth(owner.accessToken)
            jsonBody(validHomeCardBody("Manuals", "DOCUMENTS"))
        }
        val updated = update.jsonObject()

        assertEquals(HttpStatusCode.OK, update.status)
        assertEquals(cardId, updated["id"]?.jsonPrimitive?.content)
        assertEquals("Manuals", updated["title"]?.jsonPrimitive?.content)
        assertEquals(createdAt.toString(), updated["createdAt"]?.jsonPrimitive?.content)
        assertTrue((updated["updatedAt"]?.jsonPrimitive?.content?.toLong() ?: 0L) >= updatedAt)

        val delete = client.delete("/home-cards/$cardId") {
            bearerAuth(owner.accessToken)
        }

        assertEquals(HttpStatusCode.NoContent, delete.status)

        val afterDelete = client.get("/home-cards/$cardId") {
            bearerAuth(owner.accessToken)
        }

        assertEquals(HttpStatusCode.NotFound, afterDelete.status)
        assertApiError(afterDelete, ApiErrorCodes.CARD_NOT_FOUND)
    }

    @Test
    fun diagnosticsKeepPublicAndProtectedBoundaries() = testApplication {
        startBackend()

        val publicHealth = client.get("/health")

        assertEquals(HttpStatusCode.OK, publicHealth.status)
        assertEquals("ok", publicHealth.jsonObject()["status"]?.jsonPrimitive?.content)

        val anonymousDbHealth = client.get("/health/db")

        assertEquals(HttpStatusCode.Unauthorized, anonymousDbHealth.status)
        assertApiError(anonymousDbHealth, ApiErrorCodes.UNAUTHORIZED)

        val session = registerUser()
        val authenticatedDbHealth = client.get("/health/db") {
            bearerAuth(session.accessToken)
        }
        val bodyText = authenticatedDbHealth.bodyAsText()
        val body = json.parseToJsonElement(bodyText).jsonObject

        assertEquals(HttpStatusCode.OK, authenticatedDbHealth.status)
        assertEquals("ok", body["status"]?.jsonPrimitive?.content)
        assertEquals("reachable", body["database"]?.jsonPrimitive?.content)
        assertFalse(bodyText.contains("PostgreSQL", ignoreCase = true))
        assertFalse(bodyText.contains("version", ignoreCase = true))
    }

    private fun validHomeCardBody(title: String, section: String): String =
        """
        {
          "title":"$title",
          "section":"$section",
          "fields":[
            {"key":"serial","value":"A-1"},
            {"key":"room","value":"Kitchen"}
          ],
          "note":"Keep visible",
          "links":[
            "https://example.com/first",
            "https://example.com/second"
          ]
        }
        """.trimIndent()

    private suspend fun io.ktor.client.statement.HttpResponse.jsonObjectOrArrayFirstId(): String {
        val element = json.parseToJsonElement(bodyAsText())
        return element.jsonArray.first().jsonObject["id"]?.jsonPrimitive?.content
            ?: error("id missing")
    }
}
