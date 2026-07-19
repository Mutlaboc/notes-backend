package com.example.mutlabocnotes.events

import com.example.mutlabocnotes.character.CharacterRepository
import com.example.mutlabocnotes.character.CharacterSheetsTable
import com.example.mutlabocnotes.character.CharacterSkillsTable
import com.example.mutlabocnotes.inventory.InventoryItemBonusesTable
import com.example.mutlabocnotes.inventory.InventoryItemDto
import com.example.mutlabocnotes.inventory.InventoryItemsTable
import com.example.mutlabocnotes.inventory.InventoryRepository
import com.example.mutlabocnotes.inventory.ItemBonusDto
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Репозиторий событий фокус-таймера: чтение глобального каталога и идемпотентное
 * начисление наград события (опыт персонажа / опыт навыка / предмет / новый навык).
 * Клиент роллит событие сам (офлайн-first) и репортит его сюда с operationId.
 */
open class EventsRepository(
    private val characterRepository: CharacterRepository = CharacterRepository(),
    private val inventoryRepository: InventoryRepository = InventoryRepository(),
) {

    // Полный каталог событий — клиент кэширует его в Room для офлайна.
    open fun catalog(): FocusEventsCatalogDto = transaction {
        FocusEventsCatalogDto(
            events = FocusEventsTable.selectAll()
                .orderBy(FocusEventsTable.eventKey, SortOrder.ASC)
                .map { it.toDto() },
        )
    }

    /**
     * Идемпотентно применяет награды события [eventKey] к пользователю [userId].
     * Ошибки: [NoSuchElementException] — неизвестное событие (404).
     */
    open fun claim(
        userId: UUID,
        operationId: UUID,
        eventKey: String,
        skillKey: String?,
        locale: String?,
    ): FocusEventClaimResponseDto = transaction {
        // Лист персонажа существует и залочен — сериализуем конкурентные клеймы.
        characterRepository.getOrCreateForUser(userId)
        CharacterSheetsTable.selectAll()
            .where { CharacterSheetsTable.userId eq userId }
            .forUpdate()
            .single()

        val alreadyClaimed = FocusEventClaimsTable.selectAll().where {
            (FocusEventClaimsTable.userId eq userId) and (FocusEventClaimsTable.operationId eq operationId)
        }.singleOrNull() != null
        if (alreadyClaimed) {
            return@transaction FocusEventClaimResponseDto(sheet = characterRepository.getOrCreateForUser(userId))
        }

        val event = FocusEventsTable.selectAll()
            .where { FocusEventsTable.eventKey eq eventKey }
            .singleOrNull() ?: throw NoSuchElementException("Unknown event")

        val english = locale?.trim()?.lowercase()?.startsWith("en") == true
        var grantedItem: InventoryItemDto? = null

        when (FocusEventType.fromKeyOrNull(event[FocusEventsTable.eventType])) {
            FocusEventType.TEXT, null -> Unit

            FocusEventType.CHARACTER_XP -> {
                val xp = event[FocusEventsTable.characterXp]
                if (xp > 0) characterRepository.addExperience(userId, xp, null, 0)
            }

            FocusEventType.SKILL_XP -> {
                val xp = event[FocusEventsTable.skillXp]
                if (xp > 0 && !skillKey.isNullOrBlank()) {
                    characterRepository.addExperience(userId, 0, skillKey, xp)
                }
            }

            FocusEventType.NEW_SKILL -> grantNewSkill(userId, event, english)

            FocusEventType.ITEM -> grantedItem = grantItem(userId, event, english)
        }

        FocusEventClaimsTable.insert {
            it[FocusEventClaimsTable.operationId] = operationId
            it[FocusEventClaimsTable.userId] = userId
            it[FocusEventClaimsTable.eventKey] = eventKey
            it[createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }

        FocusEventClaimResponseDto(
            sheet = characterRepository.getOrCreateForUser(userId),
            grantedItem = grantedItem,
        )
    }

    // --- helpers (внутри активной транзакции) ---

    // Добавляет навык события, если его у пользователя ещё нет (уровень 1, прогресс 0).
    private fun grantNewSkill(userId: UUID, event: ResultRow, english: Boolean) {
        val skillKey = event[FocusEventsTable.newSkillKey] ?: return
        val exists = CharacterSkillsTable.selectAll().where {
            (CharacterSkillsTable.userId eq userId) and (CharacterSkillsTable.skillKey eq skillKey)
        }.singleOrNull() != null
        if (exists) return

        val nextPosition = CharacterSkillsTable.selectAll()
            .where { CharacterSkillsTable.userId eq userId }
            .maxOfOrNull { it[CharacterSkillsTable.position] }?.plus(1) ?: 0
        val name = (if (english) event[FocusEventsTable.newSkillNameEn] else event[FocusEventsTable.newSkillNameRu])
            ?: skillKey

        CharacterSkillsTable.insert {
            it[id] = UUID.randomUUID()
            it[CharacterSkillsTable.userId] = userId
            it[position] = nextPosition
            it[CharacterSkillsTable.skillKey] = skillKey
            it[CharacterSkillsTable.name] = name
            it[level] = 1
            it[progress] = 0.0
        }
    }

    // Кладёт предмет события в инвентарь, если такого предмета у пользователя ещё нет.
    // Возвращает выданный предмет или null (дубликат / у события нет предмета).
    private fun grantItem(userId: UUID, event: ResultRow, english: Boolean): InventoryItemDto? {
        val itemKey = event[FocusEventsTable.itemKey] ?: return null
        // Сначала гарантируем стартовый набор: иначе первый предмет из события
        // помешал бы seedIfMissing выдать стартовые предметы позже.
        inventoryRepository.getOrCreateForUser(userId)

        val exists = InventoryItemsTable.selectAll().where {
            (InventoryItemsTable.userId eq userId) and (InventoryItemsTable.itemKey eq itemKey)
        }.singleOrNull() != null
        if (exists) return null

        val nextPosition = InventoryItemsTable.selectAll()
            .where { InventoryItemsTable.userId eq userId }
            .maxOfOrNull { it[InventoryItemsTable.position] }?.plus(1) ?: 0

        val name = (if (english) event[FocusEventsTable.itemNameEn] else event[FocusEventsTable.itemNameRu]) ?: itemKey
        val description = (if (english) event[FocusEventsTable.itemDescriptionEn] else event[FocusEventsTable.itemDescriptionRu]).orEmpty()
        val icon = event[FocusEventsTable.itemIcon].orEmpty()
        val slot = event[FocusEventsTable.itemSlot]
        val rarity = event[FocusEventsTable.itemRarity] ?: "COMMON"
        val bonus = event[FocusEventsTable.itemBonusStatKey]?.let { statKey ->
            ItemBonusDto(
                statKey = statKey,
                statName = (if (english) event[FocusEventsTable.itemBonusStatNameEn] else event[FocusEventsTable.itemBonusStatNameRu]) ?: statKey,
                value = event[FocusEventsTable.itemBonusValue] ?: 0,
            )
        }

        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val itemId = UUID.randomUUID()
        InventoryItemsTable.insert {
            it[id] = itemId
            it[InventoryItemsTable.userId] = userId
            it[InventoryItemsTable.itemKey] = itemKey
            it[position] = nextPosition
            it[InventoryItemsTable.name] = name
            it[InventoryItemsTable.description] = description
            it[InventoryItemsTable.icon] = icon
            it[InventoryItemsTable.slot] = slot
            it[InventoryItemsTable.rarity] = rarity
            it[equippedSlot] = null
            it[createdAt] = now
            it[updatedAt] = now
        }
        if (bonus != null && bonus.value != 0) {
            InventoryItemBonusesTable.insert {
                it[InventoryItemBonusesTable.itemId] = itemId
                it[position] = 0
                it[statKey] = bonus.statKey
                it[statName] = bonus.statName
                it[value] = bonus.value
            }
        }

        return InventoryItemDto(
            id = itemKey,
            name = name,
            description = description,
            icon = icon,
            slot = slot,
            rarity = rarity,
            bonuses = if (bonus != null && bonus.value != 0) listOf(bonus) else emptyList(),
            equippedSlot = null,
        )
    }

    private fun ResultRow.toDto(): FocusEventDto = FocusEventDto(
        key = this[FocusEventsTable.eventKey],
        type = this[FocusEventsTable.eventType],
        weight = this[FocusEventsTable.weight],
        textRu = this[FocusEventsTable.textRu],
        textEn = this[FocusEventsTable.textEn],
        characterXp = this[FocusEventsTable.characterXp],
        skillXp = this[FocusEventsTable.skillXp],
        item = this[FocusEventsTable.itemKey]?.let { key ->
            FocusEventItemDto(
                key = key,
                nameRu = this[FocusEventsTable.itemNameRu] ?: key,
                nameEn = this[FocusEventsTable.itemNameEn] ?: key,
                descriptionRu = this[FocusEventsTable.itemDescriptionRu].orEmpty(),
                descriptionEn = this[FocusEventsTable.itemDescriptionEn].orEmpty(),
                icon = this[FocusEventsTable.itemIcon].orEmpty(),
                slot = this[FocusEventsTable.itemSlot],
                rarity = this[FocusEventsTable.itemRarity] ?: "COMMON",
                bonusStatKey = this[FocusEventsTable.itemBonusStatKey],
                bonusStatNameRu = this[FocusEventsTable.itemBonusStatNameRu],
                bonusStatNameEn = this[FocusEventsTable.itemBonusStatNameEn],
                bonusValue = this[FocusEventsTable.itemBonusValue],
            )
        },
        newSkill = this[FocusEventsTable.newSkillKey]?.let { key ->
            FocusEventNewSkillDto(
                key = key,
                nameRu = this[FocusEventsTable.newSkillNameRu] ?: key,
                nameEn = this[FocusEventsTable.newSkillNameEn] ?: key,
            )
        },
    )
}
