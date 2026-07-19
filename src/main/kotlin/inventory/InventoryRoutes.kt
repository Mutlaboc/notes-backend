@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.example.mutlabocnotes.inventory

import com.example.mutlabocnotes.api.ApiErrorCodes
import com.example.mutlabocnotes.api.respondApiError
import com.example.mutlabocnotes.auth.requireCurrentUserId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID

// Маршруты инвентаря: чтение (с авто-созданием стартового набора), equip и unequip.
fun Route.inventoryRoutes(
    repository: InventoryRepository = InventoryRepository(),
) {
    authenticate("auth-jwt") {
        route("/inventory") {
            get {
                val userId = call.requireCurrentUserId()?.let { UUID.fromString(it.toString()) } ?: return@get
                call.respond(repository.getOrCreateForUser(userId))
            }

            post("/equip") {
                val userId = call.requireCurrentUserId()?.let { UUID.fromString(it.toString()) } ?: return@post
                val request = call.receive<InventoryEquipRequestDto>()
                val operationId = runCatching { UUID.fromString(request.operationId) }.getOrNull()
                    ?: return@post call.respondApiError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST)
                if (request.itemId.isBlank()) {
                    return@post call.respondApiError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST)
                }
                val updated = runCatching { repository.equip(userId, operationId, request.itemId.trim()) }
                    .getOrElse { return@post call.respondInventoryError(it) }
                call.respond(updated)
            }

            post("/unequip") {
                val userId = call.requireCurrentUserId()?.let { UUID.fromString(it.toString()) } ?: return@post
                val request = call.receive<InventoryUnequipRequestDto>()
                val operationId = runCatching { UUID.fromString(request.operationId) }.getOrNull()
                    ?: return@post call.respondApiError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST)
                val updated = runCatching { repository.unequip(userId, operationId, request.slot.trim()) }
                    .getOrElse { return@post call.respondInventoryError(it) }
                call.respond(updated)
            }
        }
    }
}

// Единое сопоставление ошибок репозитория инвентаря с HTTP-статусами.
private suspend fun io.ktor.server.application.ApplicationCall.respondInventoryError(error: Throwable) {
    when (error) {
        is NoSuchElementException -> respondApiError(HttpStatusCode.NotFound, ApiErrorCodes.ITEM_NOT_FOUND)
        is IllegalArgumentException -> respondApiError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST)
        is IllegalStateException -> respondApiError(HttpStatusCode.Conflict, ApiErrorCodes.INVALID_REQUEST)
        else -> throw error
    }
}
