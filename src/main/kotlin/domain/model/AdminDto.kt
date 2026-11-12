package com.anasdev.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class CreateGameRequest(
    val title: String,
    val description: String? = null,
    val imagePath: String,
    val releaseDate: String,
    val genre: String,
    val currentPrice: Double,
)

@Serializable
data class AddKeyRequest(
    val keys: List<String>
)