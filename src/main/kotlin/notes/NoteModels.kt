@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.example.mutlabocnotes.notes

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

// Перечисление допустимых значений для этой части системы.
@Serializable
enum class NoteCategory {
    SHOPPING,
    TASKS,
    RECURRING_TASKS
}

@Serializable
enum class RepeatRule {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY
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
    val startAtMillis: Long?,
    val durationMinutes: Long?,
    val repeatRule: RepeatRule,
    val recurrenceParentId: Uuid?,
    val coinCount: Int,
    val isCompleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)
