package com.fitgpt.app.data.model

data class ClothingItem(
    val id: Int,
    val category: String,
    val color: String,
    val season: String,
    val comfortLevel: Int,
    val fit: String = "Regular",
    val imageUrl: String? = null,
    val isArchived: Boolean = false
)