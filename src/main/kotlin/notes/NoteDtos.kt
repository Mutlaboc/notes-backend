package com.example.mutlabocnotes.notes

import kotlinx.serialization.Serializable

// DTO-модель для обмена данными между API и доменом.
@Serializable
data class ChecklistItemDto(
    val text: String = "",
    val isChecked: Boolean = false
)

// DTO-модель для обмена данными между API и доменом.
@Serializable
data class NoteResponseDto(
    val id: String,
    val title: String,
    val content: String,
    val category: NoteCategory,
    val checklist: List<ChecklistItemDto>,
    val deadlineMillis: Long? = null,
    val isRepeating: Boolean = false,
    val coinCount: Int = 0,
    val isCompleted: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

// DTO-модель для обмена данными между API и доменом.
@Serializable
data class CreateNoteRequestDto(
    val title: String = "",
    val content: String = "",
    val category: NoteCategory = NoteCategory.NOTES,
    val checklist: List<ChecklistItemDto> = emptyList(),
    val deadlineMillis: Long? = null,
    val isRepeating: Boolean = false,
    val coinCount: Int = 0,
    val isCompleted: Boolean = false
)

// DTO-модель для обмена данными между API и доменом.
@Serializable
data class UpdateNoteRequestDto(
    val title: String = "",
    val content: String = "",
    val category: NoteCategory = NoteCategory.NOTES,
    val checklist: List<ChecklistItemDto> = emptyList(),
    val deadlineMillis: Long? = null,
    val isRepeating: Boolean = false,
    val coinCount: Int = 0,
    val isCompleted: Boolean = false
)

// DTO-модель для обмена данными между API и доменом.
@Serializable
data class UpdateNoteCompletionRequestDto(
    val isCompleted: Boolean
)
