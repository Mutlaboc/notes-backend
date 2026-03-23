package com.example.mutlabocnotes.homecards

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.core.java.javaUUID

object HomeCardsTable : Table("home_cards") {
    val id = javaUUID("id")
    val userId = javaUUID("user_id")
    val sourceFirestoreId = text("source_firestore_id").nullable()
    val title = text("title")
    val section = text("section")
    val note = text("note")
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object HomeCardFieldsTable : Table("home_card_fields") {
    val id = javaUUID("id")
    val cardId = javaUUID("card_id")
    val position = integer("position")
    val fieldKey = text("field_key")
    val fieldValue = text("field_value")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}

object HomeCardLinksTable : Table("home_card_links") {
    val id = javaUUID("id")
    val cardId = javaUUID("card_id")
    val position = integer("position")
    val url = text("url")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}

object HomeCardsUsersLookupTable : Table("users") {
    val id = javaUUID("id")
    val firebaseUid = text("firebase_uid").nullable()

    override val primaryKey = PrimaryKey(id)
}
