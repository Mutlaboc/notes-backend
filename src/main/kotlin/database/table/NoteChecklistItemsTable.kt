@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.example.mutlabocnotes.database.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

// Описание структуры таблицы базы данных через Exposed.
object NoteChecklistItemsTable : Table("note_checklist_items") {
    val id = uuid("id")
    val noteId = uuid("note_id")
    val position = integer("position")
    val text = text("text")
    val isChecked = bool("is_checked").default(false)
    val createdAt = timestampWithTimeZone("created_at")
}