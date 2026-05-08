@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.example.mutlabocnotes.notes

import com.example.mutlabocnotes.database.DatabaseFactory
import com.example.mutlabocnotes.database.table.NoteChecklistItemsTable
import com.example.mutlabocnotes.database.table.NotesTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.ZoneOffset
import kotlin.uuid.Uuid

// Репозиторий для доступа к данным и работы с БД.
class NotesRepository {

    // Возвращает все заметки пользователя вместе с чеклистами.
    suspend fun getAllByUser(userId: Uuid): List<NoteModel> =
        DatabaseFactory.dbQuery {
            val noteRows = NotesTable
                .selectAll()
                .where { NotesTable.userId eq userId }
                .orderBy(NotesTable.updatedAt to SortOrder.DESC)
                .toList()

            val noteIds = noteRows.map { it[NotesTable.id] }

            // Загружаем чеклист одной пачкой и группируем по noteId, чтобы не делать N+1 запросов.
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

    // Возвращает одну заметку по id с проверкой владельца.
    suspend fun getById(userId: Uuid, noteId: Uuid): NoteModel? =
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

    // Создаёт новую заметку и связанный чеклист.
    suspend fun create(userId: Uuid, request: CreateNoteRequestDto): NoteModel {
        val noteId = Uuid.random()

        DatabaseFactory.dbQuery {
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
        }

        return getById(userId, noteId)!!
    }

    // Обновляет заметку и перезаписывает её чеклист.
    suspend fun update(userId: Uuid, noteId: Uuid, request: UpdateNoteRequestDto): NoteModel? {
        val updated = DatabaseFactory.dbQuery {
            // Сначала обновляем базовые поля заметки.
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
                false
            } else {
                // Затем пересоздаём чеклист целиком, чтобы порядок и состав точно совпадали с запросом.
                NoteChecklistItemsTable.deleteWhere { NoteChecklistItemsTable.noteId eq noteId }
                insertChecklist(noteId, normalizedChecklist(request.category, request.checklist))
                true
            }
        }

        return if (updated) getById(userId, noteId) else null
    }

    // Удаляет заметку, если она принадлежит текущему пользователю.
    suspend fun delete(userId: Uuid, noteId: Uuid): Boolean =
        DatabaseFactory.dbQuery {
            NotesTable.deleteWhere {
                (NotesTable.id eq noteId) and (NotesTable.userId eq userId)
            } > 0
        }

    // Обновляет статус выполнения заметки.
    suspend fun updateCompletion(userId: Uuid, noteId: Uuid, isCompleted: Boolean): NoteModel? {
        val updated = DatabaseFactory.dbQuery {
            NotesTable.update(
                where = { (NotesTable.id eq noteId) and (NotesTable.userId eq userId) }
            ) {
                it[NotesTable.isCompleted] = isCompleted
            } > 0
        }

        return if (updated) getById(userId, noteId) else null
    }

    // Сохраняет позиции чеклиста для конкретной заметки.
    private fun insertChecklist(noteId: Uuid, checklist: List<ChecklistItemDto>) {
        checklist.forEachIndexed { index, item ->
            NoteChecklistItemsTable.insert {
                it[id] = Uuid.random()
                it[this.noteId] = noteId
                it[position] = index
                it[text] = item.text
                it[isChecked] = item.isChecked
            }
        }
    }

    // Нормализует чеклист и обнуляет его для неподдерживаемых категорий.
    private fun normalizedChecklist(
        category: NoteCategory,
        checklist: List<ChecklistItemDto>
    ): List<ChecklistItemDto> =
        if (category == NoteCategory.SHOPPING) {
            checklist.map { it.copy(text = it.text.trim()) }
        } else {
            emptyList()
        }

    // Приводит текстовое поле к правилам выбранной категории.
    private fun normalizedContent(category: NoteCategory, content: String): String =
        if (category == NoteCategory.SHOPPING) "" else content

    // Выдаёт признак повторения только для задач.
    private fun normalizedIsRepeating(category: NoteCategory, isRepeating: Boolean): Boolean =
        category == NoteCategory.TASKS && isRepeating

    // Преобразует данные в нужный формат представления.
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
            isCompleted = this[NotesTable.isCompleted],
            createdAt = this[NotesTable.createdAt].toInstant().toEpochMilli(),
            updatedAt = this[NotesTable.updatedAt].toInstant().toEpochMilli()
        )

    // Преобразует данные в нужный формат представления.
    private fun Long.toOffsetDateTimeUtc() =
        Instant.ofEpochMilli(this).atOffset(ZoneOffset.UTC)
}
