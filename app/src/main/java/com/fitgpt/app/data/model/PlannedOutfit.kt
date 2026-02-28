package com.fitgpt.app.data.model

import java.time.LocalDate

data class PlannedOutfit(
    val id: Int,
    val items: List<ClothingItem>,
    val date: LocalDate,
    val note: String = ""
)
