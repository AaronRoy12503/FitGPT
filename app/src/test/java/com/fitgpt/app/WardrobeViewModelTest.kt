package com.fitgpt.app

import com.fitgpt.app.data.model.ClothingItem
import com.fitgpt.app.data.model.UserPreferences
import com.fitgpt.app.viewmodel.WardrobeViewModel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WardrobeViewModelTest {

    private lateinit var viewModel: WardrobeViewModel

    @Before
    fun setUp() {
        viewModel = WardrobeViewModel()
    }

    // -----------------------------------------------------------------------
    // generateExplanation() via the ViewModel
    // -----------------------------------------------------------------------

    @Test
    fun generateExplanation_returnsNonEmptyString() {
        val item = ClothingItem(1, "Top", "Black", "Winter", 3)
        val explanation = viewModel.generateExplanation(item)
        assertTrue(explanation.isNotBlank())
    }

    @Test
    fun generateExplanation_containsSeasonInfo() {
        val item = ClothingItem(1, "Top", "Blue", "Summer", 4)
        val explanation = viewModel.generateExplanation(item)
        // Should reference the season somewhere in the explanation
        assertTrue(
            explanation.contains("summer", ignoreCase = true) ||
                explanation.contains("season", ignoreCase = true) ||
                explanation.contains("Versatile", ignoreCase = true)
        )
    }

    @Test
    fun generateExplanation_containsComfortInfo() {
        // Use comfort 3 (exact match) so no style note displaces it
        val item = ClothingItem(1, "Top", "Black", "Summer", 3)
        val explanation = viewModel.generateExplanation(item)
        assertTrue(
            explanation.contains("comfort", ignoreCase = true) ||
                explanation.contains("exceeds", ignoreCase = true) ||
                explanation.contains("matches", ignoreCase = true) ||
                explanation.contains("style", ignoreCase = true)
        )
    }

    @Test
    fun generateExplanation_containsColorInfo() {
        val item = ClothingItem(1, "Top", "Red", "Summer", 3)
        val explanation = viewModel.generateExplanation(item)
        assertTrue(explanation.contains("red", ignoreCase = true))
    }

    @Test
    fun generateExplanation_differentItemsProduceDifferentExplanations() {
        val winterItem = ClothingItem(1, "Top", "Black", "Winter", 1)
        val summerItem = ClothingItem(2, "Top", "White", "Summer", 5)

        val winterExplanation = viewModel.generateExplanation(winterItem)
        val summerExplanation = viewModel.generateExplanation(summerItem)

        assertNotEquals(winterExplanation, summerExplanation)
    }

    // -----------------------------------------------------------------------
    // Recommendations StateFlow
    // -----------------------------------------------------------------------

    @Test
    fun recommendations_initiallyPopulated() {
        // The ViewModel has default FakeWardrobeRepository items and calls refreshRecommendations in init
        val recommendations = viewModel.recommendations.value
        assertTrue(recommendations.isNotEmpty())
    }

    @Test
    fun recommendations_eachHasNonEmptyExplanation() {
        val recommendations = viewModel.recommendations.value
        recommendations.forEach { rec ->
            assertTrue(rec.explanation.isNotBlank())
        }
    }

    @Test
    fun recommendations_eachHasPositiveScore() {
        val recommendations = viewModel.recommendations.value
        recommendations.forEach { rec ->
            assertTrue(rec.score > 0.0)
        }
    }

    @Test
    fun recommendations_eachHasAtLeastOneItem() {
        val recommendations = viewModel.recommendations.value
        recommendations.forEach { rec ->
            assertTrue(rec.items.isNotEmpty())
        }
    }

    // -----------------------------------------------------------------------
    // updatePreferences() triggers recommendation refresh
    // -----------------------------------------------------------------------

    @Test
    fun updatePreferences_changesRecommendations() {
        val originalRecs = viewModel.recommendations.value.map { it.score }

        viewModel.updatePreferences(
            UserPreferences(
                bodyType = "Athletic",
                stylePreference = "Sporty",
                comfortPreference = 5,
                preferredSeasons = listOf("Winter")
            )
        )

        val updatedRecs = viewModel.recommendations.value.map { it.score }

        // Scores should change after preference update (given the default items have mixed seasons)
        assertNotEquals(originalRecs, updatedRecs)
    }

    @Test
    fun updatePreferences_updatesUserPreferencesState() {
        val newPrefs = UserPreferences(
            bodyType = "Slim",
            stylePreference = "Formal",
            comfortPreference = 4,
            preferredSeasons = listOf("Fall")
        )

        viewModel.updatePreferences(newPrefs)

        assertEquals(newPrefs, viewModel.userPreferences.value)
    }

    // -----------------------------------------------------------------------
    // addItem / deleteItem triggers recommendation refresh
    // -----------------------------------------------------------------------

    @Test
    fun addItem_refreshesRecommendations() {
        val initialCount = viewModel.recommendations.value.size

        viewModel.addItem(ClothingItem(100, "Top", "Green", "Summer", 5))
        viewModel.addItem(ClothingItem(101, "Bottom", "White", "Summer", 5))

        val updatedCount = viewModel.recommendations.value.size

        // Adding a top and bottom should produce more outfit combinations
        assertTrue(updatedCount >= initialCount)
    }

    @Test
    fun deleteItem_refreshesRecommendations() {
        val initialItems = viewModel.wardrobeItems.value
        if (initialItems.isNotEmpty()) {
            val initialRecs = viewModel.recommendations.value

            viewModel.deleteItem(initialItems[0])

            val updatedRecs = viewModel.recommendations.value

            // Recommendations should have changed after deletion
            assertNotEquals(initialRecs, updatedRecs)
        }
    }

    // -----------------------------------------------------------------------
    // Default preferences
    // -----------------------------------------------------------------------

    @Test
    fun defaultPreferences_hasAllFourSeasons() {
        val prefs = viewModel.userPreferences.value
        assertEquals(4, prefs.preferredSeasons.size)
        assertTrue(prefs.preferredSeasons.containsAll(listOf("Spring", "Summer", "Fall", "Winter")))
    }

    @Test
    fun defaultPreferences_isCasualStyle() {
        assertEquals("Casual", viewModel.userPreferences.value.stylePreference)
    }

    @Test
    fun defaultPreferences_accessibilityDisabled() {
        assertFalse(viewModel.userPreferences.value.accessibilityModeEnabled)
    }
}
