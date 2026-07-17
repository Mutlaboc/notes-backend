@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.example.mutlabocnotes.database.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

// Описание структуры таблицы базы данных через Exposed.
object NotesTable : Table("notes") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val sourceFirestoreId = text("source_firestore_id").nullable()
    val title = text("title")
    val content = text("content")
    val category = text("category")
    val deadlineAt = timestampWithTimeZone("deadline_at").nullable()
    val startAt = timestampWithTimeZone("start_at").nullable()
    val durationMinutes = long("duration_minutes").nullable()
    val repeatRule = text("repeat_rule").default("NONE")
    val recurrenceParentId = uuid("recurrence_parent_id").nullable().uniqueIndex()
    val recurrenceAnchorDay = integer("recurrence_anchor_day").nullable()
    val coinCount = integer("coin_count").default(0)
    val isCompleted = bool("is_completed").default(false)
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
}
