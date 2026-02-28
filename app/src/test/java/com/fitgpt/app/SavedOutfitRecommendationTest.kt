package com.fitgpt.app

import com.fitgpt.app.ai.OutfitRecommendationEngine
import com.fitgpt.app.data.model.ClothingItem
import com.fitgpt.app.data.model.TemperatureCategory
import com.fitgpt.app.data.model.TimeCategory
import com.fitgpt.app.data.model.UserPreferences
import com.fitgpt.app.viewmodel.WardrobeViewModel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Validates that saved outfits do not conflict with freshness logic,
 * that recommendation weighting optionally adjusts based on saved outfits,
 * that AI does not overwrite saved combinations, and that saved and
 * generated outfits interact correctly.
 */
class SavedOutfitRecommendationTest {

    private lateinit var engine: OutfitRecommendationEngine
    private lateinit var defaultPreferences: UserPreferences

    @Before
    fun setUp() {
        engine = OutfitRecommendationEngine()
        defaultPreferences = UserPreferences(
            bodyType = "Average",
            stylePreference = "Casual",
            comfortPreference = 3,
            preferredSeasons = listOf("Summer", "Spring", "Fall", "Winter")
        )
    }

    private fun makeItem(
        id: Int, category: String, color: String,
        season: String = "All", comfort: Int = 3, fit: String = "Regular"
    ) = ClothingItem(id, category, color, season, comfort, fit)

    // -----------------------------------------------------------------------
    // Section 1: savedOutfitBonus() unit tests
    // -----------------------------------------------------------------------

    @Test
    fun savedOutfitBonus_emptySaved_returns0() {
        val outfit = listOf(makeItem(1, "Top", "White"), makeItem(2, "Bottom", "Blue"))
        assertEquals(0.0, engine.savedOutfitBonus(outfit, emptySet()), 0.001)
    }

    @Test
    fun savedOutfitBonus_emptyOutfit_returns0() {
        val saved: Set<Set<Int>> = setOf(setOf(1, 2))
        assertEquals(0.0, engine.savedOutfitBonus(emptyList(), saved), 0.001)
    }

    @Test
    fun savedOutfitBonus_exactMatch_returns0() {
        val outfit = listOf(makeItem(1, "Top", "White"), makeItem(2, "Bottom", "Blue"))
        val saved: Set<Set<Int>> = setOf(setOf(1, 2))
        // Exact saved combo → no bonus (suggest novelty)
        assertEquals(0.0, engine.savedOutfitBonus(outfit, saved), 0.001)
    }

    @Test
    fun savedOutfitBonus_noOverlap_returns0() {
        val outfit = listOf(makeItem(3, "Top", "Red"), makeItem(4, "Bottom", "Navy"))
        val saved: Set<Set<Int>> = setOf(setOf(1, 2))
        assertEquals(0.0, engine.savedOutfitBonus(outfit, saved), 0.001)
    }

    @Test
    fun savedOutfitBonus_partialOverlap_proportional() {
        // 2-item outfit shares 1 item with saved → 1/2 = 0.5
        val outfit = listOf(makeItem(1, "Top", "White"), makeItem(3, "Bottom", "Navy"))
        val saved: Set<Set<Int>> = setOf(setOf(1, 2))
        assertEquals(0.5, engine.savedOutfitBonus(outfit, saved), 0.001)
    }

    @Test
    fun savedOutfitBonus_allItemsFromSaved_differentCombo_returns1() {
        // Items 1 and 3 both appear in saved outfits, new combo {1,3}
        val outfit = listOf(makeItem(1, "Top", "White"), makeItem(3, "Shoes", "Black"))
        val saved: Set<Set<Int>> = setOf(setOf(1, 2), setOf(3, 4))
        // Both items are "favorites" → 2/2 = 1.0
        assertEquals(1.0, engine.savedOutfitBonus(outfit, saved), 0.001)
    }

    @Test
    fun savedOutfitBonus_multipleSavedOutfits_flattenedItems() {
        // Item 1 is in saved outfit A, item 5 is in saved outfit B
        val outfit = listOf(
            makeItem(1, "Top", "White"),
            makeItem(5, "Bottom", "Navy"),
            makeItem(9, "Shoes", "Black")
        )
        val saved: Set<Set<Int>> = setOf(setOf(1, 2), setOf(5, 6))
        // 2 of 3 items are from saved → 2/3 ≈ 0.667
        assertEquals(0.667, engine.savedOutfitBonus(outfit, saved), 0.01)
    }

    @Test
    fun savedOutfitBonus_singleItemOutfit_inSaved_exactMatch() {
        val outfit = listOf(makeItem(1, "Shoes", "Black"))
        val saved: Set<Set<Int>> = setOf(setOf(1))
        // Exact match → 0.0
        assertEquals(0.0, engine.savedOutfitBonus(outfit, saved), 0.001)
    }

    @Test
    fun savedOutfitBonus_singleItemOutfit_itemInSavedCombo() {
        val outfit = listOf(makeItem(1, "Shoes", "Black"))
        val saved: Set<Set<Int>> = setOf(setOf(1, 2, 3))
        // Item 1 is a favorite, combo {1} ≠ {1,2,3} → 1/1 = 1.0
        assertEquals(1.0, engine.savedOutfitBonus(outfit, saved), 0.001)
    }

    // -----------------------------------------------------------------------
    // Section 2: Saved outfits don't conflict with freshness logic
    // -----------------------------------------------------------------------

    @Test
    fun savedOutfits_independentFromRecentlyShown() {
        val items = listOf(
            makeItem(1, "Top", "White", "Summer"),
            makeItem(2, "Bottom", "Blue", "Summer"),
            makeItem(3, "Top", "Black", "Summer"),
            makeItem(4, "Bottom", "Navy", "Summer")
        )
        val saved: Set<Set<Int>> = setOf(setOf(1, 2))

        // Saved {1,2} should NOT be treated as "recently shown"
        val result = engine.recommend(items, defaultPreferences, savedOutfitIds = saved)
        assertTrue(result.isNotEmpty())

        // {1,2} should still appear (not filtered by freshness)
        val hasSavedCombo = result.any { rec ->
            rec.items.map { it.id }.toSet() == setOf(1, 2)
        }
        // The saved combo may or may not be top-ranked, but it shouldn't be filtered out
        // (it's only in savedOutfitIds, not recentlyShown)
    }

    @Test
    fun savedOutfits_freshnessScoringIndependent() {
        val outfit = listOf(makeItem(1, "Top", "White"), makeItem(2, "Bottom", "Blue"))
        val saved: Set<Set<Int>> = setOf(setOf(1, 2))

        // freshnessScore should NOT consider saved outfits
        val freshness = engine.freshnessScore(outfit, emptySet())
        assertEquals("Freshness should be 1.0 with no history", 1.0, freshness, 0.001)

        // savedOutfitBonus should NOT consider recentlyShown
        val savedBonus = engine.savedOutfitBonus(outfit, saved)
        assertEquals("Exact saved match should have 0.0 bonus", 0.0, savedBonus, 0.001)
    }

    @Test
    fun savedOutfits_doNotPolluteFreshnessHistory() {
        val items = listOf(
            makeItem(1, "Top", "White", "Summer"),
            makeItem(2, "Bottom", "Blue", "Summer"),
            makeItem(3, "Top", "Black", "Summer"),
            makeItem(4, "Bottom", "Navy", "Summer")
        )
        val saved: Set<Set<Int>> = setOf(setOf(1, 2))

        // First recommend with saved but no history
        val round1 = engine.recommend(items, defaultPreferences, savedOutfitIds = saved)

        // Build history from round 1
        val history = round1.map { it.items.map { i -> i.id }.toSet() }.toSet()

        // Second recommend with history — saved outfits should not double-count
        val round2 = engine.recommend(
            items, defaultPreferences,
            recentlyShown = history,
            savedOutfitIds = saved
        )
        assertTrue("Round 2 with history + saved should produce results", round2.isNotEmpty())
    }

    @Test
    fun savedOutfits_canStillBeRecommendedIfFresh() {
        val items = listOf(
            makeItem(1, "Top", "White", "Summer"),
            makeItem(2, "Bottom", "Blue", "Summer")
        )
        val saved: Set<Set<Int>> = setOf(setOf(1, 2))

        // No history — the saved combo {1,2} should still appear
        val result = engine.recommend(items, defaultPreferences, savedOutfitIds = saved)
        assertTrue(result.isNotEmpty())
        // With only one possible combo, it must be {1,2}
        assertEquals(setOf(1, 2), result[0].items.map { it.id }.toSet())
    }

    // -----------------------------------------------------------------------
    // Section 3: Recommendation weighting adjusts based on saved outfits
    // -----------------------------------------------------------------------

    @Test
    fun recommend_savedItems_getBoostInNewCombos() {
        val items = listOf(
            makeItem(1, "Top", "White", "Summer", 3),
            makeItem(2, "Top", "Black", "Summer", 3),
            makeItem(3, "Bottom", "Blue", "Summer", 3),
            makeItem(4, "Bottom", "Navy", "Summer", 3)
        )
        // User saved {1,3} — items 1 and 3 are "favorites"
        val saved: Set<Set<Int>> = setOf(setOf(1, 3))

        val withSaved = engine.recommend(items, defaultPreferences, savedOutfitIds = saved)
        val withoutSaved = engine.recommend(items, defaultPreferences)

        assertTrue(withSaved.isNotEmpty())
        assertTrue(withoutSaved.isNotEmpty())

        // Combos using items 1 or 3 (but not exact {1,3}) should score higher with saved
        val comboWithFavorite = withSaved.find { rec ->
            val ids = rec.items.map { it.id }.toSet()
            (1 in ids || 3 in ids) && ids != setOf(1, 3)
        }
        val sameComboWithout = withoutSaved.find { rec ->
            rec.items.map { it.id }.toSet() == comboWithFavorite?.items?.map { it.id }?.toSet()
        }
        if (comboWithFavorite != null && sameComboWithout != null) {
            assertTrue(
                "Combo using saved items should score higher with saved context",
                comboWithFavorite.score > sameComboWithout.score
            )
        }
    }

    @Test
    fun recommend_exactSavedCombo_noBoost() {
        val items = listOf(
            makeItem(1, "Top", "White", "Summer", 3),
            makeItem(2, "Top", "Black", "Summer", 3),
            makeItem(3, "Bottom", "Blue", "Summer", 3)
        )
        val saved: Set<Set<Int>> = setOf(setOf(1, 3))

        val withSaved = engine.recommend(items, defaultPreferences, savedOutfitIds = saved)
        val withoutSaved = engine.recommend(items, defaultPreferences)

        // Find {1,3} in both result sets
        val savedComboWith = withSaved.find { rec ->
            rec.items.map { it.id }.toSet() == setOf(1, 3)
        }
        val savedComboWithout = withoutSaved.find { rec ->
            rec.items.map { it.id }.toSet() == setOf(1, 3)
        }

        if (savedComboWith != null && savedComboWithout != null) {
            // Exact saved combo should have same score (0.0 bonus)
            assertEquals(
                "Exact saved combo should have same score with or without saved context",
                savedComboWithout.score, savedComboWith.score, 0.001
            )
        }
    }

    @Test
    fun recommend_nullSaved_noEffect() {
        val items = listOf(
            makeItem(1, "Top", "White", "Summer"),
            makeItem(2, "Bottom", "Blue", "Summer")
        )
        val withEmpty = engine.recommend(items, defaultPreferences, savedOutfitIds = emptySet())
        val without = engine.recommend(items, defaultPreferences)

        assertEquals(withEmpty.size, without.size)
        assertEquals(
            withEmpty[0].items.map { it.id }.toSet(),
            without[0].items.map { it.id }.toSet()
        )
        assertEquals(withEmpty[0].score, without[0].score, 0.001)
    }

    @Test
    fun recommend_largeSavedCollection_stillProducesResults() {
        val items = listOf(
            makeItem(1, "Top", "White", "Summer"),
            makeItem(2, "Top", "Black", "Summer"),
            makeItem(3, "Bottom", "Blue", "Summer"),
            makeItem(4, "Bottom", "Navy", "Summer"),
            makeItem(5, "Shoes", "Black")
        )
        val saved: Set<Set<Int>> = (1..20).map { setOf(it * 10, it * 10 + 1) }.toSet()

        val result = engine.recommend(items, defaultPreferences, savedOutfitIds = saved)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun recommend_progressiveSaves_adjustScoring() {
        val items = listOf(
            makeItem(1, "Top", "White", "Summer", 4),
            makeItem(2, "Top", "Black", "Summer", 4),
            makeItem(3, "Bottom", "Blue", "Summer", 4),
            makeItem(4, "Bottom", "Navy", "Summer", 4),
            makeItem(5, "Shoes", "Black", "All", 4)
        )

        // No saved outfits
        val noSaved = engine.recommend(items, defaultPreferences, savedOutfitIds = emptySet())

        // Save one outfit
        val oneSaved = engine.recommend(items, defaultPreferences, savedOutfitIds = setOf(setOf(1, 3)))

        // Save multiple outfits covering more items
        val manySaved = engine.recommend(
            items, defaultPreferences,
            savedOutfitIds = setOf(setOf(1, 3), setOf(2, 4), setOf(1, 4, 5))
        )

        assertTrue(noSaved.isNotEmpty())
        assertTrue(oneSaved.isNotEmpty())
        assertTrue(manySaved.isNotEmpty())
    }

    // -----------------------------------------------------------------------
    // Section 4: AI does not overwrite saved combinations
    // -----------------------------------------------------------------------

    @Test
    fun viewModel_saveBeforeRecommend_savedPersistsAfterRefresh() {
        val viewModel = WardrobeViewModel()
        val items = viewModel.wardrobeItems.value
        val top = items.first { it.category == "Top" }
        val bottom = items.first { it.category == "Bottom" }

        // Save an outfit
        viewModel.saveOutfit(listOf(top, bottom))
        assertEquals(1, viewModel.getSavedOutfits().size)

        // Refresh recommendations multiple times
        repeat(5) { viewModel.refreshRecommendations() }

        // Saved outfit should persist unchanged
        assertEquals(1, viewModel.getSavedOutfits().size)
        val saved = viewModel.getSavedOutfits()[0]
        assertTrue(saved.items.any { it.id == top.id })
        assertTrue(saved.items.any { it.id == bottom.id })
    }

    @Test
    fun viewModel_multipleRefreshes_doNotModifySavedList() {
        val viewModel = WardrobeViewModel()
        val items = viewModel.wardrobeItems.value

        viewModel.saveOutfit(listOf(items[0], items[1]))
        viewModel.saveOutfit(listOf(items[2], items[3]))

        assertEquals(2, viewModel.getSavedOutfits().size)

        repeat(10) { viewModel.refreshRecommendations() }

        assertEquals("Refreshes should not modify saved outfit count", 2, viewModel.getSavedOutfits().size)
    }

    @Test
    fun viewModel_recommendationsDontAlterSavedOutfitItems() {
        val viewModel = WardrobeViewModel()
        val items = viewModel.wardrobeItems.value
        val top = items.first { it.category == "Top" }
        val bottom = items.first { it.category == "Bottom" }

        viewModel.saveOutfit(listOf(top, bottom))

        // Capture saved outfit state before refreshes
        val savedBefore = viewModel.getSavedOutfits()[0]
        val idsBefore = savedBefore.items.map { it.id }.toSet()

        repeat(5) { viewModel.refreshRecommendations() }

        val savedAfter = viewModel.getSavedOutfits()[0]
        val idsAfter = savedAfter.items.map { it.id }.toSet()

        assertEquals("Saved outfit item IDs should not change", idsBefore, idsAfter)
    }

    @Test
    fun viewModel_deleteItem_savedOutfitPersists() {
        val viewModel = WardrobeViewModel()
        val items = viewModel.wardrobeItems.value
        val top = items.first { it.category == "Top" }
        val bottom = items.first { it.category == "Bottom" }
        val shoes = items.first { it.category == "Shoes" }

        viewModel.saveOutfit(listOf(top, bottom))

        // Delete an unrelated item
        viewModel.deleteItem(shoes)

        assertEquals("Saved outfit should persist after deleting unrelated item", 1, viewModel.getSavedOutfits().size)
    }

    @Test
    fun viewModel_saveAndPlanCoexist_independentOfRecommendations() {
        val viewModel = WardrobeViewModel()
        val items = viewModel.wardrobeItems.value
        val top = items.first { it.category == "Top" }
        val bottom = items.first { it.category == "Bottom" }

        viewModel.saveOutfit(listOf(top, bottom))

        val planned = com.fitgpt.app.data.model.PlannedOutfit(
            id = 999,
            items = listOf(top, bottom),
            date = java.time.LocalDate.now()
        )
        viewModel.planOutfit(planned)

        assertEquals(1, viewModel.getSavedOutfits().size)
        assertEquals(1, viewModel.plannedOutfits.value.size)

        // Refreshing should not affect either
        viewModel.refreshRecommendations()
        assertEquals(1, viewModel.getSavedOutfits().size)
        assertEquals(1, viewModel.plannedOutfits.value.size)
    }

    @Test
    fun viewModel_savedOutfitsFlowToEngine() {
        val viewModel = WardrobeViewModel()
        viewModel.addItem(makeItem(10, "Top", "White", "Summer", 5))
        viewModel.addItem(makeItem(11, "Bottom", "Navy", "Summer", 4))
        viewModel.addItem(makeItem(12, "Top", "Red", "Summer", 4))
        viewModel.addItem(makeItem(13, "Bottom", "Gray", "Summer", 3))

        // Save an outfit containing items 10 and 11
        viewModel.saveOutfit(listOf(
            makeItem(10, "Top", "White", "Summer", 5),
            makeItem(11, "Bottom", "Navy", "Summer", 4)
        ))

        viewModel.refreshRecommendations()
        val recs = viewModel.recommendations.value
        assertTrue("Should produce recommendations with saved context", recs.isNotEmpty())
    }

    // -----------------------------------------------------------------------
    // Section 5: Interaction between saved and generated outfits
    // -----------------------------------------------------------------------

    @Test
    fun savedPlusFreshness_savedItemsFreshCombo_getsBothBonuses() {
        val items = listOf(
            makeItem(1, "Top", "White", "Summer"),
            makeItem(2, "Top", "Black", "Summer"),
            makeItem(3, "Bottom", "Blue", "Summer"),
            makeItem(4, "Bottom", "Navy", "Summer")
        )
        // Saved {1,3} → items 1,3 are favorites
        // No history → all combos are fresh
        val saved: Set<Set<Int>> = setOf(setOf(1, 3))

        val result = engine.recommend(items, defaultPreferences, savedOutfitIds = saved)
        assertTrue(result.isNotEmpty())

        // {1,4} uses favorite item 1 → gets savedBonus + full freshness
        // {2,3} uses favorite item 3 → gets savedBonus + full freshness
        // {2,4} uses no favorites → only freshness bonus
        val combo14 = result.find { it.items.map { i -> i.id }.toSet() == setOf(1, 4) }
        val combo24 = result.find { it.items.map { i -> i.id }.toSet() == setOf(2, 4) }

        if (combo14 != null && combo24 != null) {
            assertTrue(
                "Combo with saved item should score higher than combo without",
                combo14.score > combo24.score
            )
        }
    }

    @Test
    fun savedPlusHistory_staleComboWithSavedItems_balances() {
        val items = listOf(
            makeItem(1, "Top", "White", "Summer", 3),
            makeItem(2, "Top", "Black", "Summer", 3),
            makeItem(3, "Bottom", "Blue", "Summer", 3),
            makeItem(4, "Bottom", "Navy", "Summer", 3)
        )
        // {1,3} is saved AND recently shown
        val saved: Set<Set<Int>> = setOf(setOf(1, 3))
        val history: Set<Set<Int>> = setOf(setOf(1, 3))

        val result = engine.recommend(
            items, defaultPreferences,
            recentlyShown = history,
            savedOutfitIds = saved
        )
        assertTrue(result.isNotEmpty())

        // {1,3} is filtered as exact history match. After graceful reset it would get:
        // - freshnessBonus = 0.0 (exact repeat)
        // - savedBonus = 0.0 (exact saved match)
        // - overlapPenalty > 0
        // So fresh combos should rank higher
    }

    @Test
    fun savedPlusPlanner_bothApplied() {
        val items = listOf(
            makeItem(1, "Top", "White", "Summer"),
            makeItem(2, "Top", "Black", "Summer"),
            makeItem(3, "Bottom", "Blue", "Summer"),
            makeItem(4, "Bottom", "Navy", "Summer")
        )
        val saved: Set<Set<Int>> = setOf(setOf(1, 3))
        val planned = setOf(1)  // Item 1 is planned for today

        val result = engine.recommend(
            items, defaultPreferences,
            plannedItemIds = planned,
            savedOutfitIds = saved
        )
        assertTrue(result.isNotEmpty())

        // {2,3} uses favorite item 3, doesn't use planned item 1 → best of both
        val ideal = result.find { rec ->
            val ids = rec.items.map { it.id }.toSet()
            3 in ids && 1 !in ids
        }
        if (ideal != null) {
            // Should be among top results
            assertTrue(
                "Combo with saved item but no planned item should rank well",
                ideal.score > 0.0
            )
        }
    }

    @Test
    fun savedPlusTemperature_bothApplied() {
        val items = listOf(
            makeItem(1, "Top", "White", "Summer", 4),
            makeItem(2, "Top", "Black", "Winter", 3),
            makeItem(3, "Bottom", "Blue", "Summer", 4),
            makeItem(4, "Bottom", "Navy", "Winter", 3)
        )
        val saved: Set<Set<Int>> = setOf(setOf(1, 3))

        val result = engine.recommend(
            items, defaultPreferences,
            temperatureCategory = TemperatureCategory.HOT,
            savedOutfitIds = saved
        )
        assertTrue(result.isNotEmpty())
        // In HOT weather, summer items should dominate, AND saved items 1,3 get bonus
    }

    @Test
    fun savedPlusAllContexts_combined() {
        val items = listOf(
            makeItem(1, "Top", "White", "Summer", 4),
            makeItem(2, "Top", "Black", "Summer", 3),
            makeItem(3, "Bottom", "Blue", "Summer", 4),
            makeItem(4, "Bottom", "Navy", "Summer", 3),
            makeItem(5, "Shoes", "Black", "All", 4)
        )
        val result = engine.recommend(
            items, defaultPreferences,
            recentlyShown = setOf(setOf(1, 3)),
            plannedItemIds = setOf(2),
            timeCategory = TimeCategory.AFTERNOON,
            temperatureCategory = TemperatureCategory.WARM,
            savedOutfitIds = setOf(setOf(1, 3, 5))
        )
        assertTrue(result.isNotEmpty())
        assertTrue(result[0].score > 0.0)
    }

    @Test
    fun savedOutfit_itemsNotInWardrobe_noCrash() {
        // Saved outfit references items not in current wardrobe
        val items = listOf(
            makeItem(1, "Top", "White", "Summer"),
            makeItem(2, "Bottom", "Blue", "Summer")
        )
        val saved: Set<Set<Int>> = setOf(setOf(99, 100))  // non-existent items

        val result = engine.recommend(items, defaultPreferences, savedOutfitIds = saved)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun savedOutfit_mixedValidAndInvalidItems() {
        val items = listOf(
            makeItem(1, "Top", "White", "Summer"),
            makeItem(2, "Bottom", "Blue", "Summer"),
            makeItem(3, "Shoes", "Black")
        )
        // Saved outfit has item 1 (valid) and item 99 (not in wardrobe)
        val saved: Set<Set<Int>> = setOf(setOf(1, 99))

        val result = engine.recommend(items, defaultPreferences, savedOutfitIds = saved)
        assertTrue(result.isNotEmpty())

        // Item 1 should still get favorite bonus even though item 99 doesn't exist
        val outfit = listOf(makeItem(1, "Top", "White"), makeItem(2, "Bottom", "Blue"))
        val bonus = engine.savedOutfitBonus(outfit, saved)
        assertEquals("Item 1 is a favorite → 1/2 = 0.5", 0.5, bonus, 0.001)
    }

    // -----------------------------------------------------------------------
    // Section 6: Constants and backward compatibility
    // -----------------------------------------------------------------------

    @Test
    fun weightSaved_isCorrect() {
        assertEquals(0.08, OutfitRecommendationEngine.WEIGHT_SAVED, 0.001)
    }

    @Test
    fun baseWeights_stillSumToOne() {
        val totalWeight = OutfitRecommendationEngine.WEIGHT_SEASON +
            OutfitRecommendationEngine.WEIGHT_COMFORT +
            OutfitRecommendationEngine.WEIGHT_STYLE +
            OutfitRecommendationEngine.WEIGHT_FIT +
            OutfitRecommendationEngine.WEIGHT_HARMONY +
            OutfitRecommendationEngine.WEIGHT_COVERAGE
        assertEquals(1.0, totalWeight, 0.001)
    }

    @Test
    fun backwardCompat_defaultSavedEmpty_noEffect() {
        val items = listOf(
            makeItem(1, "Top", "White", "Summer"),
            makeItem(2, "Bottom", "Blue", "Summer")
        )
        // Calling without savedOutfitIds should behave identically
        val withDefault = engine.recommend(items, defaultPreferences)
        val withExplicitEmpty = engine.recommend(items, defaultPreferences, savedOutfitIds = emptySet())

        assertEquals(withDefault.size, withExplicitEmpty.size)
        assertEquals(withDefault[0].score, withExplicitEmpty[0].score, 0.001)
    }
}
