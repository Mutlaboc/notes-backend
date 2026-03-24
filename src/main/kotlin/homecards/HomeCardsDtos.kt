@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.example.mutlabocnotes.homecards

import kotlinx.serialization.Serializable

@Serializable
data class HomeFieldDto(
    val key: String,
    val value: String,
)

@Serializable
data class HomeCardDto(
    val id: String,
    val title: String,
    val section: String,
    val fields: List<HomeFieldDto>,
    val note: String,
    val links: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class HomeCardUpsertRequestDto(
    val title: String,
    val section: String,
    val fields: List<HomeFieldDto>,
    val note: String,
    val links: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
)
