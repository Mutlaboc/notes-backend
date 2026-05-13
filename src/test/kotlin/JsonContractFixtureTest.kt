package com.example.mutlabocnotes

import com.example.mutlabocnotes.api.ErrorResponseDto
import com.example.mutlabocnotes.auth.AuthResponseDto
import com.example.mutlabocnotes.homecards.HomeCardDto
import com.example.mutlabocnotes.homecards.HomeCardUpsertRequestDto
import com.example.mutlabocnotes.notes.CreateNoteRequestDto
import com.example.mutlabocnotes.notes.NoteCategory
import com.example.mutlabocnotes.notes.NoteResponseDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.serialization.decodeFromString

class JsonContractFixtureTest {

    @Test
    fun authResponseFixtureMatchesBackendDto() {
        val dto = ApiJson.decodeFromString<AuthResponseDto>(fixture("auth-response.json"))

        assertEquals("access-token", dto.accessToken)
        assertEquals("refresh-token", dto.refreshToken)
        assertEquals("Bearer", dto.tokenType)
        assertEquals(1800, dto.expiresInSeconds)
        assertEquals(1209600, dto.refreshExpiresInSeconds)
        assertEquals("fixture-user@example.com", dto.user.email)
        assertEquals("local:fixture-user@example.com", dto.user.bridgeUserKey)
    }

    @Test
    fun noteFixturesMatchBackendDtos() {
        val response = ApiJson.decodeFromString<NoteResponseDto>(fixture("note-response.json"))
        val request = ApiJson.decodeFromString<CreateNoteRequestDto>(fixture("note-upsert-request.json"))

        assertEquals("Groceries", response.title)
        assertEquals(NoteCategory.SHOPPING, response.category)
        assertEquals("Milk", response.checklist.single().text)
        assertFalse(response.isRepeating)
        assertEquals(1710000001000, response.updatedAt)

        assertEquals("Updated task", request.title)
        assertEquals(NoteCategory.TASKS, request.category)
        assertEquals(3, request.coinCount)
        assertEquals(true, request.isRepeating)
    }

    @Test
    fun homeCardFixturesMatchBackendDtos() {
        val response = ApiJson.decodeFromString<HomeCardDto>(fixture("home-card-response.json"))
        val request = ApiJson.decodeFromString<HomeCardUpsertRequestDto>(fixture("home-card-upsert-request.json"))

        assertEquals("Meter", response.title)
        assertEquals("METERS", response.section)
        assertEquals("serial", response.fields.single().key)
        assertEquals(1710000000000, response.createdAt)
        assertEquals(1710000001000, response.updatedAt)

        assertEquals("Manuals", request.title)
        assertEquals("DOCUMENTS", request.section)
        assertEquals(123, request.createdAt)
        assertEquals(456, request.updatedAt)
    }

    @Test
    fun errorFixtureMatchesBackendDto() {
        val response = ApiJson.decodeFromString<ErrorResponseDto>(fixture("error-response.json"))

        assertEquals("invalid_request", response.code)
        assertEquals("Email is required", response.message)
    }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader.getResource("contracts/$name")) {
            "Missing fixture contracts/$name"
        }.readText()
}
