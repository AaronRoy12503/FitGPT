package com.fitgpt.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitgpt.app.ai.GroqRecommendationService
import com.fitgpt.app.ai.OutfitRecommendationEngine
import com.fitgpt.app.data.model.ClothingItem
import com.fitgpt.app.data.model.OutfitRecommendation
import com.fitgpt.app.data.model.SavedOutfit
import com.fitgpt.app.data.model.UserPreferences
import com.fitgpt.app.data.PreferencesManager
import com.fitgpt.app.data.repository.FakeWardrobeRepository
import com.fitgpt.app.data.repository.WardrobeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WardrobeViewModel : ViewModel() {

    private val repository: WardrobeRepository = FakeWardrobeRepository()
    private val recommendationEngine = OutfitRecommendationEngine()
    private val groqService = GroqRecommendationService()
    private var preferencesManager: PreferencesManager? = null

    // Full source of truth
    private val allItems = MutableStateFlow(repository.getWardrobeItems())

    // Filters
    private val selectedSeason = MutableStateFlow<String?>(null)
    private val minComfortLevel = MutableStateFlow(1)

    // Exposed filtered list
    private val _wardrobeItems =
        MutableStateFlow<List<ClothingItem>>(allItems.value)
    val wardrobeItems: StateFlow<List<ClothingItem>> = _wardrobeItems

    // User preferences
    private val _userPreferences = MutableStateFlow(
        UserPreferences(
            bodyType = "Average",
            stylePreference = "Casual",
            comfortPreference = 3,
            preferredSeasons = listOf("Spring", "Summer", "Fall", "Winter")
        )
    )
    val userPreferences: StateFlow<UserPreferences> = _userPreferences

    // Recommendations (kept for backward compat with tests)
    private val _recommendations = MutableStateFlow<List<OutfitRecommendation>>(emptyList())
    val recommendations: StateFlow<List<OutfitRecommendation>> = _recommendations

    // Recently shown outfit history — each entry is a set of item IDs
    private val recentOutfitHistory = ArrayDeque<Set<Int>>()

    // Track in-flight Groq job so we can cancel it on wardrobe mutations
    private var groqJob: Job? = null

    // UI state for RecommendationScreen
    private val _recommendationState = MutableStateFlow<RecommendationUiState>(
        RecommendationUiState.Loading
    )
    val recommendationState: StateFlow<RecommendationUiState> = _recommendationState

    init {
        refreshRecommendations()
    }

    /* ---------- CRUD ---------- */

    fun addItem(item: ClothingItem) {
        repository.addItem(item)
        refresh()
    }

    fun deleteItem(item: ClothingItem) {
        repository.deleteItem(item)
        purgeDeletedItemFromHistory(item.id)
        refresh()
    }

    fun updateItem(item: ClothingItem) {
        repository.updateItem(item)
        refresh()
    }

    /* ---------- SAVED OUTFITS ---------- */

    fun saveOutfit(items: List<ClothingItem>) {
        val outfit = SavedOutfit(
            id = System.currentTimeMillis().toInt(),
            items = items
        )
        repository.saveOutfit(outfit)
    }

    fun getSavedOutfits(): List<SavedOutfit> {
        return repository.getSavedOutfits()
    }

    /* ---------- FILTERING ---------- */

    fun setSeasonFilter(season: String?) {
        selectedSeason.value = season
        applyFilters()
    }

    fun setComfortFilter(minComfort: Int) {
        minComfortLevel.value = minComfort
        applyFilters()
    }

    fun clearFilters() {
        selectedSeason.value = null
        minComfortLevel.value = 1
        applyFilters()
    }

    /* ---------- PERSISTENCE ---------- */

    fun initPersistence(pm: PreferencesManager) {
        preferencesManager = pm
        _userPreferences.value = pm.loadPreferences()
        refreshRecommendations()
    }

    /* ---------- USER PREFERENCES ---------- */

    fun updatePreferences(preferences: UserPreferences) {
        _userPreferences.value = preferences
        preferencesManager?.savePreferences(preferences)
        refreshRecommendations()
    }

    /* ---------- AI RECOMMENDATIONS ---------- */

    fun generateExplanation(item: ClothingItem): String {
        return recommendationEngine.generateItemExplanation(item, _userPreferences.value)
    }

    fun refreshRecommendations() {
        // Cancel any in-flight Groq request so stale results can't overwrite fresh data
        groqJob?.cancel()
        groqJob = null

        val historySnapshot = recentOutfitHistory.toSet()

        // Step 1: Always run rule-based engine synchronously as fallback
        val fallback = recommendationEngine.recommend(
            items = allItems.value,
            preferences = _userPreferences.value,
            recentlyShown = historySnapshot
        )
        // Record shown outfits immediately so next refresh won't repeat them
        recordShownOutfits(fallback)
        _recommendations.value = fallback

        // Step 2: If Groq is available, attempt AI recommendations
        if (groqService.isAvailable) {
            _recommendationState.value = RecommendationUiState.Loading

            groqJob = viewModelScope.launch {
                try {
                    val aiResults = groqService.recommend(
                        items = allItems.value,
                        preferences = _userPreferences.value
                    )
                    // Replace stale item snapshots with current data and drop deleted items
                    val currentItemsById = allItems.value.associateBy { it.id }
                    val cleanResults = aiResults.map { rec ->
                        rec.copy(
                            items = rec.items.mapNotNull { currentItemsById[it.id] },
                            itemExplanations = rec.itemExplanations.filterKeys { it in currentItemsById }
                        )
                    }.filter { it.items.isNotEmpty() }

                    // Filter out AI results that duplicate recently shown outfits
                    val updatedHistory = recentOutfitHistory.toSet()
                    val freshAiResults = cleanResults.filter { rec ->
                        rec.items.map { it.id }.toSet() !in updatedHistory
                    }.ifEmpty { cleanResults }

                    if (freshAiResults.isNotEmpty()) {
                        recordShownOutfits(freshAiResults)
                        _recommendations.value = freshAiResults
                        _recommendationState.value = RecommendationUiState.Success(
                            recommendations = freshAiResults,
                            isAiGenerated = true
                        )
                    } else {
                        // AI returned empty or all items were deleted, use fallback
                        _recommendationState.value = RecommendationUiState.Success(
                            recommendations = fallback,
                            isAiGenerated = false
                        )
                    }
                } catch (e: Exception) {
                    Log.e("WardrobeViewModel", "Groq AI failed", e)
                    _recommendationState.value = RecommendationUiState.Error(
                        message = e.message ?: "AI unavailable",
                        fallbackRecommendations = fallback
                    )
                }
            }
        } else {
            // No API key — use rule-based results directly
            _recommendationState.value = RecommendationUiState.Success(
                recommendations = fallback,
                isAiGenerated = false
            )
        }
    }

    private fun recordShownOutfits(recommendations: List<OutfitRecommendation>) {
        for (rec in recommendations) {
            val key = rec.items.map { it.id }.toSet()
            if (key !in recentOutfitHistory) {
                recentOutfitHistory.addLast(key)
            }
        }
        while (recentOutfitHistory.size > OutfitRecommendationEngine.MAX_HISTORY_SIZE) {
            recentOutfitHistory.removeFirst()
        }
    }

    /* ---------- INTERNAL ---------- */

    private fun purgeDeletedItemFromHistory(deletedId: Int) {
        val cleaned = recentOutfitHistory.map { idSet ->
            idSet - deletedId
        }.filter { it.isNotEmpty() }
        recentOutfitHistory.clear()
        cleaned.forEach { recentOutfitHistory.addLast(it) }
    }

    private fun refresh() {
        allItems.value = repository.getWardrobeItems()
        applyFilters()
        refreshRecommendations()
    }

    private fun applyFilters() {
        _wardrobeItems.value = allItems.value.filter { item ->
            val seasonMatch =
                selectedSeason.value == null || item.season == selectedSeason.value

            val comfortMatch =
                item.comfortLevel >= minComfortLevel.value

            seasonMatch && comfortMatch
        }
    }
}
