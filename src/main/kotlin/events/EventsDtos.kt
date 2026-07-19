package com.example.mutlabocnotes.events

import com.example.mutlabocnotes.character.CharacterSheetDto
import com.example.mutlabocnotes.inventory.InventoryItemDto
import kotlinx.serialization.Serializable

// Тип события фокус-таймера. Порядок — от частого к редкому.
enum class FocusEventType {
    TEXT, CHARACTER_XP, SKILL_XP, ITEM, NEW_SKILL;

    companion object {
        fun fromKeyOrNull(key: String?): FocusEventType? =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) }
    }
}

// Предмет-награда события (обе локали — клиент кэширует каталог целиком).
@Serializable
data class FocusEventItemDto(
    val key: String,
    val nameRu: String,
    val nameEn: String,
    val descriptionRu: String = "",
    val descriptionEn: String = "",
    val icon: String = "",
    val slot: String? = null,
    val rarity: String = "COMMON",
    val bonusStatKey: String? = null,
    val bonusStatNameRu: String? = null,
    val bonusStatNameEn: String? = null,
    val bonusValue: Int? = null,
)

// Новый навык-награда события.
@Serializable
data class FocusEventNewSkillDto(
    val key: String,
    val nameRu: String,
    val nameEn: String,
)

// Одно событие каталога.
@Serializable
data class FocusEventDto(
    val key: String,
    val type: String,
    val weight: Int,
    val textRu: String,
    val textEn: String,
    val characterXp: Int = 0,
    val skillXp: Int = 0,
    val item: FocusEventItemDto? = null,
    val newSkill: FocusEventNewSkillDto? = null,
)

// Ответ GET /events — весь каталог для кэширования на клиенте.
@Serializable
data class FocusEventsCatalogDto(
    val events: List<FocusEventDto> = emptyList(),
)

// Тело POST /events/claims. [skillKey] — навык сессии для SKILL_XP-событий.
// [locale] ("ru"/"en") определяет язык имени предмета/навыка, записываемого пользователю.
@Serializable
data class FocusEventClaimRequestDto(
    val operationId: String,
    val eventKey: String,
    val skillKey: String? = null,
    val locale: String? = null,
)

// Результат клейма: свежий лист персонажа и выданный предмет (если событие с предметом
// и предмета у пользователя ещё не было).
@Serializable
data class FocusEventClaimResponseDto(
    val sheet: CharacterSheetDto,
    val grantedItem: InventoryItemDto? = null,
)
