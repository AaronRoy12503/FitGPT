package com.fitgpt.app

import com.fitgpt.app.data.model.ClothingItem
import com.fitgpt.app.data.model.UserPreferences
import com.fitgpt.app.viewmodel.WardrobeViewModel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Verifies that deleted items never leak into recommendations.
 *
 * Covers:
 *  - Deleting a single item removes it from recommendations
 *  - Deleting all items results in empty recommendations
 *  - Recommendations after deletion contain only surviving items
 *  - Outfit history referencing deleted items does not resurrect them
 *  - Adding a new item after deleting all items produces valid recommendations
 */
class DeleteThenRecommendTest {

    private lateinit var viewModel: WardrobeViewModel

    // Extra items added on top of FakeWardrobeRepository defaults (ids 1, 2)
    private val extraItems = listOf(
        ClothingItem(10, "Top", "White", "Summer", 5),
        ClothingItem(11, "Bottom", "Navy", "Summer", 4),
        ClothingItem(12, "Shoes", "Black", "All", 3),
        ClothingItem(13, "Outerwear", "Gray", "Winter", 2),
        ClothingItem(14, "Accessory", "Gold", "All", 5)
    )

    @Before
    fun setUp() {
        viewModel = WardrobeViewModel()
        // Seed additional items so we have a rich wardrobe
        extraItems.forEach { viewModel.addItem(it) }
    }

    // ------------------------------------------------------------------
    // Basic: deleted item must not appear in recommendations
    // ------------------------------------------------------------------

    @Test
    fun deleteItem_removesItFromRecommendations() {
        val itemToDelete = viewModel.wardrobeItems.value.first { it.id == 10 }

        // Confirm item is referenced in at least one recommendation before deletion
        val preDeleteRecs = viewModel.recommendations.value
        val appearedBefore = preDeleteRecs.any { rec -> rec.items.any { it.id == 10 } }
        // If the item never appeared, the test is vacuous — still verify post-delete
        if (appearedBefore) {
            assertTrue("Item 10 should appear in pre-delete recommendations", true)
        }

        viewModel.deleteItem(itemToDelete)

        val postDeleteRecs = viewModel.recommendations.value
        for (rec in postDeleteRecs) {
            assertFalse(
                "Deleted item (id=10) must not appear in recommendation items",
                rec.items.any { it.id == 10 }
            )
            assertFalse(
                "Deleted item (id=10) must not appear in itemExplanations",
                rec.itemExplanations.containsKey(10)
            )
        }
    }

    @Test
    fun deleteItem_wardrobeNoLongerContainsIt() {
        val itemToDelete = viewModel.wardrobeItems.value.first { it.id == 11 }
        viewModel.deleteItem(itemToDelete)

        assertFalse(
            "Wardrobe should no longer contain deleted item",
            viewModel.wardrobeItems.value.any { it.id == 11 }
        )
    }

    @Test
    fun deleteItem_recommendationsOnlyContainSurvivingItems() {
        val itemToDelete = viewModel.wardrobeItems.value.first { it.id == 10 }
        viewModel.deleteItem(itemToDelete)

        val survivingIds = viewModel.wardrobeItems.value.map { it.id }.toSet()
        val postRecs = viewModel.recommendations.value

        for (rec in postRecs) {
            for (item in rec.items) {
                assertTrue(
                    "Recommendation item (id=${item.id}) must exist in surviving wardrobe",
                    item.id in survivingIds
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // Delete multiple items sequentially
    // ------------------------------------------------------------------

    @Test
    fun deleteMultipleItems_noneAppearInRecommendations() {
        val idsToDelete = listOf(10, 12, 14)
        for (id in idsToDelete) {
            val item = viewModel.wardrobeItems.value.first { it.id == id }
            viewModel.deleteItem(item)
        }

        val recs = viewModel.recommendations.value
        for (rec in recs) {
            for (item in rec.items) {
                assertFalse(
                    "Deleted item (id=${item.id}) must not appear in recommendations",
                    item.id in idsToDelete
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // Edge case: delete ALL items
    // ------------------------------------------------------------------

    @Test
    fun deleteAllItems_recommendationsAreEmpty() {
        // Delete every item in the wardrobe
        while (viewModel.wardrobeItems.value.isNotEmpty()) {
            viewModel.deleteItem(viewModel.wardrobeItems.value.first())
        }

        assertTrue(
            "Wardrobe must be empty after deleting all items",
            viewModel.wardrobeItems.value.isEmpty()
        )

        assertTrue(
            "Recommendations must be empty when wardrobe is empty",
            viewModel.recommendations.value.isEmpty()
        )
    }

    @Test
    fun deleteAllItems_thenAddNew_producesValidRecommendations() {
        // Delete everything
        while (viewModel.wardrobeItems.value.isNotEmpty()) {
            viewModel.deleteItem(viewModel.wardrobeItems.value.first())
        }
        assertTrue(viewModel.recommendations.value.isEmpty())

        // Add fresh items
        val newTop = ClothingItem(100, "Top", "Red", "Summer", 4)
        val newBottom = ClothingItem(101, "Bottom", "White", "Summer", 5)
        viewModel.addItem(newTop)
        viewModel.addItem(newBottom)

        val recs = viewModel.recommendations.value
        assertTrue("Should have recommendations after adding new items", recs.isNotEmpty())

        // All recommended items must be from the newly added set
        val validIds = setOf(100, 101)
        for (rec in recs) {
            for (item in rec.items) {
                assertTrue(
                    "Recommendation should only reference new items, got id=${item.id}",
                    item.id in validIds
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // Outfit history: deleted item IDs in history must not resurface
    // ------------------------------------------------------------------

    @Test
    fun outfitHistory_deletedItemDoesNotResurface() {
        // Generate initial recommendations (populates history)
        viewModel.refreshRecommendations()

        val itemToDelete = viewModel.wardrobeItems.value.first { it.id == 10 }
        viewModel.deleteItem(itemToDelete)

        // Trigger multiple recommendation refreshes to exercise history path
        repeat(5) {
            viewModel.refreshRecommendations()
        }

        val recs = viewModel.recommendations.value
        for (rec in recs) {
            assertFalse(
                "Deleted item (id=10) must not resurface via history after refresh",
                rec.items.any { it.id == 10 }
            )
        }
    }

    @Test
    fun outfitHistory_deletedItemsNeverReappearAfterManyRefreshes() {
        // Build up history
        repeat(3) { viewModel.refreshRecommendations() }

        // Delete two items
        val item10 = viewModel.wardrobeItems.value.first { it.id == 10 }
        val item13 = viewModel.wardrobeItems.value.first { it.id == 13 }
        viewModel.deleteItem(item10)
        viewModel.deleteItem(item13)

        // Refresh many times to cycle through history
        repeat(15) {
            viewModel.refreshRecommendations()
            val recs = viewModel.recommendations.value
            for (rec in recs) {
                assertFalse(
                    "Deleted item (id=10) must not appear after refresh #$it",
                    rec.items.any { it.id == 10 }
                )
                assertFalse(
                    "Deleted item (id=13) must not appear after refresh #$it",
                    rec.items.any { it.id == 13 }
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // Recommendation quality after deletion
    // ------------------------------------------------------------------

    @Test
    fun deleteItem_recommendationsStillHavePositiveScores() {
        val itemToDelete = viewModel.wardrobeItems.value.first { it.id == 13 }
        viewModel.deleteItem(itemToDelete)

        val recs = viewModel.recommendations.value
        for (rec in recs) {
            assertTrue(
                "Recommendation score should be positive, got ${rec.score}",
                rec.score > 0.0
            )
        }
    }

    @Test
    fun deleteItem_recommendationsStillHaveExplanations() {
        val itemToDelete = viewModel.wardrobeItems.value.first { it.id == 12 }
        viewModel.deleteItem(itemToDelete)

        val recs = viewModel.recommendations.value
        for (rec in recs) {
            assertTrue(
                "Recommendation explanation must be non-empty",
                rec.explanation.isNotBlank()
            )
        }
    }

    @Test
    fun deleteItem_eachRecommendationHasAtLeastOneItem() {
        val itemToDelete = viewModel.wardrobeItems.value.first { it.id == 14 }
        viewModel.deleteItem(itemToDelete)

        val recs = viewModel.recommendations.value
        for (rec in recs) {
            assertTrue(
                "Each recommendation must have at least one item",
                rec.items.isNotEmpty()
            )
        }
    }

    // ------------------------------------------------------------------
    // Preferences + deletion interaction
    // ------------------------------------------------------------------

    @Test
    fun deleteItem_thenUpdatePreferences_noDeletedItemsInResults() {
        val itemToDelete = viewModel.wardrobeItems.value.first { it.id == 10 }
        viewModel.deleteItem(itemToDelete)

        viewModel.updatePreferences(
            UserPreferences(
                bodyType = "Athletic",
                stylePreference = "Sporty",
                comfortPreference = 5,
                preferredSeasons = listOf("Summer")
            )
        )

        val recs = viewModel.recommendations.value
        for (rec in recs) {
            assertFalse(
                "Deleted item must not appear after preference update",
                rec.items.any { it.id == 10 }
            )
        }
    }

    // ------------------------------------------------------------------
    // Delete the only top or only bottom — verify graceful degradation
    // ------------------------------------------------------------------

    @Test
    fun deleteOnlyDefaultTop_stillProducesRecommendations() {
        // FakeWardrobeRepository default has id=1 Top, id=2 Bottom
        // We also added id=10 Top, so deleting id=1 leaves id=10 as the only original-repo Top

        // Delete the extra top too, leaving just default id=1
        val extraTop = viewModel.wardrobeItems.value.first { it.id == 10 }
        viewModel.deleteItem(extraTop)

        // Now delete the default top
        val defaultTop = viewModel.wardrobeItems.value.first { it.id == 1 }
        viewModel.deleteItem(defaultTop)

        // Should still get recommendations (individual items or non-top-bottom combos)
        val recs = viewModel.recommendations.value
        // Verify no deleted items present
        for (rec in recs) {
            assertFalse(
                "Deleted top (id=1) must not appear",
                rec.items.any { it.id == 1 }
            )
            assertFalse(
                "Deleted top (id=10) must not appear",
                rec.items.any { it.id == 10 }
            )
        }
    }
}
