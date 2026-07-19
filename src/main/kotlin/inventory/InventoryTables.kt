package com.example.mutlabocnotes.inventory

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

// Предметы инвентаря: несколько строк на пользователя, item_key — «id» для клиента.
object InventoryItemsTable : Table("inventory_items") {
    val id = javaUUID("id")
    val userId = javaUUID("user_id")
    val itemKey = text("item_key")
    val position = integer("position")
    val name = text("name")
    val description = text("description")
    val icon = text("icon")
    val slot = text("slot").nullable()
    val rarity = text("rarity")
    val equippedSlot = text("equipped_slot").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}

// Бонусы предмета к характеристикам (ключи и имена — как у статов персонажа).
object InventoryItemBonusesTable : Table("inventory_item_bonuses") {
    val itemId = javaUUID("item_id")
    val position = integer("position")
    val statKey = text("stat_key")
    val statName = text("stat_name")
    val value = integer("value")

    override val primaryKey = PrimaryKey(itemId, position)
}

// Идемпотентность equip/unequip по operationId.
object InventoryOperationsTable : Table("inventory_operations") {
    val operationId = javaUUID("operation_id")
    val userId = javaUUID("user_id")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(userId, operationId)
}
