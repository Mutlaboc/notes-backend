package com.example.mutlabocnotes

import com.example.mutlabocnotes.inventory.InventoryDto
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.decodeFromString

// Интеграционные тесты инвентаря: стартовый набор, equip/unequip, идемпотентность, ошибки.
class InventoryIntegrationTest : BackendIntegrationTestSupport() {

    @Test
    fun getInventoryCreatesStarterSetOnFirstAccess() = testApplication {
        startBackend()
        val user = registerUser()

        val response = client.get("/inventory") { bearerAuth(user.accessToken) }

        assertEquals(HttpStatusCode.OK, response.status)
        val inventory = json.decodeFromString<InventoryDto>(response.bodyAsText())
        assertTrue(inventory.items.isNotEmpty())
        assertTrue(inventory.items.none { it.equippedSlot != null })
        val sword = inventory.items.first { it.id == "wooden-sword" }
        assertEquals("WEAPON", sword.slot)
        assertEquals("STRENGTH", sword.bonuses.single().statKey)
        // Ненадеваемый предмет присутствует и slot == null.
        assertNull(inventory.items.first { it.id == "lucky-acorn" }.slot)
    }

    @Test
    fun inventoryRequiresAuthentication() = testApplication {
        startBackend()

        assertEquals(HttpStatusCode.Unauthorized, client.get("/inventory").status)
    }

    @Test
    fun equipPutsItemIntoSlotAndIsIdempotent() = testApplication {
        startBackend()
        val user = registerUser()
        client.get("/inventory") { bearerAuth(user.accessToken) }
        val operationId = UUID.randomUUID()

        val first = client.post("/inventory/equip") {
            bearerAuth(user.accessToken)
            jsonBody("""{"operationId":"$operationId","itemId":"wooden-sword"}""")
        }
        assertEquals(HttpStatusCode.OK, first.status)
        val equipped = json.decodeFromString<InventoryDto>(first.bodyAsText())
        assertEquals("WEAPON", equipped.items.first { it.id == "wooden-sword" }.equippedSlot)

        // Повтор той же операции (ретрай outbox) не меняет состояние и не падает.
        val retry = client.post("/inventory/equip") {
            bearerAuth(user.accessToken)
            jsonBody("""{"operationId":"$operationId","itemId":"wooden-sword"}""")
        }
        assertEquals(HttpStatusCode.OK, retry.status)
        val retried = json.decodeFromString<InventoryDto>(retry.bodyAsText())
        assertEquals(equipped, retried)
    }

    @Test
    fun unequipClearsSlotAndEmptySlotIsNoOp() = testApplication {
        startBackend()
        val user = registerUser()
        client.get("/inventory") { bearerAuth(user.accessToken) }
        client.post("/inventory/equip") {
            bearerAuth(user.accessToken)
            jsonBody("""{"operationId":"${UUID.randomUUID()}","itemId":"straw-hat"}""")
        }

        val unequip = client.post("/inventory/unequip") {
            bearerAuth(user.accessToken)
            jsonBody("""{"operationId":"${UUID.randomUUID()}","slot":"HEAD"}""")
        }
        assertEquals(HttpStatusCode.OK, unequip.status)
        val inventory = json.decodeFromString<InventoryDto>(unequip.bodyAsText())
        assertTrue(inventory.items.none { it.equippedSlot != null })

        // Пустой слот — no-op, не ошибка.
        val again = client.post("/inventory/unequip") {
            bearerAuth(user.accessToken)
            jsonBody("""{"operationId":"${UUID.randomUUID()}","slot":"HEAD"}""")
        }
        assertEquals(HttpStatusCode.OK, again.status)
    }

    @Test
    fun equipUsesUnifiedErrorContract() = testApplication {
        startBackend()
        val user = registerUser()
        client.get("/inventory") { bearerAuth(user.accessToken) }

        // Неизвестный предмет -> 404 item_not_found.
        val missing = client.post("/inventory/equip") {
            bearerAuth(user.accessToken)
            jsonBody("""{"operationId":"${UUID.randomUUID()}","itemId":"no-such-item"}""")
        }
        assertEquals(HttpStatusCode.NotFound, missing.status)
        assertApiError(missing, "item_not_found")

        // Ненадеваемый предмет -> 400 invalid_request.
        val notEquippable = client.post("/inventory/equip") {
            bearerAuth(user.accessToken)
            jsonBody("""{"operationId":"${UUID.randomUUID()}","itemId":"lucky-acorn"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, notEquippable.status)
        assertApiError(notEquippable, "invalid_request")

        // Некорректный operationId -> 400.
        val badOperation = client.post("/inventory/equip") {
            bearerAuth(user.accessToken)
            jsonBody("""{"operationId":"not-a-uuid","itemId":"wooden-sword"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, badOperation.status)

        // Неизвестный слот при снятии -> 400.
        val badSlot = client.post("/inventory/unequip") {
            bearerAuth(user.accessToken)
            jsonBody("""{"operationId":"${UUID.randomUUID()}","slot":"TAIL"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, badSlot.status)
    }

    @Test
    fun inventoryIsUserScoped() = testApplication {
        startBackend()
        val owner = registerUser(email = "inv-owner-${UUID.randomUUID()}@example.com")
        val other = registerUser(email = "inv-other-${UUID.randomUUID()}@example.com")

        client.post("/inventory/equip") {
            bearerAuth(owner.accessToken)
            jsonBody("""{"operationId":"${UUID.randomUUID()}","itemId":"wooden-sword"}""")
        }

        val otherInventory = json.decodeFromString<InventoryDto>(
            client.get("/inventory") { bearerAuth(other.accessToken) }.bodyAsText()
        )
        assertTrue(otherInventory.items.none { it.equippedSlot != null })
    }
}
