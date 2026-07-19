package com.example.mutlabocnotes.inventory

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

// Репозиторий инвентаря: equip/unequip с идемпотентностью по operationId
// (паттерн — как в CharacterRepository). Стартового набора нет: новый пользователь
// начинает с пустым инвентарём, предметы приходят из событий фокус-таймера.
open class InventoryRepository {

    // Возвращает инвентарь пользователя (пустой для нового пользователя).
    open fun getOrCreateForUser(userId: UUID): InventoryDto = transaction {
        readInventory(userId)
    }

    /**
     * Надевает предмет [itemKey] в его слот; предмет, занимавший слот, снимается.
     * Ошибки: [NoSuchElementException] — предмет не найден (404),
     * [IllegalArgumentException] — предмет нельзя надеть (400).
     */
    open fun equip(userId: UUID, operationId: UUID, itemKey: String): InventoryDto = transaction {
        lockInventory(userId)
        if (operationAlreadyApplied(userId, operationId)) return@transaction readInventory(userId)

        val item = InventoryItemsTable.selectAll().where {
            (InventoryItemsTable.userId eq userId) and (InventoryItemsTable.itemKey eq itemKey)
        }.singleOrNull() ?: throw NoSuchElementException("Unknown item")

        val slot = EquipSlot.fromKeyOrNull(item[InventoryItemsTable.slot])
            ?: throw IllegalArgumentException("Item is not equippable")

        val now = OffsetDateTime.now(ZoneOffset.UTC)
        if (item[InventoryItemsTable.equippedSlot] == null) {
            // Сначала освобождаем слот (частичный уникальный индекс), затем надеваем.
            InventoryItemsTable.update({
                (InventoryItemsTable.userId eq userId) and (InventoryItemsTable.equippedSlot eq slot.name)
            }) {
                it[equippedSlot] = null
                it[updatedAt] = now
            }
            InventoryItemsTable.update({ InventoryItemsTable.id eq item[InventoryItemsTable.id] }) {
                it[equippedSlot] = slot.name
                it[updatedAt] = now
            }
        }
        recordOperation(userId, operationId, now)
        readInventory(userId)
    }

    /**
     * Снимает предмет из слота [slot]. Пустой слот — no-op (не ошибка).
     * Неизвестный слот — [IllegalArgumentException] (400).
     */
    open fun unequip(userId: UUID, operationId: UUID, slot: String): InventoryDto = transaction {
        val parsed = EquipSlot.fromKeyOrNull(slot) ?: throw IllegalArgumentException("Unknown slot")
        lockInventory(userId)
        if (operationAlreadyApplied(userId, operationId)) return@transaction readInventory(userId)

        val now = OffsetDateTime.now(ZoneOffset.UTC)
        InventoryItemsTable.update({
            (InventoryItemsTable.userId eq userId) and (InventoryItemsTable.equippedSlot eq parsed.name)
        }) {
            it[equippedSlot] = null
            it[updatedAt] = now
        }
        recordOperation(userId, operationId, now)
        readInventory(userId)
    }

    // --- helpers (внутри активной транзакции) ---

    // Сериализует конкурентные мутации инвентаря одного пользователя.
    private fun lockInventory(userId: UUID) {
        InventoryItemsTable.selectAll()
            .where { InventoryItemsTable.userId eq userId }
            .forUpdate()
            .toList()
    }

    private fun operationAlreadyApplied(userId: UUID, operationId: UUID): Boolean =
        InventoryOperationsTable.selectAll().where {
            (InventoryOperationsTable.userId eq userId) and
                (InventoryOperationsTable.operationId eq operationId)
        }.singleOrNull() != null

    private fun recordOperation(userId: UUID, operationId: UUID, now: OffsetDateTime) {
        InventoryOperationsTable.insert {
            it[InventoryOperationsTable.operationId] = operationId
            it[InventoryOperationsTable.userId] = userId
            it[createdAt] = now
        }
    }

    private fun readInventory(userId: UUID): InventoryDto {
        val itemRows = InventoryItemsTable.selectAll()
            .where { InventoryItemsTable.userId eq userId }
            .orderBy(InventoryItemsTable.position, SortOrder.ASC)
            .toList()

        val itemIds = itemRows.map { it[InventoryItemsTable.id] }
        val bonusesByItem = if (itemIds.isEmpty()) emptyMap() else {
            InventoryItemBonusesTable.selectAll()
                .where { InventoryItemBonusesTable.itemId inList itemIds }
                .orderBy(InventoryItemBonusesTable.position, SortOrder.ASC)
                .groupBy(
                    keySelector = { it[InventoryItemBonusesTable.itemId] },
                    valueTransform = {
                        ItemBonusDto(
                            statKey = it[InventoryItemBonusesTable.statKey],
                            statName = it[InventoryItemBonusesTable.statName],
                            value = it[InventoryItemBonusesTable.value],
                        )
                    },
                )
        }

        return InventoryDto(
            items = itemRows.map { row ->
                InventoryItemDto(
                    id = row[InventoryItemsTable.itemKey],
                    name = row[InventoryItemsTable.name],
                    description = row[InventoryItemsTable.description],
                    icon = row[InventoryItemsTable.icon],
                    slot = row[InventoryItemsTable.slot],
                    rarity = row[InventoryItemsTable.rarity],
                    bonuses = bonusesByItem[row[InventoryItemsTable.id]].orEmpty(),
                    equippedSlot = row[InventoryItemsTable.equippedSlot],
                )
            },
        )
    }

}
