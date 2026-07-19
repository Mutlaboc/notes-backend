package com.example.mutlabocnotes.inventory

import kotlinx.serialization.Serializable

// Слоты экипировки (классика D&D). Порядок — порядок отображения на клиенте.
enum class EquipSlot {
    HEAD, BODY, LEGS, WEAPON, OFFHAND, ACCESSORY;

    companion object {
        fun fromKeyOrNull(key: String?): EquipSlot? =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) }
    }
}

// Редкость предмета.
enum class ItemRarity { COMMON, UNCOMMON, RARE, EPIC, LEGENDARY }

// DTO бонуса предмета к характеристике.
@Serializable
data class ItemBonusDto(
    val statKey: String,
    val statName: String,
    val value: Int,
)

// DTO одного предмета инвентаря (id — стабильный item_key).
@Serializable
data class InventoryItemDto(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val slot: String?,
    val rarity: String = ItemRarity.COMMON.name,
    val bonuses: List<ItemBonusDto> = emptyList(),
    val equippedSlot: String? = null,
)

// Полный инвентарь — ответ GET и всех мутаций.
@Serializable
data class InventoryDto(
    val items: List<InventoryItemDto> = emptyList(),
)

// Тело запроса «надеть предмет»; слот определяет сам предмет.
@Serializable
data class InventoryEquipRequestDto(
    val operationId: String,
    val itemId: String,
)

// Тело запроса «снять предмет из слота».
@Serializable
data class InventoryUnequipRequestDto(
    val operationId: String,
    val slot: String,
)
