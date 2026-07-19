package com.example.mutlabocnotes.events

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

// Глобальный каталог случайных событий фокус-таймера (сидится миграцией V10).
object FocusEventsTable : Table("focus_events") {
    val eventKey = text("event_key")
    val eventType = text("event_type")
    val weight = integer("weight")
    val textRu = text("text_ru")
    val textEn = text("text_en")
    val characterXp = integer("character_xp")
    val skillXp = integer("skill_xp")
    val itemKey = text("item_key").nullable()
    val itemNameRu = text("item_name_ru").nullable()
    val itemNameEn = text("item_name_en").nullable()
    val itemDescriptionRu = text("item_description_ru").nullable()
    val itemDescriptionEn = text("item_description_en").nullable()
    val itemIcon = text("item_icon").nullable()
    val itemSlot = text("item_slot").nullable()
    val itemRarity = text("item_rarity").nullable()
    val itemBonusStatKey = text("item_bonus_stat_key").nullable()
    val itemBonusStatNameRu = text("item_bonus_stat_name_ru").nullable()
    val itemBonusStatNameEn = text("item_bonus_stat_name_en").nullable()
    val itemBonusValue = integer("item_bonus_value").nullable()
    val newSkillKey = text("new_skill_key").nullable()
    val newSkillNameRu = text("new_skill_name_ru").nullable()
    val newSkillNameEn = text("new_skill_name_en").nullable()

    override val primaryKey = PrimaryKey(eventKey)
}

// Идемпотентность начисления наград события по (user_id, operation_id).
object FocusEventClaimsTable : Table("focus_event_claims") {
    val operationId = javaUUID("operation_id")
    val userId = javaUUID("user_id")
    val eventKey = text("event_key")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(userId, operationId)
}
