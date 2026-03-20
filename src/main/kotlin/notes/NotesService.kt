@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.example.mutlabocnotes.notes

import kotlin.uuid.Uuid

class NotesService(
    private val repository: NotesRepository
) {
    suspend fun getAll(userId: Uuid): List<NoteResponseDto> =
        repository.getAllByUser(userId).map { it.toDto() }

    suspend fun getById(userId: Uuid, noteId: Uuid): NoteResponseDto? =
        repository.getById(userId, noteId)?.toDto()

    suspend fun create(userId: Uuid, request: CreateNoteRequestDto): NoteResponseDto =
        repository.create(userId, request).toDto()

    suspend fun update(userId: Uuid, noteId: Uuid, request: UpdateNoteRequestDto): NoteResponseDto? =
        repository.update(userId, noteId, request)?.toDto()

    suspend fun delete(userId: Uuid, noteId: Uuid): Boolean =
        repository.delete(userId, noteId)

    suspend fun updateCompletion(userId: Uuid, noteId: Uuid, isCompleted: Boolean): NoteResponseDto? =
        repository.updateCompletion(userId, noteId, isCompleted)?.toDto()

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
            isCompleted = isCompleted
        )
}