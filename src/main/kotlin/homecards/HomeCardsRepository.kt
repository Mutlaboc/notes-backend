package com.example.mutlabocnotes.homecards

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

// Repository for home-card persistence and ownership checks.
open class HomeCardsRepository {

    open fun getAllForUser(userId: UUID): List<HomeCardDto> = transaction {
        HomeCardsTable
            .selectAll()
            .where { HomeCardsTable.userId eq userId }
            .orderBy(HomeCardsTable.updatedAt, SortOrder.DESC)
            .map { row -> row.toHomeCardDto() }
    }

    open fun getByIdForUser(userId: UUID, cardId: String): HomeCardDto? = transaction {
        val uuid = runCatching { UUID.fromString(cardId) }.getOrNull() ?: return@transaction null

        HomeCardsTable
            .selectAll()
            .where { (HomeCardsTable.id eq uuid) and (HomeCardsTable.userId eq userId) }
            .singleOrNull()
            ?.toHomeCardDto()
    }

    open fun createForUser(userId: UUID, request: HomeCardUpsertRequestDto): HomeCardDto = transaction {
        val cardId = UUID.randomUUID()
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        HomeCardsTable.insert {
            it[HomeCardsTable.id] = cardId
            it[HomeCardsTable.userId] = userId
            it[HomeCardsTable.title] = request.title
            it[HomeCardsTable.section] = request.section
            it[HomeCardsTable.note] = request.note
            it[HomeCardsTable.createdAt] = now
            it[HomeCardsTable.updatedAt] = now
        }

        request.fields.forEachIndexed { index, field ->
            HomeCardFieldsTable.insert {
                it[HomeCardFieldsTable.id] = UUID.randomUUID()
                it[HomeCardFieldsTable.cardId] = cardId
                it[HomeCardFieldsTable.position] = index
                it[HomeCardFieldsTable.fieldKey] = field.key
                it[HomeCardFieldsTable.fieldValue] = field.value
                it[HomeCardFieldsTable.createdAt] = now
            }
        }

        request.links.forEachIndexed { index, link ->
            HomeCardLinksTable.insert {
                it[HomeCardLinksTable.id] = UUID.randomUUID()
                it[HomeCardLinksTable.cardId] = cardId
                it[HomeCardLinksTable.position] = index
                it[HomeCardLinksTable.url] = link
                it[HomeCardLinksTable.createdAt] = now
            }
        }

        HomeCardsTable
            .selectAll()
            .where { (HomeCardsTable.id eq cardId) and (HomeCardsTable.userId eq userId) }
            .single()
            .toHomeCardDto()
    }

    open fun updateForUser(userId: UUID, cardId: String, request: HomeCardUpsertRequestDto): HomeCardDto? = transaction {
        val uuid = runCatching { UUID.fromString(cardId) }.getOrNull() ?: return@transaction null

        HomeCardsTable
            .selectAll()
            .where { (HomeCardsTable.id eq uuid) and (HomeCardsTable.userId eq userId) }
            .singleOrNull()
            ?: return@transaction null

        val now = OffsetDateTime.now(ZoneOffset.UTC)

        HomeCardsTable.update({ (HomeCardsTable.id eq uuid) and (HomeCardsTable.userId eq userId) }) {
            it[HomeCardsTable.title] = request.title
            it[HomeCardsTable.section] = request.section
            it[HomeCardsTable.note] = request.note
            it[HomeCardsTable.updatedAt] = now
        }

        HomeCardFieldsTable.deleteWhere { HomeCardFieldsTable.cardId eq uuid }
        HomeCardLinksTable.deleteWhere { HomeCardLinksTable.cardId eq uuid }

        request.fields.forEachIndexed { index, field ->
            HomeCardFieldsTable.insert {
                it[HomeCardFieldsTable.id] = UUID.randomUUID()
                it[HomeCardFieldsTable.cardId] = uuid
                it[HomeCardFieldsTable.position] = index
                it[HomeCardFieldsTable.fieldKey] = field.key
                it[HomeCardFieldsTable.fieldValue] = field.value
                it[HomeCardFieldsTable.createdAt] = now
            }
        }

        request.links.forEachIndexed { index, link ->
            HomeCardLinksTable.insert {
                it[HomeCardLinksTable.id] = UUID.randomUUID()
                it[HomeCardLinksTable.cardId] = uuid
                it[HomeCardLinksTable.position] = index
                it[HomeCardLinksTable.url] = link
                it[HomeCardLinksTable.createdAt] = now
            }
        }

        HomeCardsTable
            .selectAll()
            .where { (HomeCardsTable.id eq uuid) and (HomeCardsTable.userId eq userId) }
            .single()
            .toHomeCardDto()
    }

    open fun deleteForUser(userId: UUID, cardId: String): Boolean = transaction {
        val uuid = runCatching { UUID.fromString(cardId) }.getOrNull() ?: return@transaction false

        val deleted = HomeCardsTable.deleteWhere {
            (HomeCardsTable.id eq uuid) and (HomeCardsTable.userId eq userId)
        }

        deleted > 0
    }

    private fun ResultRow.toHomeCardDto(): HomeCardDto {
        val cardId = this[HomeCardsTable.id]
        val fields = HomeCardFieldsTable
            .selectAll()
            .where { HomeCardFieldsTable.cardId eq cardId }
            .orderBy(HomeCardFieldsTable.position, SortOrder.ASC)
            .map { fieldRow ->
                HomeFieldDto(
                    key = fieldRow[HomeCardFieldsTable.fieldKey],
                    value = fieldRow[HomeCardFieldsTable.fieldValue],
                )
            }

        val links = HomeCardLinksTable
            .selectAll()
            .where { HomeCardLinksTable.cardId eq cardId }
            .orderBy(HomeCardLinksTable.position, SortOrder.ASC)
            .map { linkRow ->
                linkRow[HomeCardLinksTable.url]
            }

        return HomeCardDto(
            id = cardId.toString(),
            title = this[HomeCardsTable.title],
            section = this[HomeCardsTable.section],
            fields = fields,
            note = this[HomeCardsTable.note],
            links = links,
            createdAt = this[HomeCardsTable.createdAt].toInstant().toEpochMilli(),
            updatedAt = this[HomeCardsTable.updatedAt].toInstant().toEpochMilli(),
        )
    }
}
