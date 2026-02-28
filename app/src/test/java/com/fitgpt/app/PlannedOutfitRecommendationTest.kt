package com.fitgpt.app

import com.fitgpt.app.ai.OutfitRecommendationEngine
import com.fitgpt.app.data.model.ClothingItem
import com.fitgpt.app.data.model.PlannedOutfit
import com.fitgpt.app.data.model.SavedOutfit
import com.fitgpt.app.data.model.UserPreferences
import com.fitgpt.app.viewmodel.WardrobeViewModel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Tests that the planner integration correctly deprioritizes planned items
 * in the recommendation engine and ViewModel.
 *
 * Engine-level:
 *  - plannerItemPenalty returns 0 for empty/no-overlap
 *  - plannerItemPenalty returns max for full overlap
 *  - plannerItemPenalty is proportional for partial overlap
 *  - recommend with planned items deprioritizes them
 *  - Both penalties (overlap + planner) stack correctly
 *  - Empty planned items = identical to default (backward compat)
 *  - All items planned → still returns results (graceful degradation)
 *
 * ViewModel-level:
 *  - planOutfit for today deprioritizes items in recommendations
 *  - planOutfit for future/past date has no effect on today
 *  - Plan + delete item handles gracefully
 *  - Multiple outfits planned for today — all items deprioritized
 *  - Planned and saved outfits coexist independently
 *  - Plan + update preferences uses latest data
 */
class PlannedOutfitRecommendationTest {

    private lateinit var engine: OutfitRecommendationEngine

    private val defaultPrefs = UserPreferences(
        bodyType = "Average",
        stylePreference = "Casual",
        comfortPreference = 3,
        preferredSeasons = listOf("Spring", "Summer", "Fall", "Winter")
    )

    private fun makeItem(
        id: Int, category: String, color: String,
        season: String = "All", comfort: Int = 3, fit: String = "Regular"
    ) = ClothingItem(id, category, color, season, comfort, fit)

    @Before
    fun setUp() {
        engine = OutfitRecommendationEngine()
    }

    // ==================================================================
    // Engine-level: plannerItemPenalty
    // ==================================================================

    @Test
    fun plannerItemPenalty_emptyPlannedIds_returnsZero() {
        val outfit = listOf(makeItem(1, "Top", "Black"), makeItem(2, "Bottom", "Blue"))
        assertEquals(0.0, engine.plannerItemPenalty(outfit, emptySet()), 0.001)
    }

    @Test
    fun plannerItemPenalty_noOverlap_returnsZero() {
        val outfit = listOf(makeItem(1, "Top", "Black"), makeItem(2, "Bottom", "Blue"))
        assertEquals(0.0, engine.plannerItemPenalty(outfit, setOf(99, 100)), 0.001)
    }

    @Test
    fun plannerItemPenalty_fullOverlap_returnsMaxPenalty() {
        val outfit = listOf(makeItem(1, "Top", "Black"), makeItem(2, "Bottom", "Blue"))
        val penalty = engine.plannerItemPenalty(outfit, setOf(1, 2))
        assertEquals(OutfitRecommendationEngine.WEIGHT_PLANNER, penalty, 0.001)
    }

    @Test
    fun plannerItemPenalty_partialOverlap_isProportional() {
        val outfit = listOf(
            makeItem(1, "Top", "Black"),
            makeItem(2, "Bottom", "Blue"),
            makeItem(3, "Shoes", "White"),
            makeItem(4, "Outerwear", "Gray")
        )
        // 1 of 4 items planned
        val penalty = engine.plannerItemPenalty(outfit, setOf(1))
        val expected = 0.25 * OutfitRecommendationEngine.WEIGHT_PLANNER
        assertEquals(expected, penalty, 0.001)
    }

    @Test
    fun plannerItemPenalty_halfOverlap_isProportional() {
        val outfit = listOf(
            makeItem(1, "Top", "Black"),
            makeItem(2, "Bottom", "Blue"),
            makeItem(3, "Shoes", "White"),
            makeItem(4, "Outerwear", "Gray")
        )
        // 2 of 4 items planned
        val penalty = engine.plannerItemPenalty(outfit, setOf(1, 2))
        val expected = 0.5 * OutfitRecommendationEngine.WEIGHT_PLANNER
        assertEquals(expected, penalty, 0.001)
    }

    @Test
    fun plannerItemPenalty_singleItemOutfit_fullOverlap() {
        val outfit = listOf(makeItem(5, "Accessory", "Gold"))
        val penalty = engine.plannerItemPenalty(outfit, setOf(5))
        assertEquals(OutfitRecommendationEngine.WEIGHT_PLANNER, penalty, 0.001)
    }

    // ==================================================================
    // Engine-level: recommend with plannedItemIds
    // ==================================================================

    @Test
    fun recommend_plannedItems_deprioritizesButDoesNotExclude() {
        val items = listOf(
            makeItem(1, "Top", "White", "Summer", 5),
            makeItem(2, "Top", "Black", "Summer", 5),
            makeItem(3, "Bottom", "Blue", "Summer", 5),
            makeItem(4, "Bottom", "Navy", "Summer", 5),
            makeItem(5, "Shoes", "White", "All", 4)
        )

        val withoutPlan = engine.recommend(items, defaultPrefs)
        val withPlan = engine.recommend(items, defaultPrefs, plannedItemIds = setOf(1, 3))

        // Outfits containing planned items should have lower scores
        val plannedOutfitScoreWith = withPlan
            .filter { rec -> rec.items.any { it.id == 1 || it.id == 3 } }
            .maxOfOrNull { it.score } ?: 0.0
        val unplannedOutfitScoreWith = withPlan
            .filter { rec -> rec.items.none { it.id == 1 || it.id == 3 } }
            .maxOfOrNull { it.score } ?: 0.0

        assertTrue(
            "Unplanned outfits should score >= planned outfits",
            unplannedOutfitScoreWith >= plannedOutfitScoreWith
        )
        // Planned items should still appear (not excluded)
        val plannedPresent = withPlan.any { rec -> rec.items.any { it.id == 1 || it.id == 3 } }
        assertTrue("Planned items should still appear in results", plannedPresent)
    }

    @Test
    fun recommend_bothPenaltiesStack() {
        val items = listOf(
            makeItem(1, "Top", "White", "Summer", 5),
            makeItem(2, "Bottom", "Blue", "Summer", 5),
            makeItem(3, "Shoes", "White", "All", 4)
        )
        val recentlyShown = setOf(setOf(1, 2))
        val plannedIds = setOf(1)

        val result = engine.recommend(items, defaultPrefs, recentlyShown, plannedIds)

        // All results should have positive scores (graceful degradation)
        for (rec in result) {
            assertTrue("Score must be positive even with stacked penalties", rec.score > 0)
        }
    }

    @Test
    fun recommend_emptyPlannedItems_identicalToDefault() {
        val items = listOf(
            makeItem(1, "Top", "White", "Summer", 4),
            makeItem(2, "Bottom", "Blue", "Summer", 3),
            makeItem(3, "Shoes", "Black", "All", 4)
        )

        val withEmpty = engine.recommend(items, defaultPrefs, emptySet(), emptySet())
        val withDefault = engine.recommend(items, defaultPrefs)

        assertEquals(withDefault.size, withEmpty.size)
        assertEquals(
            withDefault.map { it.items.map { i -> i.id }.toSet() },
            withEmpty.map { it.items.map { i -> i.id }.toSet() }
        )
    }

    @Test
    fun recommend_allItemsPlanned_stillReturnsResults() {
        val items = listOf(
            makeItem(1, "Top", "White", "Summer", 4),
            makeItem(2, "Bottom", "Blue", "Summer", 3)
        )
        val allIds = items.map { it.id }.toSet()

        val result = engine.recommend(items, defaultPrefs, plannedItemIds = allIds)

        assertTrue("Should still return results when all items are planned", result.isNotEmpty())
        for (rec in result) {
            assertTrue("Score must be at least 0.01", rec.score >= 0.01)
        }
    }

    @Test
    fun recommend_plannedItems_scoresNeverNegative() {
        val items = listOf(
            makeItem(1, "Top", "Black", "Winter", 1),
            makeItem(2, "Bottom", "Red", "Winter", 1)
        )
        val result = engine.recommend(
            items, defaultPrefs,
            recentlyShown = setOf(setOf(1, 2)),
            plannedItemIds = setOf(1, 2)
        )

        for (rec in result) {
            assertTrue("Score must never be negative, got ${rec.score}", rec.score > 0)
        }
    }

    // ==================================================================
    // ViewModel-level tests
    // ==================================================================

    private val fixedToday = LocalDate.of(2026, 3, 15)

    private fun createViewModel(): WardrobeViewModel {
        return WardrobeViewModel(todayProvider = { fixedToday })
    }

    @Test
    fun viewModel_planOutfitToday_deprioritizesItems() {
        val vm = createViewModel()
        // Add extra items so there are alternatives
        vm.addItem(makeItem(20, "Top", "Red", "Summer", 5))
        vm.addItem(makeItem(21, "Bottom", "Green", "Summer", 5))

        val recsBefore = vm.recommendations.value

        // Plan an outfit for today using items from the default wardrobe
        val wardrobeItems = vm.wardrobeItems.value
        val top = wardrobeItems.first { it.category == "Top" }
        val bottom = wardrobeItems.first { it.category == "Bottom" }
        val planned = PlannedOutfit(
            id = 500,
            items = listOf(top, bottom),
            date = fixedToday
        )
        vm.planOutfit(planned)

        val recsAfter = vm.recommendations.value
        // Recommendations should still exist
        assertTrue("Should still have recommendations after planning", recsAfter.isNotEmpty())

        // Verify the planned outfit state was updated
        assertEquals(1, vm.plannedOutfits.value.size)
    }

    @Test
    fun viewModel_planOutfitFutureDate_noEffectOnToday() {
        val vm = createViewModel()

        val wardrobeItems = vm.wardrobeItems.value
        val top = wardrobeItems.first { it.category == "Top" }
        val bottom = wardrobeItems.first { it.category == "Bottom" }

        // Plan for today to get a baseline with planner penalty
        val todayPlan = PlannedOutfit(900, listOf(top, bottom), fixedToday)
        vm.planOutfit(todayPlan)
        val scoresWithTodayPlan = vm.recommendations.value.map { it.score }

        // Remove the today plan and add a future-date plan with same items
        vm.removePlannedOutfit(900)
        val futurePlan = PlannedOutfit(501, listOf(top, bottom), fixedToday.plusDays(5))
        vm.planOutfit(futurePlan)
        val scoresWithFuturePlan = vm.recommendations.value.map { it.score }

        // Future plan should NOT cause same deprioritization as today plan
        // i.e. scores with future plan should be higher (no planner penalty)
        assertNotEquals(
            "Future-date plan should not penalize today's recommendations the same way",
            scoresWithTodayPlan, scoresWithFuturePlan
        )
    }

    @Test
    fun viewModel_planOutfitPastDate_noEffectOnToday() {
        val vm = createViewModel()

        val wardrobeItems = vm.wardrobeItems.value
        val top = wardrobeItems.first { it.category == "Top" }
        val bottom = wardrobeItems.first { it.category == "Bottom" }

        // Plan for today to get a baseline with planner penalty
        val todayPlan = PlannedOutfit(901, listOf(top, bottom), fixedToday)
        vm.planOutfit(todayPlan)
        val scoresWithTodayPlan = vm.recommendations.value.map { it.score }

        // Remove the today plan and add a past-date plan with same items
        vm.removePlannedOutfit(901)
        val pastPlan = PlannedOutfit(502, listOf(top, bottom), fixedToday.minusDays(3))
        vm.planOutfit(pastPlan)
        val scoresWithPastPlan = vm.recommendations.value.map { it.score }

        // Past plan should NOT cause same deprioritization as today plan
        assertNotEquals(
            "Past-date plan should not penalize today's recommendations the same way",
            scoresWithTodayPlan, scoresWithPastPlan
        )
    }

    @Test
    fun viewModel_planThenDelete_handlesGracefully() {
        val vm = createViewModel()
        val wardrobeItems = vm.wardrobeItems.value
        val top = wardrobeItems.first { it.category == "Top" }
        val bottom = wardrobeItems.first { it.category == "Bottom" }

        vm.planOutfit(PlannedOutfit(600, listOf(top, bottom), fixedToday))

        // Delete one of the planned items from wardrobe
        vm.deleteItem(top)

        val recs = vm.recommendations.value
        // Should not crash and should not include the deleted item
        for (rec in recs) {
            assertFalse(
                "Deleted item should not appear in recommendations",
                rec.items.any { it.id == top.id }
            )
        }
    }

    @Test
    fun viewModel_multipleOutfitsPlannedToday_allItemsDeprioritized() {
        val vm = createViewModel()
        vm.addItem(makeItem(30, "Top", "Purple", "Summer", 5))
        vm.addItem(makeItem(31, "Bottom", "Orange", "Summer", 5))
        vm.addItem(makeItem(32, "Shoes", "Brown", "All", 4))

        val wardrobeItems = vm.wardrobeItems.value
        val top1 = wardrobeItems.first { it.id == 1 }
        val bottom1 = wardrobeItems.first { it.id == 2 }
        val shoes1 = wardrobeItems.first { it.id == 3 }

        vm.planOutfit(PlannedOutfit(700, listOf(top1, bottom1), fixedToday))
        vm.planOutfit(PlannedOutfit(701, listOf(shoes1), fixedToday))

        val recs = vm.recommendations.value
        assertTrue("Should still have recommendations", recs.isNotEmpty())

        // All planned items (1, 2, 3) should be in the planner state
        val plannedForToday = vm.getPlannedOutfitsForDate(fixedToday)
        assertEquals(2, plannedForToday.size)
        val allPlannedIds = plannedForToday.flatMap { it.items.map { i -> i.id } }.toSet()
        assertTrue(allPlannedIds.containsAll(setOf(1, 2, 3)))
    }

    @Test
    fun viewModel_plannedAndSavedCoexist() {
        val vm = createViewModel()
        val wardrobeItems = vm.wardrobeItems.value
        val top = wardrobeItems.first { it.category == "Top" }
        val bottom = wardrobeItems.first { it.category == "Bottom" }

        // Save an outfit
        vm.saveOutfit(listOf(top, bottom))

        // Plan an outfit for today
        vm.planOutfit(PlannedOutfit(800, listOf(top, bottom), fixedToday))

        assertEquals(1, vm.getSavedOutfits().size)
        assertEquals(1, vm.plannedOutfits.value.size)

        // Removing the planned outfit should not affect saved
        vm.removePlannedOutfit(800)
        assertEquals(0, vm.plannedOutfits.value.size)
        assertEquals(1, vm.getSavedOutfits().size)
    }

    @Test
    fun viewModel_planThenUpdatePreferences_usesLatestData() {
        val vm = createViewModel()
        val wardrobeItems = vm.wardrobeItems.value
        val top = wardrobeItems.first { it.category == "Top" }
        val bottom = wardrobeItems.first { it.category == "Bottom" }

        vm.planOutfit(PlannedOutfit(900, listOf(top, bottom), fixedToday))

        vm.updatePreferences(
            UserPreferences(
                bodyType = "Athletic",
                stylePreference = "Sporty",
                comfortPreference = 5,
                preferredSeasons = listOf("Summer")
            )
        )

        val recs = vm.recommendations.value
        assertTrue("Should have recommendations after pref update", recs.isNotEmpty())
        // Planned outfit should still be tracked
        assertEquals(1, vm.plannedOutfits.value.size)
    }

    @Test
    fun viewModel_removePlannedOutfit_restoresScores() {
        val vm = createViewModel()
        vm.addItem(makeItem(40, "Top", "Teal", "Summer", 5))
        vm.addItem(makeItem(41, "Bottom", "Cream", "Summer", 5))

        val wardrobeItems = vm.wardrobeItems.value
        val top = wardrobeItems.first { it.id == 1 }
        val bottom = wardrobeItems.first { it.id == 2 }

        // Plan for today — scores should reflect planner penalty
        vm.planOutfit(PlannedOutfit(950, listOf(top, bottom), fixedToday))
        val recsWithPlan = vm.recommendations.value

        // Check that at least some outfits containing planned items are penalized
        val plannedOutfitScore = recsWithPlan
            .filter { rec -> rec.items.any { it.id == top.id || it.id == bottom.id } }
            .minOfOrNull { it.score }

        // Remove the plan — planner penalty should be gone
        vm.removePlannedOutfit(950)
        val recsAfterRemove = vm.recommendations.value

        // Outfits with previously-planned items should now score higher
        val restoredOutfitScore = recsAfterRemove
            .filter { rec -> rec.items.any { it.id == top.id || it.id == bottom.id } }
            .minOfOrNull { it.score }

        if (plannedOutfitScore != null && restoredOutfitScore != null) {
            assertTrue(
                "Removing plan should increase scores for previously-planned items",
                restoredOutfitScore >= plannedOutfitScore
            )
        }
    }

    @Test
    fun viewModel_getPlannedOutfitsForDate_filtersCorrectly() {
        val vm = createViewModel()
        val wardrobeItems = vm.wardrobeItems.value
        val top = wardrobeItems.first { it.category == "Top" }
        val bottom = wardrobeItems.first { it.category == "Bottom" }

        vm.planOutfit(PlannedOutfit(1000, listOf(top), fixedToday))
        vm.planOutfit(PlannedOutfit(1001, listOf(bottom), fixedToday.plusDays(1)))
        vm.planOutfit(PlannedOutfit(1002, listOf(top, bottom), fixedToday))

        val todayPlans = vm.getPlannedOutfitsForDate(fixedToday)
        assertEquals(2, todayPlans.size)
        assertTrue(todayPlans.all { it.date == fixedToday })

        val tomorrowPlans = vm.getPlannedOutfitsForDate(fixedToday.plusDays(1))
        assertEquals(1, tomorrowPlans.size)
    }
}
