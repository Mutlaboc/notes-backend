package com.example.mutlabocnotes.notes

import com.example.mutlabocnotes.database.DatabaseFactory
import com.example.mutlabocnotes.database.table.NoteChecklistItemsTable
import com.example.mutlabocnotes.database.table.NotesTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class NotesRepository {

    suspend fun getAllByUser(userId: UUID): List<NoteModel> =
        DatabaseFactory.dbQuery {
            val noteRows = NotesTable
                .selectAll()
                .where { NotesTable.userId eq userId }
                .orderBy(NotesTable.updatedAt to SortOrder.DESC)
                .toList()

            val noteIds = noteRows.map { it[NotesTable.id] }

            val checklistByNoteId = if (noteIds.isEmpty()) {
                emptyMap()
            } else {
                NoteChecklistItemsTable
                    .selectAll()
                    .where { NoteChecklistItemsTable.noteId inList noteIds }
                    .orderBy(NoteChecklistItemsTable.position to SortOrder.ASC)
                    .toList()
                    .groupBy { it[NoteChecklistItemsTable.noteId] }
                    .mapValues { (_, rows) ->
                        rows.map { row ->
                            ChecklistItemModel(
                                text = row[NoteChecklistItemsTable.text],
                                isChecked = row[NoteChecklistItemsTable.isChecked]
                            )
                        }
                    }
            }

            noteRows.map { row ->
                val noteId = row[NotesTable.id]
                row.toNoteModel(checklistByNoteId[noteId].orEmpty())
            }
        }

    suspend fun getById(userId: UUID, noteId: UUID): NoteModel? =
        DatabaseFactory.dbQuery {
            val noteRow = NotesTable
                .selectAll()
                .where { (NotesTable.id eq noteId) and (NotesTable.userId eq userId) }
                .singleOrNull()
                ?: return@dbQuery null

            val checklist = NoteChecklistItemsTable
                .selectAll()
                .where { NoteChecklistItemsTable.noteId eq noteId }
                .orderBy(NoteChecklistItemsTable.position to SortOrder.ASC)
                .toList()
                .map { row ->
                    ChecklistItemModel(
                        text = row[NoteChecklistItemsTable.text],
                        isChecked = row[NoteChecklistItemsTable.isChecked]
                    )
                }

            noteRow.toNoteModel(checklist)
        }

    suspend fun create(userId: UUID, request: CreateNoteRequestDto): NoteModel =
        DatabaseFactory.dbQuery {
            val noteId = UUID.randomUUID()

            NotesTable.insert {
                it[id] = noteId
                it[this.userId] = userId
                it[title] = request.title
                it[content] = normalizedContent(request.category, request.content)
                it[category] = request.category.name
                it[deadlineAt] = request.deadlineMillis?.toOffsetDateTimeUtc()
                it[isRepeating] = normalizedIsRepeating(request.category, request.isRepeating)
                it[coinCount] = request.coinCount
                it[isCompleted] = request.isCompleted
            }

            insertChecklist(noteId, normalizedChecklist(request.category, request.checklist))
            getById(userId, noteId)!!
        }

    suspend fun update(userId: UUID, noteId: UUID, request: UpdateNoteRequestDto): NoteModel? =
        DatabaseFactory.dbQuery {
            val updatedRows = NotesTable.update(
                where = { (NotesTable.id eq noteId) and (NotesTable.userId eq userId) }
            ) {
                it[title] = request.title
                it[content] = normalizedContent(request.category, request.content)
                it[category] = request.category.name
                it[deadlineAt] = request.deadlineMillis?.toOffsetDateTimeUtc()
                it[isRepeating] = normalizedIsRepeating(request.category, request.isRepeating)
                it[coinCount] = request.coinCount
                it[isCompleted] = request.isCompleted
            }

            if (updatedRows == 0) {
                return@dbQuery null
            }

            NoteChecklistItemsTable.deleteWhere { NoteChecklistItemsTable.noteId eq noteId }
            insertChecklist(noteId, normalizedChecklist(request.category, request.checklist))

            getById(userId, noteId)
        }

    suspend fun delete(userId: UUID, noteId: UUID): Boolean =
        DatabaseFactory.dbQuery {
            NotesTable.deleteWhere {
                (NotesTable.id eq noteId) and (NotesTable.userId eq userId)
            } > 0
        }

    suspend fun updateCompletion(userId: UUID, noteId: UUID, isCompleted: Boolean): NoteModel? =
        DatabaseFactory.dbQuery {
            val updatedRows = NotesTable.update(
                where = { (NotesTable.id eq noteId) and (NotesTable.userId eq userId) }
            ) {
                it[NotesTable.isCompleted] = isCompleted
            }

            if (updatedRows == 0) {
                return@dbQuery null
            }

            getById(userId, noteId)
        }

    private fun insertChecklist(noteId: UUID, checklist: List<ChecklistItemDto>) {
        checklist.forEachIndexed { index, item ->
            NoteChecklistItemsTable.insert {
                it[id] = UUID.randomUUID()
                it[this.noteId] = noteId
                it[position] = index
                it[text] = item.text
                it[isChecked] = item.isChecked
            }
        }
    }

    private fun normalizedChecklist(
        category: NoteCategory,
        checklist: List<ChecklistItemDto>
    ): List<ChecklistItemDto> =
        if (category == NoteCategory.SHOPPING) {
            checklist.map { it.copy(text = it.text.trim()) }
        } else {
            emptyList()
        }

    private fun normalizedContent(category: NoteCategory, content: String): String =
        if (category == NoteCategory.SHOPPING) "" else content

    private fun normalizedIsRepeating(category: NoteCategory, isRepeating: Boolean): Boolean =
        category == NoteCategory.TASKS && isRepeating

    private fun ResultRow.toNoteModel(checklist: List<ChecklistItemModel>): NoteModel =
        NoteModel(
            id = this[NotesTable.id],
            userId = this[NotesTable.userId],
            title = this[NotesTable.title],
            content = this[NotesTable.content],
            category = NoteCategory.valueOf(this[NotesTable.category]),
            checklist = checklist,
            deadlineMillis = this[NotesTable.deadlineAt]?.toInstant()?.toEpochMilli(),
            isRepeating = this[NotesTable.isRepeating],
            coinCount = this[NotesTable.coinCount],
            isCompleted = this[NotesTable.isCompleted]
        )

    private fun Long.toOffsetDateTimeUtc() =
        Instant.ofEpochMilli(this).atOffset(ZoneOffset.UTC)
}