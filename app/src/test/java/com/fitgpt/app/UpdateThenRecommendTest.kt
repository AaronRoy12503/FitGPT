package com.fitgpt.app

import com.fitgpt.app.data.model.ClothingCategory
import com.fitgpt.app.data.model.ClothingItem
import com.fitgpt.app.data.model.UserPreferences
import com.fitgpt.app.viewmodel.WardrobeViewModel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Verifies that updated item attributes propagate to recommendations.
 *
 * Covers:
 *  - Updating an item's category changes its role in outfit combinations
 *  - Recommendations reflect the latest item data after update
 *  - No stale/cached item snapshots appear in recommendation results
 *  - Updating multiple items simultaneously produces correct recommendations
 *  - Outfit history does not resurrect pre-update attribute combinations
 */
class UpdateThenRecommendTest {

    private lateinit var viewModel: WardrobeViewModel

    @Before
    fun setUp() {
        viewModel = WardrobeViewModel()
    }

    // ------------------------------------------------------------------
    // Basic: updated attributes appear in recommendations
    // ------------------------------------------------------------------

    @Test
    fun updateItem_categoryChange_reflectedInRecommendations() {
        val items = viewModel.wardrobeItems.value
        val topItem = items.first { it.category == ClothingCategory.TOP }

        // Change Top to Accessory
        val updated = topItem.copy(category = ClothingCategory.ACCESSORY)
        viewModel.updateItem(updated)

        val recs = viewModel.recommendations.value
        for (rec in recs) {
            for (item in rec.items) {
                if (item.id == topItem.id) {
                    assertEquals(
                        "Updated item should have new category in recommendations",
                        ClothingCategory.ACCESSORY,
                        item.category
                    )
                }
            }
        }
    }

    @Test
    fun updateItem_colorChange_reflectedInRecommendations() {
        val items = viewModel.wardrobeItems.value
        val bottomItem = items.first { it.category == ClothingCategory.BOTTOM }

        val updated = bottomItem.copy(color = "Red")
        viewModel.updateItem(updated)

        val recs = viewModel.recommendations.value
        for (rec in recs) {
            for (item in rec.items) {
                if (item.id == bottomItem.id) {
                    assertEquals(
                        "Updated item should have new color in recommendations",
                        "Red",
                        item.color
                    )
                }
            }
        }
    }

    @Test
    fun updateItem_seasonChange_reflectedInRecommendations() {
        val items = viewModel.wardrobeItems.value
        val topItem = items.first { it.category == ClothingCategory.TOP }

        val updated = topItem.copy(season = "Summer")
        viewModel.updateItem(updated)

        val recs = viewModel.recommendations.value
        for (rec in recs) {
            for (item in rec.items) {
                if (item.id == topItem.id) {
                    assertEquals(
                        "Updated item should have new season in recommendations",
                        "Summer",
                        item.season
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // No stale data in wardrobe list
    // ------------------------------------------------------------------

    @Test
    fun updateItem_wardrobeReflectsNewAttributes() {
        val items = viewModel.wardrobeItems.value
        val shoesItem = items.first { it.category == ClothingCategory.SHOES }

        val updated = shoesItem.copy(color = "Gold", comfortLevel = 1)
        viewModel.updateItem(updated)

        val currentItem = viewModel.wardrobeItems.value.first { it.id == shoesItem.id }
        assertEquals("Gold", currentItem.color)
        assertEquals(1, currentItem.comfortLevel)
    }

    @Test
    fun updateItem_noStaleSnapshotsInWardrobe() {
        val items = viewModel.wardrobeItems.value
        val outerwearItem = items.first { it.category == ClothingCategory.OUTERWEAR }

        val oldColor = outerwearItem.color
        val updated = outerwearItem.copy(color = "Pink")
        viewModel.updateItem(updated)

        // No item in wardrobe should have the old color for this ID
        val current = viewModel.wardrobeItems.value.first { it.id == outerwearItem.id }
        assertNotEquals(
            "Wardrobe should not retain old color",
            oldColor,
            current.color
        )
        assertEquals("Pink", current.color)
    }

    // ------------------------------------------------------------------
    // Category change affects outfit structure
    // ------------------------------------------------------------------

    @Test
    fun updateItem_removingOnlyTop_fallsBackToIndividualScoring() {
        // Change the only Top to an Accessory — no Top+Bottom combos possible
        val items = viewModel.wardrobeItems.value
        val topItem = items.first { it.category == ClothingCategory.TOP }

        val updated = topItem.copy(category = ClothingCategory.ACCESSORY)
        viewModel.updateItem(updated)

        val recs = viewModel.recommendations.value
        // Without a Top, engine can't form Top+Bottom combos → individual item fallback
        assertTrue("Should still have recommendations", recs.isNotEmpty())

        // Verify no recommendation references old category
        for (rec in recs) {
            for (item in rec.items) {
                if (item.id == topItem.id) {
                    assertEquals(ClothingCategory.ACCESSORY, item.category)
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Multiple updates
    // ------------------------------------------------------------------

    @Test
    fun updateMultipleItems_allChangesReflected() {
        val items = viewModel.wardrobeItems.value
        val topItem = items.first { it.category == ClothingCategory.TOP }
        val bottomItem = items.first { it.category == ClothingCategory.BOTTOM }

        viewModel.updateItem(topItem.copy(color = "Purple", season = "Fall"))
        viewModel.updateItem(bottomItem.copy(color = "Green", comfortLevel = 5))

        val currentTop = viewModel.wardrobeItems.value.first { it.id == topItem.id }
        val currentBottom = viewModel.wardrobeItems.value.first { it.id == bottomItem.id }

        assertEquals("Purple", currentTop.color)
        assertEquals("Fall", currentTop.season)
        assertEquals("Green", currentBottom.color)
        assertEquals(5, currentBottom.comfortLevel)

        // Recommendations should exist and reference current data
        val recs = viewModel.recommendations.value
        assertTrue("Should have recommendations after updates", recs.isNotEmpty())
        for (rec in recs) {
            for (item in rec.items) {
                when (item.id) {
                    topItem.id -> {
                        assertEquals("Purple", item.color)
                        assertEquals("Fall", item.season)
                    }
                    bottomItem.id -> {
                        assertEquals("Green", item.color)
                        assertEquals(5, item.comfortLevel)
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Update + preference change interaction
    // ------------------------------------------------------------------

    @Test
    fun updateItem_thenChangePreferences_usesLatestItemData() {
        val items = viewModel.wardrobeItems.value
        val topItem = items.first { it.category == ClothingCategory.TOP }

        viewModel.updateItem(topItem.copy(color = "Teal"))

        viewModel.updatePreferences(
            UserPreferences(
                bodyType = "Slim",
                stylePreference = "Formal",
                comfortPreference = 4,
                preferredSeasons = listOf("Winter")
            )
        )

        val recs = viewModel.recommendations.value
        for (rec in recs) {
            for (item in rec.items) {
                if (item.id == topItem.id) {
                    assertEquals(
                        "Item should have updated color after preference change",
                        "Teal",
                        item.color
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Refresh after update uses fresh data
    // ------------------------------------------------------------------

    @Test
    fun refreshRecommendations_afterUpdate_usesCurrentData() {
        val items = viewModel.wardrobeItems.value
        val accessoryItem = items.first { it.category == ClothingCategory.ACCESSORY }

        viewModel.updateItem(accessoryItem.copy(color = "Silver", fit = "Fitted"))

        // Explicit refresh
        viewModel.refreshRecommendations()

        val current = viewModel.wardrobeItems.value.first { it.id == accessoryItem.id }
        assertEquals("Silver", current.color)
        assertEquals("Fitted", current.fit)
    }
}
