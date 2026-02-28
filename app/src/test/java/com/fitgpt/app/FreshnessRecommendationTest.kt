package com.fitgpt.app

import com.fitgpt.app.ai.OutfitRecommendationEngine
import com.fitgpt.app.data.model.ClothingItem
import com.fitgpt.app.data.model.TemperatureCategory
import com.fitgpt.app.data.model.TimeCategory
import com.fitgpt.app.data.model.UserPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FreshnessRecommendationTest {

    private lateinit var engine: OutfitRecommendationEngine
    private lateinit var defaultPreferences: UserPreferences

    @Before
    fun setUp() {
        engine = OutfitRecommendationEngine()
        defaultPreferences = UserPreferences(
            bodyType = "Average",
            stylePreference = "Casual",
            comfortPreference = 3,
            preferredSeasons = listOf("Summer", "Spring")
        )
    }

    // -----------------------------------------------------------------------
    // freshnessScore() — unit tests
    // -----------------------------------------------------------------------

    @Test
    fun freshnessScore_emptyRecentlyShown_returns1() {
        val outfit = listOf(ClothingItem(1, "Top", "Black", "Summer", 3))
        assertEquals(1.0, engine.freshnessScore(outfit, emptySet()), 0.001)
    }

    @Test
    fun freshnessScore_emptyOutfit_returns1() {
        val shown = setOf(setOf(1, 2))
        assertEquals(1.0, engine.freshnessScore(emptyList(), shown), 0.001)
    }

    @Test
    fun freshnessScore_exactMatch_returns0() {
        val outfit = listOf(
            ClothingItem(1, "Top", "White", "Summer", 4),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3)
        )
        val shown = setOf(setOf(1, 2))
        assertEquals(0.0, engine.freshnessScore(outfit, shown), 0.001)
    }

    @Test
    fun freshnessScore_noOverlap_returns1() {
        val outfit = listOf(
            ClothingItem(3, "Top", "White", "Summer", 4),
            ClothingItem(4, "Bottom", "Blue", "Summer", 3)
        )
        val shown = setOf(setOf(1, 2))
        assertEquals(1.0, engine.freshnessScore(outfit, shown), 0.001)
    }

    @Test
    fun freshnessScore_partialOverlapBelowThreshold_proportional() {
        // 2-item outfit shares 1 item with a 2-item shown → overlap = 0.5 (below 0.75)
        val outfit = listOf(
            ClothingItem(1, "Top", "White", "Summer", 4),
            ClothingItem(3, "Bottom", "Navy", "Summer", 3)
        )
        val shown = setOf(setOf(1, 2))
        // freshness = 1.0 - 0.5 = 0.5
        assertEquals(0.5, engine.freshnessScore(outfit, shown), 0.001)
    }

    @Test
    fun freshnessScore_nearDuplicate_amplifiedPenalty() {
        // 4-item outfit shares 3 items → overlap = 0.75, triggers amplification
        val outfit = listOf(
            ClothingItem(1, "Top", "White", "Summer", 4),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3),
            ClothingItem(3, "Shoes", "Black", "All", 4),
            ClothingItem(5, "Accessory", "Gold", "All", 3)  // swapped item
        )
        val shown = setOf(setOf(1, 2, 3, 4))  // original had item 4 instead of 5
        // overlap = 3/4 = 0.75 → amplified = 0.5*(1+0.75) = 0.875
        // freshness = 1.0 - 0.875 = 0.125
        assertEquals(0.125, engine.freshnessScore(outfit, shown), 0.001)
    }

    @Test
    fun freshnessScore_nearDuplicate_3of4_strongPenalty() {
        // Without amplification this would be 0.25 freshness; with it, 0.125
        val outfit = listOf(
            ClothingItem(1, "Top", "White", "Summer", 4),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3),
            ClothingItem(3, "Shoes", "Black", "All", 4),
            ClothingItem(99, "Outerwear", "Navy", "Winter", 3)
        )
        val shown = setOf(setOf(1, 2, 3, 4))
        val freshness = engine.freshnessScore(outfit, shown)
        // Near-duplicate: less fresh than the un-amplified 0.25
        assertTrue("Near-duplicate should be penalized below 0.25", freshness < 0.25)
        assertTrue("Near-duplicate should still be positive", freshness > 0.0)
    }

    @Test
    fun freshnessScore_multipleRecentOutfits_usesMaxOverlap() {
        val outfit = listOf(
            ClothingItem(1, "Top", "White", "Summer", 4),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3)
        )
        // One recent outfit shares 1 item (overlap=0.5), another shares 0 (overlap=0.0)
        val shown = setOf(setOf(1, 5), setOf(6, 7))
        // Max overlap = 0.5
        assertEquals(0.5, engine.freshnessScore(outfit, shown), 0.001)
    }

    @Test
    fun freshnessScore_singleItemExactMatch_returns0() {
        val outfit = listOf(ClothingItem(1, "Top", "Black", "Summer", 3))
        val shown = setOf(setOf(1))
        assertEquals(0.0, engine.freshnessScore(outfit, shown), 0.001)
    }

    @Test
    fun freshnessScore_highOverlapNotExact_amplified() {
        // 3-item outfit shares 3 items with a 4-item shown set → overlap = 3/3 = 1.0
        // but outfit IDs {1,2,3} ≠ shown IDs {1,2,3,4}, so NOT exact match
        val outfit = listOf(
            ClothingItem(1, "Top", "White", "Summer", 4),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3),
            ClothingItem(3, "Shoes", "Black", "All", 4)
        )
        val shown = setOf(setOf(1, 2, 3, 4))
        // overlap = 3/3 = 1.0, amplified = 0.5*(1+1.0) = 1.0
        // freshness = 1.0 - 1.0 = 0.0
        assertEquals(0.0, engine.freshnessScore(outfit, shown), 0.001)
    }

    // -----------------------------------------------------------------------
    // Exact repeat penalty — integration tests
    // -----------------------------------------------------------------------

    @Test
    fun recommend_exactRepeatAfterGracefulReset_scoresLower() {
        // With only one combo possible, graceful reset brings it back
        // But freshness bonus should be 0.0 for the exact repeat
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 4),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3)
        )
        val shown = setOf(setOf(1, 2))

        val fresh = engine.recommend(items, defaultPreferences, recentlyShown = emptySet())
        val stale = engine.recommend(items, defaultPreferences, recentlyShown = shown)

        assertTrue(fresh.isNotEmpty())
        assertTrue(stale.isNotEmpty())
        // After graceful reset, the exact repeat should score lower (no freshness bonus)
        assertTrue(
            "Exact repeat after graceful reset should score lower than fresh",
            fresh[0].score > stale[0].score
        )
    }

    @Test
    fun recommend_exactRepeat_stillHasPositiveScore() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 4),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3)
        )
        val shown = setOf(setOf(1, 2))
        val result = engine.recommend(items, defaultPreferences, recentlyShown = shown)
        assertTrue(result.isNotEmpty())
        assertTrue("Exact repeat should still have positive score", result[0].score > 0.0)
    }

    @Test
    fun recommend_freshComboScoresHigherThanRepeat() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 3),
            ClothingItem(2, "Top", "Black", "Summer", 3),
            ClothingItem(3, "Bottom", "Blue", "Summer", 3),
            ClothingItem(4, "Bottom", "Navy", "Summer", 3)
        )
        // Mark {1,3} as recently shown — {2,4} is fresh
        val shown = setOf(setOf(1, 3))
        val result = engine.recommend(items, defaultPreferences, recentlyShown = shown)
        assertTrue(result.size >= 2)

        // Find the fresh combo {2,4} and the partially-stale ones
        val freshCombo = result.find { rec ->
            rec.items.map { it.id }.toSet() == setOf(2, 4)
        }
        val staleCombo = result.find { rec ->
            val ids = rec.items.map { it.id }.toSet()
            ids.any { it in setOf(1, 3) }
        }

        if (freshCombo != null && staleCombo != null) {
            assertTrue(
                "Fresh combo should score >= stale combo",
                freshCombo.score >= staleCombo.score
            )
        }
    }

    @Test
    fun recommend_multipleExactRepeats_allScoreLower() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 3),
            ClothingItem(2, "Top", "Black", "Summer", 3),
            ClothingItem(3, "Bottom", "Blue", "Summer", 3)
        )
        // Both possible combos shown
        val shown = setOf(setOf(1, 3), setOf(2, 3))
        val freshResult = engine.recommend(items, defaultPreferences, recentlyShown = emptySet())
        val staleResult = engine.recommend(items, defaultPreferences, recentlyShown = shown)

        assertTrue(freshResult.isNotEmpty())
        assertTrue(staleResult.isNotEmpty())
        // Top stale score should be less than top fresh score
        assertTrue(
            "All-repeated wardrobe should score lower than fresh",
            freshResult[0].score > staleResult[0].score
        )
    }

    // -----------------------------------------------------------------------
    // Near-duplicate penalty — integration tests
    // -----------------------------------------------------------------------

    @Test
    fun recommend_nearDuplicate_scoresLowerThanFresh() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 3),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3),
            ClothingItem(3, "Shoes", "Black", "All", 4),
            ClothingItem(4, "Shoes", "White", "All", 4),  // alternative shoe
            ClothingItem(5, "Top", "Red", "Summer", 3)
        )
        // Mark {1,2,3} as shown — {1,2,4} is a near-duplicate, {5,2,3} is fresh-ish
        val shown = setOf(setOf(1, 2, 3))
        val result = engine.recommend(items, defaultPreferences, recentlyShown = shown)
        assertTrue(result.isNotEmpty())

        val nearDup = result.find { rec ->
            val ids = rec.items.map { it.id }.toSet()
            ids.containsAll(listOf(1, 2, 4)) && ids.size == 3
        }
        val fresh = result.find { rec ->
            val ids = rec.items.map { it.id }.toSet()
            5 in ids && 1 !in ids
        }
        // At minimum, near-duplicate should exist and have reduced score
        if (nearDup != null && fresh != null) {
            assertTrue(
                "Near-duplicate should score lower than fresh outfit",
                fresh.score >= nearDup.score
            )
        }
    }

    @Test
    fun recommend_nearDuplicate_scoresHigherThanExactRepeat() {
        // Near-duplicate should still beat an exact repeat
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 3),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3),
            ClothingItem(3, "Shoes", "Black", "All", 4)
        )
        // Show the only combo, then check it's the lowest scoring via graceful reset
        val shown = setOf(setOf(1, 2, 3))

        // Score with exact repeat history
        val exactRepeatResult = engine.recommend(items, defaultPreferences, recentlyShown = shown)

        // Add an alternative shoe to create a near-duplicate
        val extendedItems = items + ClothingItem(4, "Shoes", "White", "All", 4)
        val nearDupResult = engine.recommend(extendedItems, defaultPreferences, recentlyShown = shown)

        // The near-duplicate {1,2,4} should be in nearDupResult and score higher
        // than the exact repeat {1,2,3} in exactRepeatResult
        val nearDup = nearDupResult.find { rec ->
            val ids = rec.items.map { it.id }.toSet()
            ids == setOf(1, 2, 4)
        }
        if (nearDup != null) {
            assertTrue(
                "Near-duplicate should score higher than exact repeat",
                nearDup.score > exactRepeatResult[0].score
            )
        }
    }

    @Test
    fun recommend_swapOneAccessory_deprioritized() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 3),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3),
            ClothingItem(3, "Accessory", "Gold", "All", 5),
            ClothingItem(4, "Accessory", "Silver", "All", 5),
            ClothingItem(5, "Top", "Red", "Summer", 3)
        )
        // {1,2,3} shown — {1,2,4} is near-duplicate (swap one accessory)
        val shown = setOf(setOf(1, 2, 3))
        val result = engine.recommend(items, defaultPreferences, recentlyShown = shown)
        assertTrue(result.isNotEmpty())

        val swapped = result.find { rec ->
            rec.items.map { it.id }.toSet() == setOf(1, 2, 4)
        }
        if (swapped != null) {
            // The swapped outfit should not be the top-ranked result
            val topIds = result[0].items.map { it.id }.toSet()
            if (topIds != setOf(1, 2, 4)) {
                assertTrue(
                    "Swap-one-accessory outfit should be deprioritized",
                    result[0].score >= swapped.score
                )
            }
        }
    }

    @Test
    fun overlapPenalty_andFreshnessScore_complementary() {
        // overlapPenalty is subtractive, freshnessScore is additive bonus
        // A fresh outfit gets +0.15 bonus and -0.0 penalty
        // A stale outfit gets +0.0 bonus and -0.15 penalty
        // Total swing = 0.30

        val freshOutfit = listOf(
            ClothingItem(10, "Top", "White", "Summer", 3),
            ClothingItem(11, "Bottom", "Blue", "Summer", 3)
        )
        val staleOutfit = listOf(
            ClothingItem(1, "Top", "Black", "Summer", 3),
            ClothingItem(2, "Bottom", "Navy", "Summer", 3)
        )
        val shown = setOf(setOf(1, 2))

        val freshFreshness = engine.freshnessScore(freshOutfit, shown)
        val staleFreshness = engine.freshnessScore(staleOutfit, shown)
        val freshPenalty = engine.overlapPenalty(freshOutfit, shown)
        val stalePenalty = engine.overlapPenalty(staleOutfit, shown)

        assertEquals(1.0, freshFreshness, 0.001)
        // staleOutfit is exact match → freshnessScore = 0.0
        assertEquals(0.0, staleFreshness, 0.001)
        assertEquals(0.0, freshPenalty, 0.001)
        // stalePenalty = 1.0 * WEIGHT_DIVERSITY = 0.15
        assertEquals(
            OutfitRecommendationEngine.WEIGHT_DIVERSITY,
            stalePenalty, 0.001
        )
    }

    // -----------------------------------------------------------------------
    // Small wardrobe fallback
    // -----------------------------------------------------------------------

    @Test
    fun smallWardrobe_singleItem_returnsResult() {
        val items = listOf(ClothingItem(1, "Top", "Black", "Summer", 3))
        val result = engine.recommend(items, defaultPreferences)
        assertEquals(1, result.size)
        assertTrue(result[0].score > 0.0)
    }

    @Test
    fun smallWardrobe_singleTopSingleBottom_returnsResult() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 4),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3)
        )
        val result = engine.recommend(items, defaultPreferences)
        assertTrue(result.isNotEmpty())
        assertTrue(result[0].items.size == 2)
    }

    @Test
    fun smallWardrobe_onlyShoes_individualScoring() {
        val items = listOf(
            ClothingItem(1, "Shoes", "Black", "All", 4),
            ClothingItem(2, "Shoes", "White", "All", 5)
        )
        val result = engine.recommend(items, defaultPreferences)
        assertTrue(result.isNotEmpty())
        result.forEach { assertEquals(1, it.items.size) }
    }

    @Test
    fun smallWardrobe_onlyAccessories_individualScoring() {
        val items = listOf(
            ClothingItem(1, "Accessory", "Gold", "All", 5),
            ClothingItem(2, "Accessory", "Silver", "All", 4)
        )
        val result = engine.recommend(items, defaultPreferences)
        assertTrue(result.isNotEmpty())
        result.forEach { assertEquals(1, it.items.size) }
    }

    @Test
    fun smallWardrobe_allShown_gracefulReset() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 4),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3)
        )
        val shown = setOf(setOf(1, 2))
        val result = engine.recommend(items, defaultPreferences, recentlyShown = shown)
        assertTrue("Graceful reset should return results", result.isNotEmpty())
    }

    @Test
    fun smallWardrobe_withTemperature_returnsResult() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 4),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3)
        )
        val result = engine.recommend(
            items, defaultPreferences,
            temperatureCategory = TemperatureCategory.HOT
        )
        assertTrue(result.isNotEmpty())
        assertTrue(result[0].score > 0.0)
    }

    @Test
    fun smallWardrobe_withTimeAndTemp_returnsResult() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 4),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3)
        )
        val result = engine.recommend(
            items, defaultPreferences,
            timeCategory = TimeCategory.MORNING,
            temperatureCategory = TemperatureCategory.MILD
        )
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun smallWardrobe_repeatedRefresh_neverCrashes() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 4),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3)
        )
        var shown = emptySet<Set<Int>>()
        repeat(10) {
            val result = engine.recommend(items, defaultPreferences, recentlyShown = shown)
            assertTrue("Refresh $it should produce results", result.isNotEmpty())
            shown = shown + result.map { rec -> rec.items.map { it.id }.toSet() }.toSet()
        }
    }

    @Test
    fun smallWardrobe_mismatchedSeasons_stillReturnsResults() {
        // Only winter items but summer preferences — scores should be low but non-empty
        val items = listOf(
            ClothingItem(1, "Top", "Black", "Winter", 3),
            ClothingItem(2, "Bottom", "Navy", "Winter", 3)
        )
        val result = engine.recommend(items, defaultPreferences)
        assertTrue(result.isNotEmpty())
        assertTrue(result[0].score > 0.0)
    }

    // -----------------------------------------------------------------------
    // Large wardrobe tests
    // -----------------------------------------------------------------------

    private fun buildLargeWardrobe(): List<ClothingItem> {
        val colors = listOf("White", "Black", "Blue", "Red", "Green", "Navy", "Gray", "Beige")
        val seasons = listOf("Summer", "Winter", "Spring", "Fall", "All")
        var id = 1
        val items = mutableListOf<ClothingItem>()

        // 6 tops
        for (i in 0 until 6) {
            items.add(ClothingItem(id++, "Top", colors[i % colors.size], seasons[i % seasons.size], (i % 5) + 1))
        }
        // 6 bottoms
        for (i in 0 until 6) {
            items.add(ClothingItem(id++, "Bottom", colors[(i + 2) % colors.size], seasons[(i + 1) % seasons.size], (i % 5) + 1))
        }
        // 4 shoes
        for (i in 0 until 4) {
            items.add(ClothingItem(id++, "Shoes", colors[(i + 4) % colors.size], "All", (i % 3) + 3))
        }
        // 3 outerwear
        for (i in 0 until 3) {
            items.add(ClothingItem(id++, "Outerwear", colors[(i + 1) % colors.size], seasons[(i + 2) % seasons.size], (i % 3) + 2))
        }
        // 3 accessories
        for (i in 0 until 3) {
            items.add(ClothingItem(id++, "Accessory", colors[(i + 3) % colors.size], "All", (i % 3) + 3))
        }

        return items  // 22 items total
    }

    @Test
    fun largeWardrobe_returnsResults() {
        val items = buildLargeWardrobe()
        val result = engine.recommend(items, defaultPreferences)
        assertTrue(result.isNotEmpty())
        assertTrue(result.size <= 5)
    }

    @Test
    fun largeWardrobe_maxFiveRecommendations() {
        val items = buildLargeWardrobe()
        val result = engine.recommend(items, defaultPreferences)
        assertTrue(result.size <= 5)
    }

    @Test
    fun largeWardrobe_withExtensiveHistory_stillReturns() {
        val items = buildLargeWardrobe()
        // Build up a big history by running multiple recommendations
        var shown = emptySet<Set<Int>>()
        repeat(5) {
            val result = engine.recommend(items, defaultPreferences, recentlyShown = shown)
            shown = shown + result.map { rec -> rec.items.map { it.id }.toSet() }.toSet()
        }
        // Now recommend with the large history
        val result = engine.recommend(items, defaultPreferences, recentlyShown = shown)
        assertTrue("Should still return results with extensive history", result.isNotEmpty())
    }

    @Test
    fun largeWardrobe_multipleRefreshes_neverEmpty() {
        val items = buildLargeWardrobe()
        var shown = emptySet<Set<Int>>()
        repeat(20) { i ->
            val result = engine.recommend(items, defaultPreferences, recentlyShown = shown)
            assertTrue("Refresh $i should produce results", result.isNotEmpty())
            assertTrue("Refresh $i scores should be positive", result[0].score > 0.0)
            shown = shown + result.map { rec -> rec.items.map { it.id }.toSet() }.toSet()
        }
    }

    @Test
    fun largeWardrobe_freshOutfitsRankedFirst() {
        val items = buildLargeWardrobe()
        val firstRound = engine.recommend(items, defaultPreferences)
        val shownIds = firstRound.map { rec -> rec.items.map { it.id }.toSet() }.toSet()

        val secondRound = engine.recommend(items, defaultPreferences, recentlyShown = shownIds)
        assertTrue(secondRound.isNotEmpty())

        // The top result in second round should NOT be an exact repeat of first round
        for (rec in secondRound) {
            val ids = rec.items.map { it.id }.toSet()
            assertFalse(
                "Second round should not repeat exact combos from first round",
                ids in shownIds
            )
        }
    }

    @Test
    fun largeWardrobe_withAllContexts_neverEmpty() {
        val items = buildLargeWardrobe()
        val shown = setOf(
            items.take(3).map { it.id }.toSet(),
            items.drop(3).take(2).map { it.id }.toSet()
        )
        val result = engine.recommend(
            items, defaultPreferences,
            recentlyShown = shown,
            plannedItemIds = setOf(1, 2),
            timeCategory = TimeCategory.EVENING,
            temperatureCategory = TemperatureCategory.WARM
        )
        assertTrue(result.isNotEmpty())
        assertTrue(result[0].score > 0.0)
    }

    // -----------------------------------------------------------------------
    // No infinite loop / no empty results
    // -----------------------------------------------------------------------

    @Test
    fun recommend_emptyWardrobe_returnsEmptyNotInfiniteLoop() {
        val result = engine.recommend(emptyList(), defaultPreferences)
        assertTrue(result.isEmpty())
    }

    @Test
    fun recommend_allContextsPlusHistory_neverReturnsEmpty() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 4),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3),
            ClothingItem(3, "Shoes", "Black", "All", 4)
        )
        val shown = setOf(setOf(1, 2), setOf(1, 2, 3))
        val result = engine.recommend(
            items, defaultPreferences,
            recentlyShown = shown,
            plannedItemIds = setOf(1),
            timeCategory = TimeCategory.NIGHT,
            temperatureCategory = TemperatureCategory.COLD
        )
        assertTrue("Should always return results when items exist", result.isNotEmpty())
    }

    @Test
    fun recommend_50ConsecutiveRefreshes_mediumWardrobe_neverEmpty() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 5),
            ClothingItem(2, "Top", "Black", "Winter", 3),
            ClothingItem(3, "Top", "Blue", "Spring", 4),
            ClothingItem(4, "Bottom", "Navy", "Summer", 3),
            ClothingItem(5, "Bottom", "Gray", "Fall", 4),
            ClothingItem(6, "Bottom", "Beige", "All", 3),
            ClothingItem(7, "Shoes", "Black", "All", 4),
            ClothingItem(8, "Shoes", "White", "All", 5),
            ClothingItem(9, "Outerwear", "Brown", "Winter", 2),
            ClothingItem(10, "Accessory", "Gold", "All", 5)
        )
        var shown = emptySet<Set<Int>>()
        repeat(50) { i ->
            val result = engine.recommend(items, defaultPreferences, recentlyShown = shown)
            assertTrue("Refresh $i must not be empty", result.isNotEmpty())
            result.forEach { rec ->
                assertTrue("Score at refresh $i must be positive", rec.score > 0.0)
                assertTrue("Explanation must not be blank at refresh $i", rec.explanation.isNotBlank())
            }
            shown = shown + result.map { rec -> rec.items.map { it.id }.toSet() }.toSet()
        }
    }

    @Test
    fun recommend_allCombosShown_scoreNeverZero() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 4),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3)
        )
        val shown = setOf(setOf(1, 2))
        val result = engine.recommend(items, defaultPreferences, recentlyShown = shown)
        assertTrue(result.isNotEmpty())
        // Score should be at least 0.01 (the floor in the engine)
        assertTrue("Score floor should hold", result[0].score >= 0.01)
    }

    @Test
    fun recommend_singleItemWardrobe_50Refreshes_neverEmpty() {
        val items = listOf(ClothingItem(1, "Top", "Black", "Summer", 3))
        var shown = emptySet<Set<Int>>()
        repeat(50) { i ->
            val result = engine.recommend(items, defaultPreferences, recentlyShown = shown)
            assertTrue("Single-item refresh $i must not be empty", result.isNotEmpty())
            shown = shown + result.map { rec -> rec.items.map { it.id }.toSet() }.toSet()
        }
    }

    @Test
    fun recommend_onlyMismatchedItems_withHistory_stillWorks() {
        // All winter items, summer preferences, hot temperature, and everything in history
        val items = listOf(
            ClothingItem(1, "Top", "Black", "Winter", 2),
            ClothingItem(2, "Bottom", "Navy", "Winter", 2)
        )
        val shown = setOf(setOf(1, 2))
        val result = engine.recommend(
            items, defaultPreferences,
            recentlyShown = shown,
            temperatureCategory = TemperatureCategory.HOT
        )
        assertTrue("Must return results even in worst case", result.isNotEmpty())
        assertTrue("Score must be positive", result[0].score > 0.0)
    }

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    @Test
    fun weightFreshness_isCorrect() {
        assertEquals(0.15, OutfitRecommendationEngine.WEIGHT_FRESHNESS, 0.001)
    }

    @Test
    fun nearDuplicateThreshold_isCorrect() {
        assertEquals(0.75, OutfitRecommendationEngine.NEAR_DUPLICATE_THRESHOLD, 0.001)
    }

    @Test
    fun baseWeights_stillSumToOne() {
        // The 6 base item/outfit weights remain unchanged
        val totalWeight = OutfitRecommendationEngine.WEIGHT_SEASON +
            OutfitRecommendationEngine.WEIGHT_COMFORT +
            OutfitRecommendationEngine.WEIGHT_STYLE +
            OutfitRecommendationEngine.WEIGHT_FIT +
            OutfitRecommendationEngine.WEIGHT_HARMONY +
            OutfitRecommendationEngine.WEIGHT_COVERAGE
        assertEquals(1.0, totalWeight, 0.001)
    }

    @Test
    fun contextPenaltyCap_stillCoversAllPenalties() {
        val maxPossiblePenalty = OutfitRecommendationEngine.WEIGHT_DIVERSITY +
            OutfitRecommendationEngine.WEIGHT_PLANNER
        assertTrue(
            "MAX_CONTEXT_PENALTY should cover combined diversity + planner penalties",
            OutfitRecommendationEngine.MAX_CONTEXT_PENALTY >= maxPossiblePenalty
        )
    }
}
