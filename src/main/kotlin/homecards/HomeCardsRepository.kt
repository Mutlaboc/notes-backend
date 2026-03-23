package com.example.mutlabocnotes.homecards

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

class HomeCardsRepository {

    fun getAllForFirebaseUid(firebaseUid: String): List<HomeCardDto> = transaction {
        val userId = resolveOrCreateUserId(firebaseUid)

        HomeCardsTable
            .selectAll()
            .where { HomeCardsTable.userId eq userId }
            .orderBy(HomeCardsTable.updatedAt, SortOrder.DESC)
            .map { row ->
                val cardId = row[HomeCardsTable.id]

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

                HomeCardDto(
                    id = cardId.toString(),
                    title = row[HomeCardsTable.title],
                    section = row[HomeCardsTable.section],
                    fields = fields,
                    note = row[HomeCardsTable.note],
                    links = links,
                    createdAt = row[HomeCardsTable.createdAt].toInstant().toEpochMilli(),
                    updatedAt = row[HomeCardsTable.updatedAt].toInstant().toEpochMilli(),
                )
            }
    }

    fun getByIdForFirebaseUid(firebaseUid: String, cardId: String): HomeCardDto? = transaction {
        val userId = resolveOrCreateUserId(firebaseUid)
        val uuid = runCatching { UUID.fromString(cardId) }.getOrNull() ?: return@transaction null

        val row = HomeCardsTable
            .selectAll()
            .where { (HomeCardsTable.id eq uuid) and (HomeCardsTable.userId eq userId) }
            .singleOrNull()
            ?: return@transaction null

        val fields = HomeCardFieldsTable
            .selectAll()
            .where { HomeCardFieldsTable.cardId eq uuid }
            .orderBy(HomeCardFieldsTable.position, SortOrder.ASC)
            .map { fieldRow ->
                HomeFieldDto(
                    key = fieldRow[HomeCardFieldsTable.fieldKey],
                    value = fieldRow[HomeCardFieldsTable.fieldValue],
                )
            }

        val links = HomeCardLinksTable
            .selectAll()
            .where { HomeCardLinksTable.cardId eq uuid }
            .orderBy(HomeCardLinksTable.position, SortOrder.ASC)
            .map { linkRow ->
                linkRow[HomeCardLinksTable.url]
            }

        HomeCardDto(
            id = uuid.toString(),
            title = row[HomeCardsTable.title],
            section = row[HomeCardsTable.section],
            fields = fields,
            note = row[HomeCardsTable.note],
            links = links,
            createdAt = row[HomeCardsTable.createdAt].toInstant().toEpochMilli(),
            updatedAt = row[HomeCardsTable.updatedAt].toInstant().toEpochMilli(),
        )
    }

    fun createForFirebaseUid(firebaseUid: String, request: HomeCardUpsertRequestDto): HomeCardDto = transaction {
        val userId = resolveOrCreateUserId(firebaseUid)
        val cardId = UUID.randomUUID()
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val createdAtValue = if (request.createdAt > 0) epochMillisToOffsetDateTime(request.createdAt) else now
        val updatedAtValue = if (request.updatedAt > 0) epochMillisToOffsetDateTime(request.updatedAt) else now

        HomeCardsTable.insert {
            it[HomeCardsTable.id] = cardId
            it[HomeCardsTable.userId] = userId
            it[HomeCardsTable.title] = request.title
            it[HomeCardsTable.section] = request.section
            it[HomeCardsTable.note] = request.note
            it[HomeCardsTable.createdAt] = createdAtValue
            it[HomeCardsTable.updatedAt] = updatedAtValue
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

        HomeCardDto(
            id = cardId.toString(),
            title = request.title,
            section = request.section,
            fields = request.fields,
            note = request.note,
            links = request.links,
            createdAt = createdAtValue.toInstant().toEpochMilli(),
            updatedAt = updatedAtValue.toInstant().toEpochMilli(),
        )
    }

    fun updateForFirebaseUid(firebaseUid: String, cardId: String, request: HomeCardUpsertRequestDto): HomeCardDto? = transaction {
        val userId = resolveOrCreateUserId(firebaseUid)
        val uuid = runCatching { UUID.fromString(cardId) }.getOrNull() ?: return@transaction null

        val existing = HomeCardsTable
            .selectAll()
            .where { (HomeCardsTable.id eq uuid) and (HomeCardsTable.userId eq userId) }
            .singleOrNull()
            ?: return@transaction null

        val updatedAtValue = if (request.updatedAt > 0) epochMillisToOffsetDateTime(request.updatedAt) else OffsetDateTime.now(ZoneOffset.UTC)
        val createdAtValue = if (request.createdAt > 0) epochMillisToOffsetDateTime(request.createdAt) else existing[HomeCardsTable.createdAt]

        HomeCardsTable.update({ (HomeCardsTable.id eq uuid) and (HomeCardsTable.userId eq userId) }) {
            it[HomeCardsTable.title] = request.title
            it[HomeCardsTable.section] = request.section
            it[HomeCardsTable.note] = request.note
            it[HomeCardsTable.createdAt] = createdAtValue
            it[HomeCardsTable.updatedAt] = updatedAtValue
        }

        HomeCardFieldsTable.deleteWhere { HomeCardFieldsTable.cardId eq uuid }
        HomeCardLinksTable.deleteWhere { HomeCardLinksTable.cardId eq uuid }

        val now = OffsetDateTime.now(ZoneOffset.UTC)

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

        HomeCardDto(
            id = uuid.toString(),
            title = request.title,
            section = request.section,
            fields = request.fields,
            note = request.note,
            links = request.links,
            createdAt = createdAtValue.toInstant().toEpochMilli(),
            updatedAt = updatedAtValue.toInstant().toEpochMilli(),
        )
    }

    fun deleteForFirebaseUid(firebaseUid: String, cardId: String): Boolean = transaction {
        val userId = resolveOrCreateUserId(firebaseUid)
        val uuid = runCatching { UUID.fromString(cardId) }.getOrNull() ?: return@transaction false

        val deleted = HomeCardsTable.deleteWhere {
            (HomeCardsTable.id eq uuid) and (HomeCardsTable.userId eq userId)
        }

        deleted > 0
    }

    private fun resolveOrCreateUserId(firebaseUid: String): UUID {
        val existing = HomeCardsUsersLookupTable
            .selectAll()
            .where { HomeCardsUsersLookupTable.firebaseUid eq firebaseUid }
            .singleOrNull()

        if (existing != null) {
            return existing[HomeCardsUsersLookupTable.id]
        }

        val userId = UUID.randomUUID()

        HomeCardsUsersLookupTable.insert {
            it[HomeCardsUsersLookupTable.id] = userId
            it[HomeCardsUsersLookupTable.firebaseUid] = firebaseUid
        }

        return userId
    }

    private fun epochMillisToOffsetDateTime(value: Long): OffsetDateTime =
        OffsetDateTime.ofInstant(Instant.ofEpochMilli(value), ZoneOffset.UTC)
}
