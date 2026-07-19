package com.example.mutlabocnotes

import com.example.mutlabocnotes.inventory.InventoryDto
import com.example.mutlabocnotes.inventory.InventoryItemBonusesTable
import com.example.mutlabocnotes.inventory.InventoryItemsTable
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.decodeFromString
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

// Интеграционные тесты инвентаря: пустой старт, equip/unequip, идемпотентность, ошибки.
class InventoryIntegrationTest : BackendIntegrationTestSupport() {

    // Стартового набора больше нет: новый пользователь начинает с пустым инвентарём.
    @Test
    fun getInventoryIsEmptyForNewUser() = testApplication {
        startBackend()
        val user = registerUser()

        val response = client.get("/inventory") { bearerAuth(user.accessToken) }

        assertEquals(HttpStatusCode.OK, response.status)
        val inventory = json.decodeFromString<InventoryDto>(response.bodyAsText())
        assertTrue(inventory.items.isEmpty())
    }

    @Test
    fun seededItemsAreReturnedWithSlotAndBonuses() = testApplication {
        startBackend()
        val user = registerUser()
        seedTestItems(user.user.id)

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
        seedTestItems(user.user.id)
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
        seedTestItems(user.user.id)
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
        seedTestItems(user.user.id)

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
        seedTestItems(owner.user.id)
        seedTestItems(other.user.id)

        client.post("/inventory/equip") {
            bearerAuth(owner.accessToken)
            jsonBody("""{"operationId":"${UUID.randomUUID()}","itemId":"wooden-sword"}""")
        }

        val otherInventory = json.decodeFromString<InventoryDto>(
            client.get("/inventory") { bearerAuth(other.accessToken) }.bodyAsText()
        )
        assertTrue(otherInventory.items.none { it.equippedSlot != null })
    }

    // Кладёт пользователю тестовый набор предметов напрямую в БД
    // (в проде предметы приходят только из событий фокус-таймера).
    private fun seedTestItems(userIdRaw: String) {
        val userId = UUID.fromString(userIdRaw)
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        data class Seed(
            val key: String,
            val name: String,
            val slot: String?,
            val rarity: String,
            val bonus: Triple<String, String, Int>?,
        )
        val seeds = listOf(
            Seed("wooden-sword", "Деревянный меч", "WEAPON", "COMMON", Triple("STRENGTH", "Сила", 1)),
            Seed("straw-hat", "Соломенная шляпа", "HEAD", "COMMON", Triple("WISDOM", "Мудрость", 1)),
            Seed("lucky-acorn", "Счастливый жёлудь", null, "RARE", null),
        )
        transaction {
            seeds.forEachIndexed { index, seed ->
                val itemId = UUID.randomUUID()
                InventoryItemsTable.insert {
                    it[id] = itemId
                    it[InventoryItemsTable.userId] = userId
                    it[itemKey] = seed.key
                    it[position] = index
                    it[name] = seed.name
                    it[description] = ""
                    it[icon] = "🎒"
                    it[slot] = seed.slot
                    it[rarity] = seed.rarity
                    it[equippedSlot] = null
                    it[createdAt] = now
                    it[updatedAt] = now
                }
                seed.bonus?.let { (statKey, statName, value) ->
                    InventoryItemBonusesTable.insert {
                        it[InventoryItemBonusesTable.itemId] = itemId
                        it[position] = 0
                        it[InventoryItemBonusesTable.statKey] = statKey
                        it[InventoryItemBonusesTable.statName] = statName
                        it[InventoryItemBonusesTable.value] = value
                    }
                }
            }
        }
    }
}
