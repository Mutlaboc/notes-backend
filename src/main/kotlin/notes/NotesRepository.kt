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
import java.time.ZonedDateTime
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
                it[deadlineAt] = normalizedDeadline(request.category, request.deadlineMillis)?.toOffsetDateTimeUtc()
                it[startAt] = normalizedStart(request.category, request.startAtMillis)?.toOffsetDateTimeUtc()
                it[durationMinutes] = normalizedDuration(request.category, request.durationMinutes)
                it[repeatRule] = normalizedRepeatRule(request.category, request.repeatRule).name
                it[recurrenceAnchorDay] = normalizedStart(request.category, request.startAtMillis)
                    ?.let(::utcDayOfMonth)
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
                it[deadlineAt] = normalizedDeadline(request.category, request.deadlineMillis)?.toOffsetDateTimeUtc()
                it[startAt] = normalizedStart(request.category, request.startAtMillis)?.toOffsetDateTimeUtc()
                it[durationMinutes] = normalizedDuration(request.category, request.durationMinutes)
                it[repeatRule] = normalizedRepeatRule(request.category, request.repeatRule).name
                it[recurrenceAnchorDay] = normalizedStart(request.category, request.startAtMillis)
                    ?.let(::utcDayOfMonth)
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
    suspend fun updateCompletion(
        userId: Uuid,
        noteId: Uuid,
        isCompleted: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): Pair<NoteModel, NoteModel?>? = DatabaseFactory.dbQuery {
        val currentRow = NotesTable.selectAll()
            .where { (NotesTable.id eq noteId) and (NotesTable.userId eq userId) }
            .singleOrNull() ?: return@dbQuery null

        NotesTable.update(
            where = { (NotesTable.id eq noteId) and (NotesTable.userId eq userId) }
        ) { it[NotesTable.isCompleted] = isCompleted }

        val nextId = if (isCompleted && currentRow[NotesTable.category] == NoteCategory.RECURRING_TASKS.name) {
            NotesTable.selectAll()
                .where { NotesTable.recurrenceParentId eq noteId }
                .singleOrNull()
                ?.get(NotesTable.id)
                ?: createNextOccurrence(currentRow, noteId, nowMillis)
        } else {
            null
        }

        val completedChecklist = loadChecklist(noteId)
        val completed = NotesTable.selectAll()
            .where { NotesTable.id eq noteId }
            .single()
            .toNoteModel(completedChecklist)
        val next = nextId?.let { id ->
            NotesTable.selectAll().where { NotesTable.id eq id }.single().toNoteModel(emptyList())
        }
        completed to next
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
    private fun normalizedRepeatRule(category: NoteCategory, repeatRule: RepeatRule): RepeatRule =
        if (category == NoteCategory.RECURRING_TASKS) {
            require(repeatRule != RepeatRule.NONE) { "Recurring task requires repeat rule" }
            repeatRule
        } else RepeatRule.NONE

    private fun normalizedDeadline(category: NoteCategory, deadlineMillis: Long?): Long? =
        if (category == NoteCategory.TASKS) deadlineMillis else null

    private fun normalizedStart(category: NoteCategory, startAtMillis: Long?): Long? =
        if (category == NoteCategory.RECURRING_TASKS) {
            requireNotNull(startAtMillis) { "Recurring task requires startAtMillis" }
        } else null

    private fun normalizedDuration(category: NoteCategory, durationMinutes: Long?): Long? =
        if (category == NoteCategory.RECURRING_TASKS) {
            requireNotNull(durationMinutes) { "Recurring task requires durationMinutes" }
                .also { require(it > 0) { "durationMinutes must be positive" } }
        } else null

    private fun loadChecklist(noteId: Uuid): List<ChecklistItemModel> =
        NoteChecklistItemsTable.selectAll()
            .where { NoteChecklistItemsTable.noteId eq noteId }
            .orderBy(NoteChecklistItemsTable.position to SortOrder.ASC)
            .map { row ->
                ChecklistItemModel(
                    text = row[NoteChecklistItemsTable.text],
                    isChecked = row[NoteChecklistItemsTable.isChecked]
                )
            }

    private fun createNextOccurrence(row: ResultRow, parentId: Uuid, nowMillis: Long): Uuid {
        val startMillis = requireNotNull(row[NotesTable.startAt]?.toInstant()?.toEpochMilli())
        val rule = RepeatRule.valueOf(row[NotesTable.repeatRule])
        val anchorDay = row[NotesTable.recurrenceAnchorDay]
            ?: utcDayOfMonth(startMillis)
        val nextStart = nextOccurrenceStart(startMillis, nowMillis, rule, anchorDay)
        val nextId = Uuid.random()
        NotesTable.insert {
            it[id] = nextId
            it[userId] = row[NotesTable.userId]
            it[title] = row[NotesTable.title]
            it[content] = row[NotesTable.content]
            it[category] = NoteCategory.RECURRING_TASKS.name
            it[deadlineAt] = null
            it[startAt] = nextStart.toOffsetDateTimeUtc()
            it[durationMinutes] = row[NotesTable.durationMinutes]
            it[repeatRule] = rule.name
            it[coinCount] = row[NotesTable.coinCount]
            it[isCompleted] = false
            it[recurrenceParentId] = parentId
            it[recurrenceAnchorDay] = anchorDay
        }
        return nextId
    }

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
            startAtMillis = this[NotesTable.startAt]?.toInstant()?.toEpochMilli(),
            durationMinutes = this[NotesTable.durationMinutes],
            repeatRule = RepeatRule.valueOf(this[NotesTable.repeatRule]),
            recurrenceParentId = this[NotesTable.recurrenceParentId],
            coinCount = this[NotesTable.coinCount],
            isCompleted = this[NotesTable.isCompleted],
            createdAt = this[NotesTable.createdAt].toInstant().toEpochMilli(),
            updatedAt = this[NotesTable.updatedAt].toInstant().toEpochMilli()
        )

    // Преобразует данные в нужный формат представления.
    private fun Long.toOffsetDateTimeUtc() =
        Instant.ofEpochMilli(this).atOffset(ZoneOffset.UTC)
}

internal fun nextOccurrenceStart(
    startMillis: Long,
    nowMillis: Long,
    rule: RepeatRule,
    anchorDay: Int = utcDayOfMonth(startMillis)
): Long {
    require(rule != RepeatRule.NONE)
    var candidate = Instant.ofEpochMilli(startMillis).atZone(ZoneOffset.UTC)
    do {
        candidate = when (rule) {
            RepeatRule.DAILY -> candidate.plusDays(1)
            RepeatRule.WEEKLY -> candidate.plusWeeks(1)
            RepeatRule.MONTHLY -> candidate.plusMonthsClamped(anchorDay)
            RepeatRule.NONE -> error("Repeat rule is required")
        }
    } while (candidate.toInstant().toEpochMilli() <= nowMillis)
    return candidate.toInstant().toEpochMilli()
}

private fun ZonedDateTime.plusMonthsClamped(anchorDay: Int): ZonedDateTime {
    val nextMonth = withDayOfMonth(1).plusMonths(1)
    return nextMonth.withDayOfMonth(minOf(anchorDay, nextMonth.toLocalDate().lengthOfMonth()))
}

private fun utcDayOfMonth(millis: Long): Int =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).dayOfMonth
