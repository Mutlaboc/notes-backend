package com.example.mutlabocnotes.character

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

// Лист персонажа: одна строка на пользователя.
object CharacterSheetsTable : Table("character_sheets") {
    val userId = javaUUID("user_id")
    val name = text("name")
    val level = integer("level")
    val xp = integer("xp")
    val xpToNext = integer("xp_to_next")
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(userId)
}

// Характеристики (Сила, Ловкость и т.д.).
object CharacterStatsTable : Table("character_stats") {
    val id = javaUUID("id")
    val userId = javaUUID("user_id")
    val position = integer("position")
    val statKey = text("stat_key")
    val name = text("name")
    val description = text("description")
    val value = integer("value")

    override val primaryKey = PrimaryKey(id)
}

// Навыки (Лесоруб, Плотник, Архивариус и т.д.).
object CharacterSkillsTable : Table("character_skills") {
    val id = javaUUID("id")
    val userId = javaUUID("user_id")
    val position = integer("position")
    val skillKey = text("skill_key")
    val name = text("name")
    val level = integer("level")
    val progress = double("progress")

    override val primaryKey = PrimaryKey(id)
}
