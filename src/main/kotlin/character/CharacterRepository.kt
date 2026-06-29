package com.example.mutlabocnotes.character

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

// Repository for the per-user character sheet (level, experience, stats, skills).
open class CharacterRepository {

    // Returns the user's sheet, lazily creating a default one on first access.
    open fun getOrCreateForUser(userId: UUID): CharacterSheetDto = transaction {
        val exists = CharacterSheetsTable
            .selectAll()
            .where { CharacterSheetsTable.userId eq userId }
            .singleOrNull()

        if (exists == null) {
            seedDefault(userId)
        }

        readSheet(userId)
    }

    // Full replace of the user's sheet (creates it if missing).
    open fun updateForUser(userId: UUID, request: CharacterUpdateRequestDto): CharacterSheetDto = transaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        val exists = CharacterSheetsTable
            .selectAll()
            .where { CharacterSheetsTable.userId eq userId }
            .singleOrNull()

        if (exists == null) {
            CharacterSheetsTable.insert {
                it[CharacterSheetsTable.userId] = userId
                it[name] = request.name
                it[level] = request.level
                it[xp] = request.xp
                it[xpToNext] = request.xpToNext
                it[createdAt] = now
                it[updatedAt] = now
            }
        } else {
            CharacterSheetsTable.update({ CharacterSheetsTable.userId eq userId }) {
                it[name] = request.name
                it[level] = request.level
                it[xp] = request.xp
                it[xpToNext] = request.xpToNext
                it[updatedAt] = now
            }
        }

        CharacterStatsTable.deleteWhere { CharacterStatsTable.userId eq userId }
        CharacterSkillsTable.deleteWhere { CharacterSkillsTable.userId eq userId }
        insertStats(userId, request.stats)
        insertSkills(userId, request.skills)

        readSheet(userId)
    }

    // Adds experience to the character and (optionally) one skill, applying level-ups.
    open fun addExperience(
        userId: UUID,
        characterXp: Int,
        skillKey: String?,
        skillXp: Int,
    ): CharacterSheetDto = transaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        val exists = CharacterSheetsTable
            .selectAll()
            .where { CharacterSheetsTable.userId eq userId }
            .singleOrNull()
        if (exists == null) {
            seedDefault(userId)
        }

        if (characterXp > 0) {
            val row = CharacterSheetsTable
                .selectAll()
                .where { CharacterSheetsTable.userId eq userId }
                .single()

            var level = row[CharacterSheetsTable.level]
            var xp = row[CharacterSheetsTable.xp].toLong() + characterXp
            while (level < MAX_LEVEL && xp >= xpToNext(level)) {
                xp -= xpToNext(level)
                level++
            }
            val finalXp = if (level >= MAX_LEVEL) xp.coerceAtMost(xpToNext(MAX_LEVEL).toLong()) else xp

            CharacterSheetsTable.update({ CharacterSheetsTable.userId eq userId }) {
                it[CharacterSheetsTable.level] = level
                it[CharacterSheetsTable.xp] = finalXp.toInt()
                it[xpToNext] = xpToNext(level)
                it[updatedAt] = now
            }
        }

        if (!skillKey.isNullOrBlank() && skillXp > 0) {
            val skillRow = CharacterSkillsTable
                .selectAll()
                .where { (CharacterSkillsTable.userId eq userId) and (CharacterSkillsTable.skillKey eq skillKey) }
                .singleOrNull()

            if (skillRow != null) {
                var sLevel = skillRow[CharacterSkillsTable.level]
                var sXp = (skillRow[CharacterSkillsTable.progress] * xpToNext(sLevel)).toLong() + skillXp
                while (sLevel < MAX_LEVEL && sXp >= xpToNext(sLevel)) {
                    sXp -= xpToNext(sLevel)
                    sLevel++
                }
                val newProgress = if (sLevel >= MAX_LEVEL) 1.0
                else (sXp.toDouble() / xpToNext(sLevel)).coerceIn(0.0, 1.0)

                CharacterSkillsTable.update({
                    (CharacterSkillsTable.userId eq userId) and (CharacterSkillsTable.skillKey eq skillKey)
                }) {
                    it[CharacterSkillsTable.level] = sLevel
                    it[progress] = newProgress
                }
            }
        }

        readSheet(userId)
    }

    // --- helpers (must run inside an active transaction) ---

    private fun seedDefault(userId: UUID) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        CharacterSheetsTable.insert {
            it[CharacterSheetsTable.userId] = userId
            it[name] = DEFAULT_NAME
            it[level] = DEFAULT_LEVEL
            it[xp] = DEFAULT_XP
            it[xpToNext] = DEFAULT_XP_TO_NEXT
            it[createdAt] = now
            it[updatedAt] = now
        }
        insertStats(userId, defaultStats())
        insertSkills(userId, defaultSkills())
    }

    private fun insertStats(userId: UUID, stats: List<CharacterStatDto>) {
        stats.forEachIndexed { index, stat ->
            CharacterStatsTable.insert {
                it[id] = UUID.randomUUID()
                it[CharacterStatsTable.userId] = userId
                it[position] = index
                it[statKey] = stat.key
                it[name] = stat.name
                it[description] = stat.description
                it[value] = stat.value
            }
        }
    }

    private fun insertSkills(userId: UUID, skills: List<CharacterSkillDto>) {
        skills.forEachIndexed { index, skill ->
            CharacterSkillsTable.insert {
                it[id] = UUID.randomUUID()
                it[CharacterSkillsTable.userId] = userId
                it[position] = index
                it[skillKey] = skill.key
                it[name] = skill.name
                it[level] = skill.level
                it[progress] = skill.progress.coerceIn(0.0, 1.0)
            }
        }
    }

    private fun readSheet(userId: UUID): CharacterSheetDto {
        val sheet = CharacterSheetsTable
            .selectAll()
            .where { CharacterSheetsTable.userId eq userId }
            .single()

        val stats = CharacterStatsTable
            .selectAll()
            .where { CharacterStatsTable.userId eq userId }
            .orderBy(CharacterStatsTable.position, SortOrder.ASC)
            .map { row ->
                CharacterStatDto(
                    key = row[CharacterStatsTable.statKey],
                    name = row[CharacterStatsTable.name],
                    description = row[CharacterStatsTable.description],
                    value = row[CharacterStatsTable.value],
                )
            }

        val skills = CharacterSkillsTable
            .selectAll()
            .where { CharacterSkillsTable.userId eq userId }
            .orderBy(CharacterSkillsTable.position, SortOrder.ASC)
            .map { row ->
                CharacterSkillDto(
                    key = row[CharacterSkillsTable.skillKey],
                    name = row[CharacterSkillsTable.name],
                    level = row[CharacterSkillsTable.level],
                    progress = row[CharacterSkillsTable.progress],
                )
            }

        return CharacterSheetDto(
            name = sheet[CharacterSheetsTable.name],
            level = sheet[CharacterSheetsTable.level],
            xp = sheet[CharacterSheetsTable.xp],
            xpToNext = sheet[CharacterSheetsTable.xpToNext],
            stats = stats,
            skills = skills,
        )
    }

    private companion object {
        const val DEFAULT_NAME = "Мутлабок"
        const val DEFAULT_LEVEL = 1
        const val DEFAULT_XP = 0
        const val DEFAULT_XP_TO_NEXT = 100

        const val MAX_LEVEL = 100
        const val BASE_XP = 100

        // Experience required to advance FROM [level]: 100 * 2^(level-1), capped at level 100.
        // Clamped to Int range so it always fits the DB column.
        fun xpToNext(level: Int): Int {
            if (level >= MAX_LEVEL) return Int.MAX_VALUE
            val shift = level - 1
            if (shift >= 31) return Int.MAX_VALUE
            val value = BASE_XP.toLong() shl shift
            return if (value >= Int.MAX_VALUE.toLong()) Int.MAX_VALUE else value.toInt()
        }

        // All abilities and skills start at 1 by default.
        fun defaultStats(): List<CharacterStatDto> = listOf(
            CharacterStatDto("STRENGTH", "Сила", "Физическая мощь", 1),
            CharacterStatDto("DEXTERITY", "Ловкость", "Проворство, рефлексы и равновесие", 1),
            CharacterStatDto("CONSTITUTION", "Телосложение", "Здоровье и выносливость", 1),
            CharacterStatDto("INTELLIGENCE", "Интеллект", "Логика и память", 1),
            CharacterStatDto("WISDOM", "Мудрость", "Восприимчивость и ментальная устойчивость", 1),
            CharacterStatDto("CHARISMA", "Харизма", "Уверенность, самообладание и обаяние", 1),
        )

        fun defaultSkills(): List<CharacterSkillDto> = listOf(
            CharacterSkillDto("LUMBERJACK", "Лесоруб", 1, 0.0),
            CharacterSkillDto("CARPENTER", "Плотник", 1, 0.0),
            CharacterSkillDto("ARCHIVIST", "Архивариус", 1, 0.0),
        )
    }
}
