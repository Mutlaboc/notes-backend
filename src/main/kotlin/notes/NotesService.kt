package com.example.mutlabocnotes.notes

import java.util.UUID

class NotesService(
    private val repository: NotesRepository
) {
    suspend fun getAll(userId: UUID): List<NoteResponseDto> =
        repository.getAllByUser(userId).map { it.toDto() }

    suspend fun getById(userId: UUID, noteId: UUID): NoteResponseDto? =
        repository.getById(userId, noteId)?.toDto()

    suspend fun create(userId: UUID, request: CreateNoteRequestDto): NoteResponseDto =
        repository.create(userId, request).toDto()

    suspend fun update(userId: UUID, noteId: UUID, request: UpdateNoteRequestDto): NoteResponseDto? =
        repository.update(userId, noteId, request)?.toDto()

    suspend fun delete(userId: UUID, noteId: UUID): Boolean =
        repository.delete(userId, noteId)

    suspend fun updateCompletion(userId: UUID, noteId: UUID, isCompleted: Boolean): NoteResponseDto? =
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