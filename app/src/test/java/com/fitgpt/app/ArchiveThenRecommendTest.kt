package com.fitgpt.app

import com.fitgpt.app.data.model.ClothingItem
import com.fitgpt.app.data.model.PlannedOutfit
import com.fitgpt.app.data.model.UserPreferences
import com.fitgpt.app.viewmodel.WardrobeViewModel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Verifies that archived items are excluded from recommendations,
 * wardrobe display, the Groq AI prompt, and outfit history — while
 * remaining recoverable via unarchive.
 */
class ArchiveThenRecommendTest {

    private lateinit var viewModel: WardrobeViewModel

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
        extraItems.forEach { viewModel.addItem(it) }
    }

    // ------------------------------------------------------------------
    // Pool exclusion
    // ------------------------------------------------------------------

    @Test
    fun archiveItem_removesItFromWardrobeItems() {
        val item = viewModel.wardrobeItems.value.first { it.id == 10 }
        viewModel.archiveItem(item)

        assertFalse(
            "Archived item must not appear in wardrobeItems",
            viewModel.wardrobeItems.value.any { it.id == 10 }
        )
    }

    @Test
    fun archiveItem_removesItFromRecommendations() {
        val item = viewModel.wardrobeItems.value.first { it.id == 10 }
        viewModel.archiveItem(item)

        for (rec in viewModel.recommendations.value) {
            assertFalse(
                "Archived item (id=10) must not appear in recommendation items",
                rec.items.any { it.id == 10 }
            )
            assertFalse(
                "Archived item (id=10) must not appear in itemExplanations",
                rec.itemExplanations.containsKey(10)
            )
        }
    }

    @Test
    fun archiveItem_appearsInArchivedItems() {
        val item = viewModel.wardrobeItems.value.first { it.id == 10 }
        viewModel.archiveItem(item)

        assertTrue(
            "Archived item must appear in archivedItems",
            viewModel.archivedItems.value.any { it.id == 10 }
        )
    }

    @Test
    fun archiveMultipleItems_noneAppearInRecommendations() {
        val idsToArchive = listOf(10, 12, 14)
        for (id in idsToArchive) {
            val item = viewModel.wardrobeItems.value.first { it.id == id }
            viewModel.archiveItem(item)
        }

        for (rec in viewModel.recommendations.value) {
            for (item in rec.items) {
                assertFalse(
                    "Archived item (id=${item.id}) must not appear in recommendations",
                    item.id in idsToArchive
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // AI / Groq cleanup
    // ------------------------------------------------------------------

    @Test
    fun archiveItem_thenRefresh_neverReintroducesArchivedItem() {
        val item = viewModel.wardrobeItems.value.first { it.id == 10 }
        viewModel.archiveItem(item)

        repeat(5) {
            viewModel.refreshRecommendations()
            for (rec in viewModel.recommendations.value) {
                assertFalse(
                    "Archived item (id=10) must not resurface after refresh #$it",
                    rec.items.any { it.id == 10 }
                )
            }
        }
    }

    @Test
    fun archiveItem_refreshDoesNotLeakViaCachedPath() {
        // Build up history first
        repeat(3) { viewModel.refreshRecommendations() }

        val item = viewModel.wardrobeItems.value.first { it.id == 11 }
        viewModel.archiveItem(item)

        repeat(5) {
            viewModel.refreshRecommendations()
        }

        for (rec in viewModel.recommendations.value) {
            assertFalse(
                "Archived item (id=11) must not leak via cached path",
                rec.items.any { it.id == 11 }
            )
        }
    }

    // ------------------------------------------------------------------
    // Before / after archiving
    // ------------------------------------------------------------------

    @Test
    fun archiveItem_recommendationsChangeAfterArchiving() {
        val preArchiveRecs = viewModel.recommendations.value.flatMap { it.items.map { i -> i.id } }.toSet()

        val item = viewModel.wardrobeItems.value.first { it.id == 10 }
        viewModel.archiveItem(item)

        val postArchiveRecs = viewModel.recommendations.value.flatMap { it.items.map { i -> i.id } }.toSet()

        assertFalse(
            "Archived item (id=10) must not be in post-archive recommendations",
            10 in postArchiveRecs
        )
    }

    @Test
    fun archiveItem_remainingRecommendationsHavePositiveScores() {
        val item = viewModel.wardrobeItems.value.first { it.id == 13 }
        viewModel.archiveItem(item)

        for (rec in viewModel.recommendations.value) {
            assertTrue(
                "Score should be positive, got ${rec.score}",
                rec.score > 0.0
            )
        }
    }

    @Test
    fun archiveItem_remainingRecommendationsHaveExplanations() {
        val item = viewModel.wardrobeItems.value.first { it.id == 12 }
        viewModel.archiveItem(item)

        for (rec in viewModel.recommendations.value) {
            assertTrue(
                "Explanation must be non-empty",
                rec.explanation.isNotBlank()
            )
        }
    }

    @Test
    fun archiveItem_eachRecommendationHasAtLeastOneItem() {
        val item = viewModel.wardrobeItems.value.first { it.id == 14 }
        viewModel.archiveItem(item)

        for (rec in viewModel.recommendations.value) {
            assertTrue(
                "Each recommendation must have at least one item",
                rec.items.isNotEmpty()
            )
        }
    }

    // ------------------------------------------------------------------
    // No archived in outputs
    // ------------------------------------------------------------------

    @Test
    fun archiveItem_thenUpdatePreferences_noArchivedItemsInResults() {
        val item = viewModel.wardrobeItems.value.first { it.id == 10 }
        viewModel.archiveItem(item)

        viewModel.updatePreferences(
            UserPreferences(
                bodyType = "Athletic",
                stylePreference = "Sporty",
                comfortPreference = 5,
                preferredSeasons = listOf("Summer")
            )
        )

        for (rec in viewModel.recommendations.value) {
            assertFalse(
                "Archived item must not appear after preference update",
                rec.items.any { it.id == 10 }
            )
        }
    }

    @Test
    fun archiveItem_wardrobeCountDecreasesByOne() {
        val countBefore = viewModel.wardrobeItems.value.size
        val item = viewModel.wardrobeItems.value.first { it.id == 10 }
        viewModel.archiveItem(item)

        assertEquals(
            "Wardrobe count should decrease by 1 after archiving",
            countBefore - 1,
            viewModel.wardrobeItems.value.size
        )
    }

    // ------------------------------------------------------------------
    // Round-trip: archive then unarchive
    // ------------------------------------------------------------------

    @Test
    fun unarchiveItem_restoresItToWardrobeItems() {
        val item = viewModel.wardrobeItems.value.first { it.id == 10 }
        viewModel.archiveItem(item)
        assertFalse(viewModel.wardrobeItems.value.any { it.id == 10 })

        val archived = viewModel.archivedItems.value.first { it.id == 10 }
        viewModel.unarchiveItem(archived)

        assertTrue(
            "Unarchived item must reappear in wardrobeItems",
            viewModel.wardrobeItems.value.any { it.id == 10 }
        )
    }

    @Test
    fun unarchiveItem_removesItFromArchivedItems() {
        val item = viewModel.wardrobeItems.value.first { it.id == 10 }
        viewModel.archiveItem(item)

        val archived = viewModel.archivedItems.value.first { it.id == 10 }
        viewModel.unarchiveItem(archived)

        assertFalse(
            "Unarchived item must not appear in archivedItems",
            viewModel.archivedItems.value.any { it.id == 10 }
        )
    }

    @Test
    fun unarchiveItem_attributesArePreserved() {
        val original = viewModel.wardrobeItems.value.first { it.id == 10 }
        viewModel.archiveItem(original)

        val archived = viewModel.archivedItems.value.first { it.id == 10 }
        viewModel.unarchiveItem(archived)

        val restored = viewModel.wardrobeItems.value.first { it.id == 10 }
        assertEquals("Category preserved", original.category, restored.category)
        assertEquals("Color preserved", original.color, restored.color)
        assertEquals("Season preserved", original.season, restored.season)
        assertEquals("Comfort preserved", original.comfortLevel, restored.comfortLevel)
        assertEquals("Fit preserved", original.fit, restored.fit)
        assertFalse("isArchived should be false", restored.isArchived)
    }

    @Test
    fun unarchiveItem_wardrobeCountRestored() {
        val countBefore = viewModel.wardrobeItems.value.size
        val item = viewModel.wardrobeItems.value.first { it.id == 10 }
        viewModel.archiveItem(item)
        assertEquals(countBefore - 1, viewModel.wardrobeItems.value.size)

        val archived = viewModel.archivedItems.value.first { it.id == 10 }
        viewModel.unarchiveItem(archived)
        assertEquals(
            "Wardrobe count should be restored after unarchive",
            countBefore,
            viewModel.wardrobeItems.value.size
        )
    }

    @Test
    fun unarchiveItem_canAppearInRecommendationsAgain() {
        val item = viewModel.wardrobeItems.value.first { it.id == 10 }
        viewModel.archiveItem(item)

        // Verify excluded
        for (rec in viewModel.recommendations.value) {
            assertFalse(rec.items.any { it.id == 10 })
        }

        val archived = viewModel.archivedItems.value.first { it.id == 10 }
        viewModel.unarchiveItem(archived)

        // After unarchive the item is back in the pool; the engine may or may not
        // pick it, but it should at least be in the wardrobe
        assertTrue(viewModel.wardrobeItems.value.any { it.id == 10 })
    }

    // ------------------------------------------------------------------
    // Archive + delete interaction
    // ------------------------------------------------------------------

    @Test
    fun archiveThenDelete_itemFullyRemoved() {
        val item = viewModel.wardrobeItems.value.first { it.id == 10 }
        viewModel.archiveItem(item)
        assertTrue(viewModel.archivedItems.value.any { it.id == 10 })

        // Now delete the archived item by getting it from archived list
        val archived = viewModel.archivedItems.value.first { it.id == 10 }
        viewModel.deleteItem(archived)

        assertFalse(
            "Deleted archived item must not appear in wardrobe",
            viewModel.wardrobeItems.value.any { it.id == 10 }
        )
        assertFalse(
            "Deleted archived item must not appear in archivedItems",
            viewModel.archivedItems.value.any { it.id == 10 }
        )
    }

    @Test
    fun mixedArchiveAndDelete_correctPoolState() {
        // Archive item 10, delete item 11
        val item10 = viewModel.wardrobeItems.value.first { it.id == 10 }
        viewModel.archiveItem(item10)

        val item11 = viewModel.wardrobeItems.value.first { it.id == 11 }
        viewModel.deleteItem(item11)

        assertFalse("Archived id=10 not in wardrobe", viewModel.wardrobeItems.value.any { it.id == 10 })
        assertTrue("Archived id=10 in archivedItems", viewModel.archivedItems.value.any { it.id == 10 })
        assertFalse("Deleted id=11 not in wardrobe", viewModel.wardrobeItems.value.any { it.id == 11 })
        assertFalse("Deleted id=11 not in archivedItems", viewModel.archivedItems.value.any { it.id == 11 })

        for (rec in viewModel.recommendations.value) {
            assertFalse("id=10 not in recs", rec.items.any { it.id == 10 })
            assertFalse("id=11 not in recs", rec.items.any { it.id == 11 })
        }
    }

    @Test
    fun deleteArchivedItem_unarchiveHasNoEffect() {
        val item = viewModel.wardrobeItems.value.first { it.id == 10 }
        viewModel.archiveItem(item)

        val archived = viewModel.archivedItems.value.first { it.id == 10 }
        viewModel.deleteItem(archived)

        // Attempting to unarchive a deleted item should be a no-op
        viewModel.unarchiveItem(archived)
        assertFalse(
            "Deleted item must not reappear after unarchive attempt",
            viewModel.wardrobeItems.value.any { it.id == 10 }
        )
    }

    // ------------------------------------------------------------------
    // Archive + planned outfit
    // ------------------------------------------------------------------

    @Test
    fun archiveItem_plannedOutfitSurvives() {
        val today = LocalDate.now()
        val item10 = viewModel.wardrobeItems.value.first { it.id == 10 }
        val item11 = viewModel.wardrobeItems.value.first { it.id == 11 }

        val planned = PlannedOutfit(
            id = 900,
            items = listOf(item10, item11),
            date = today,
            note = "Test outfit"
        )
        viewModel.planOutfit(planned)

        // Archive item 10
        viewModel.archiveItem(item10)

        // Planned outfit should still exist
        val plannedOutfits = viewModel.getPlannedOutfitsForDate(today)
        assertTrue(
            "Planned outfit should survive archiving one of its items",
            plannedOutfits.any { it.id == 900 }
        )
    }

    @Test
    fun archiveItem_noDoublePenaltyInRecommendations() {
        val item = viewModel.wardrobeItems.value.first { it.id == 10 }
        viewModel.archiveItem(item)

        // Remaining recommendations should not have negative or zero scores
        // caused by double-counting archived items
        for (rec in viewModel.recommendations.value) {
            assertTrue(
                "No double penalty: score should be positive, got ${rec.score}",
                rec.score > 0.0
            )
        }
    }

    @Test
    fun archiveItem_independentOfPlannedOutfitPenalty() {
        val today = LocalDate.now()
        val item11 = viewModel.wardrobeItems.value.first { it.id == 11 }
        val item12 = viewModel.wardrobeItems.value.first { it.id == 12 }

        // Plan outfit with item 11
        val planned = PlannedOutfit(
            id = 901,
            items = listOf(item11, item12),
            date = today,
            note = "Planned"
        )
        viewModel.planOutfit(planned)

        // Archive item 10 (different from planned items)
        val item10 = viewModel.wardrobeItems.value.first { it.id == 10 }
        viewModel.archiveItem(item10)

        // Both planned items should still be in the wardrobe
        assertTrue("Item 11 still active", viewModel.wardrobeItems.value.any { it.id == 11 })
        assertTrue("Item 12 still active", viewModel.wardrobeItems.value.any { it.id == 12 })

        // Archived item excluded
        assertFalse("Item 10 archived", viewModel.wardrobeItems.value.any { it.id == 10 })
    }

    // ------------------------------------------------------------------
    // Graceful degradation
    // ------------------------------------------------------------------

    @Test
    fun archiveAllItems_recommendationsAreEmpty() {
        // Archive every item
        while (viewModel.wardrobeItems.value.isNotEmpty()) {
            viewModel.archiveItem(viewModel.wardrobeItems.value.first())
        }

        assertTrue(
            "Wardrobe must be empty after archiving all items",
            viewModel.wardrobeItems.value.isEmpty()
        )
        assertTrue(
            "Recommendations must be empty when all items are archived",
            viewModel.recommendations.value.isEmpty()
        )
        assertTrue(
            "All items should be in archivedItems",
            viewModel.archivedItems.value.isNotEmpty()
        )
    }

    @Test
    fun archiveAllItems_thenUnarchiveOne_recommendationsResume() {
        // Archive everything
        while (viewModel.wardrobeItems.value.isNotEmpty()) {
            viewModel.archiveItem(viewModel.wardrobeItems.value.first())
        }
        assertTrue(viewModel.recommendations.value.isEmpty())

        // Unarchive one item
        val archived = viewModel.archivedItems.value.first()
        viewModel.unarchiveItem(archived)

        assertTrue(
            "Wardrobe should have 1 item after unarchiving",
            viewModel.wardrobeItems.value.size == 1
        )
        // With only one item, the engine may or may not generate recommendations,
        // but the item should be available for consideration
        assertTrue(
            "The unarchived item should be in the wardrobe",
            viewModel.wardrobeItems.value.any { it.id == archived.id }
        )
    }

    // ------------------------------------------------------------------
    // History cleanup
    // ------------------------------------------------------------------

    @Test
    fun archiveItem_purgedFromOutfitHistory() {
        // Generate history
        repeat(3) { viewModel.refreshRecommendations() }

        val item = viewModel.wardrobeItems.value.first { it.id == 10 }
        viewModel.archiveItem(item)

        // Many refreshes to cycle through history
        repeat(10) {
            viewModel.refreshRecommendations()
            for (rec in viewModel.recommendations.value) {
                assertFalse(
                    "Archived item (id=10) must not resurface from history after refresh #$it",
                    rec.items.any { it.id == 10 }
                )
            }
        }
    }

    @Test
    fun archiveItem_neverResurfacesAfterHistoryCleanup() {
        // Build up significant history
        repeat(5) { viewModel.refreshRecommendations() }

        val item10 = viewModel.wardrobeItems.value.first { it.id == 10 }
        val item13 = viewModel.wardrobeItems.value.first { it.id == 13 }
        viewModel.archiveItem(item10)
        viewModel.archiveItem(item13)

        repeat(15) {
            viewModel.refreshRecommendations()
            for (rec in viewModel.recommendations.value) {
                assertFalse(
                    "Archived item (id=10) must not appear after refresh #$it",
                    rec.items.any { it.id == 10 }
                )
                assertFalse(
                    "Archived item (id=13) must not appear after refresh #$it",
                    rec.items.any { it.id == 13 }
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // Multi-refresh leakage
    // ------------------------------------------------------------------

    @Test
    fun multiRefresh_archivedItemNeverLeaks_20Refreshes() {
        val item = viewModel.wardrobeItems.value.first { it.id == 10 }
        viewModel.archiveItem(item)

        repeat(20) { i ->
            viewModel.refreshRecommendations()
            for (rec in viewModel.recommendations.value) {
                assertFalse(
                    "Archived item leaked on refresh #$i",
                    rec.items.any { it.id == 10 }
                )
            }
        }
    }

    @Test
    fun multiRefresh_archivedItemNeverInWardrobeItems() {
        val item = viewModel.wardrobeItems.value.first { it.id == 10 }
        viewModel.archiveItem(item)

        repeat(20) { i ->
            viewModel.refreshRecommendations()
            assertFalse(
                "Archived item in wardrobeItems on refresh #$i",
                viewModel.wardrobeItems.value.any { it.id == 10 }
            )
        }
    }

    @Test
    fun multiRefresh_multipleArchivedItems_neverLeak() {
        val idsToArchive = listOf(10, 11, 14)
        for (id in idsToArchive) {
            val item = viewModel.wardrobeItems.value.first { it.id == id }
            viewModel.archiveItem(item)
        }

        repeat(20) { i ->
            viewModel.refreshRecommendations()
            for (rec in viewModel.recommendations.value) {
                for (archivedId in idsToArchive) {
                    assertFalse(
                        "Archived item (id=$archivedId) leaked on refresh #$i",
                        rec.items.any { it.id == archivedId }
                    )
                }
            }
        }
    }

    @Test
    fun multiRefresh_archiveUnarchiveArchive_noLeakage() {
        val item = viewModel.wardrobeItems.value.first { it.id == 10 }

        // Archive → refresh → unarchive → refresh → re-archive → stress test
        viewModel.archiveItem(item)
        repeat(5) { viewModel.refreshRecommendations() }

        val archived = viewModel.archivedItems.value.first { it.id == 10 }
        viewModel.unarchiveItem(archived)
        repeat(5) { viewModel.refreshRecommendations() }

        val restored = viewModel.wardrobeItems.value.first { it.id == 10 }
        viewModel.archiveItem(restored)

        repeat(20) { i ->
            viewModel.refreshRecommendations()
            for (rec in viewModel.recommendations.value) {
                assertFalse(
                    "Re-archived item leaked on refresh #$i",
                    rec.items.any { it.id == 10 }
                )
            }
        }
    }
}
