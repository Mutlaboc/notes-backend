@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.example.mutlabocnotes.notes

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
enum class NoteCategory {
    SHOPPING,
    TASKS,
    NOTES
}

data class ChecklistItemModel(
    val text: String,
    val isChecked: Boolean
)

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