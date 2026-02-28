package com.fitgpt.app.viewmodel

import com.fitgpt.app.data.model.OutfitRecommendation

sealed class RecommendationUiState {
    data object Loading : RecommendationUiState()
    data object Unauthenticated : RecommendationUiState()

    data class Success(
        val recommendations: List<OutfitRecommendation>,
        val isAiGenerated: Boolean
    ) : RecommendationUiState()

    data class Error(
        val message: String,
        val fallbackRecommendations: List<OutfitRecommendation>
    ) : RecommendationUiState()
}
