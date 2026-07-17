package com.example.mutlabocnotes.character

import kotlinx.serialization.Serializable

// DTO одной характеристики персонажа.
@Serializable
data class CharacterStatDto(
    val key: String,
    val name: String,
    val description: String,
    val value: Int,
)

// DTO одного навыка персонажа.
@Serializable
data class CharacterSkillDto(
    val key: String,
    val name: String,
    val level: Int,
    val progress: Double,
)

// Полный лист персонажа, отдаваемый клиенту.
@Serializable
data class CharacterSheetDto(
    val name: String,
    val level: Int,
    val xp: Int,
    val xpToNext: Int,
    val stats: List<CharacterStatDto>,
    val skills: List<CharacterSkillDto>,
    val wallet: CharacterWalletDto = CharacterWalletDto(),
)

@Serializable
data class CharacterWalletDto(
    val earnedCoins: Int = 0,
    val spentCoins: Int = 0,
    val availableCoins: Int = 0,
)

// Тело запроса на полное обновление листа персонажа.
@Serializable
data class CharacterUpdateRequestDto(
    val name: String,
    val level: Int,
    val xp: Int,
    val xpToNext: Int,
    val stats: List<CharacterStatDto>,
    val skills: List<CharacterSkillDto>,
)

// Тело запроса на начисление опыта: персонажу и (опционально) одному навыку.
@Serializable
data class CharacterXpRequestDto(
    val characterXp: Int = 0,
    val skillKey: String? = null,
    val skillXp: Int = 0,
    val operationId: String? = null,
)

@Serializable
data class CharacterStatUpgradeRequestDto(
    val operationId: String,
    val statKey: String,
)

@Serializable
data class CharacterRenameRequestDto(
    val operationId: String,
    val name: String,
)
