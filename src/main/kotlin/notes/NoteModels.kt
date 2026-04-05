@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.example.mutlabocnotes.notes

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

// Перечисление допустимых значений для этой части системы.
@Serializable
enum class NoteCategory {
    SHOPPING,
    TASKS,
    NOTES
}

// Модель данных, используемая в бизнес-логике.
data class ChecklistItemModel(
    val text: String,
    val isChecked: Boolean
)

// Модель данных, используемая в бизнес-логике.
data class NoteModel(
    val id: Uuid,
    val userId: Uuid,
    val title: String,
    val content: String,
    val category: NoteCategory,
    val checklist: List<ChecklistItemModel>,
    val deadlineMillis: Long?,
    val isRepeating: Boolean,
    val coinCount: Int,
    val isCompleted: Boolean
)