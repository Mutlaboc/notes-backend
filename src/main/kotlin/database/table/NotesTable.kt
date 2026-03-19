package com.example.mutlabocnotes.database.table

import org.jetbrains.exposed.v1.core.PrimaryKey
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object NotesTable : Table("notes") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val sourceFirestoreId = text("source_firestore_id").nullable()
    val title = text("title")
    val content = text("content")
    val category = text("category")
    val deadlineAt = timestampWithTimeZone("deadline_at").nullable()
    val isRepeating = bool("is_repeating").default(false)
    val coinCount = integer("coin_count").default(0)
    val isCompleted = bool("is_completed").default(false)
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}