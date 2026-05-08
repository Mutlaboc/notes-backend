@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.example.mutlabocnotes.notes

import kotlin.uuid.Uuid

// Сервис с прикладной бизнес-логикой модуля.
open class NotesService(
    private val repository: NotesRepository
) {
    // Возвращает данные по заданным параметрам запроса.
    open suspend fun getAll(userId: Uuid): List<NoteResponseDto> =
        repository.getAllByUser(userId).map { it.toDto() }

    // Возвращает одну заметку по id с проверкой владельца.
    open suspend fun getById(userId: Uuid, noteId: Uuid): NoteResponseDto? =
        repository.getById(userId, noteId)?.toDto()

    // Создаёт новую заметку и связанный чеклист.
    open suspend fun create(userId: Uuid, request: CreateNoteRequestDto): NoteResponseDto =
        repository.create(userId, request).toDto()

    // Обновляет заметку и перезаписывает её чеклист.
    open suspend fun update(userId: Uuid, noteId: Uuid, request: UpdateNoteRequestDto): NoteResponseDto? =
        repository.update(userId, noteId, request)?.toDto()

    // Удаляет заметку, если она принадлежит текущему пользователю.
    open suspend fun delete(userId: Uuid, noteId: Uuid): Boolean =
        repository.delete(userId, noteId)

    // Обновляет статус выполнения заметки.
    open suspend fun updateCompletion(userId: Uuid, noteId: Uuid, isCompleted: Boolean): NoteResponseDto? =
        repository.updateCompletion(userId, noteId, isCompleted)?.toDto()

    // Преобразует данные в нужный формат представления.
    private fun NoteModel.toDto(): NoteResponseDto =
        NoteResponseDto(
            id = id.toString(),
            title = title,
            content = content,
            category = category,
            checklist = checklist.map {
                ChecklistItemDto(
                    text = it.text,
                    isChecked = it.isChecked
                )
            },
            deadlineMillis = deadlineMillis,
            isRepeating = isRepeating,
            coinCount = coinCount,
            isCompleted = isCompleted,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
}
