@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.example.mutlabocnotes.notes

import com.example.mutlabocnotes.database.DatabaseFactory
import com.example.mutlabocnotes.database.table.UsersTable
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.Uuid

class FirebaseUserResolver {

    suspend fun resolveUserId(call: ApplicationCall): Uuid {
        val firebaseUid = call.request.headers["X-Firebase-Uid"]
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: throw BadRequestException("Missing X-Firebase-Uid header")

        return DatabaseFactory.dbQuery {
            val existing = UsersTable
                .selectAll()
                .where { UsersTable.firebaseUid eq firebaseUid }
                .singleOrNull()

            if (existing != null) {
                existing[UsersTable.id]
            } else {
                UsersTable.insert {
                    it[id] = Uuid.random()
                    it[UsersTable.firebaseUid] = firebaseUid
                }[UsersTable.id]
            }
        }
    }
}