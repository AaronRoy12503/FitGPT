package com.fitgpt.app

import com.fitgpt.app.ai.OutfitRecommendationEngine
import com.fitgpt.app.data.model.ClothingItem
import com.fitgpt.app.data.model.TemperatureCategory
import com.fitgpt.app.data.model.TimeCategory
import com.fitgpt.app.data.model.UserPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Validates fit compatibility weighting in the recommendation engine:
 * - fitCompatibility matrix across body types and fits
 * - outfitFitScore outfit-level aggregation
 * - fitMismatchPenalty for strongly mismatched fits
 * - Engine integration and scoring formula
 * - Multi-body-type ranking differentiation
 * - Default fit safety (Regular never breaks flow)
 * - Performance under load
 * - Explanation text for high/low compatibility
 */
@RunWith(JUnit4::class)
class FitCompatibilityRecommendationTest {

    private lateinit var engine: OutfitRecommendationEngine

    @Before
    fun setUp() {
        engine = OutfitRecommendationEngine()
    }

    private fun item(
        id: Int,
        category: String = "Top",
        color: String = "Black",
        season: String = "All",
        comfortLevel: Int = 3,
        fit: String = "Regular"
    ) = ClothingItem(id, category, color, season, comfortLevel, fit)

    private fun prefs(bodyType: String = "Average") = UserPreferences(
        bodyType = bodyType,
        stylePreference = "Casual",
        comfortPreference = 3,
        preferredSeasons = listOf("Spring", "Summer", "Fall", "Winter")
    )

    // ================================================================
    // fitCompatibility — slim
    // ================================================================

    @Test
    fun fitCompat_slim_fitted() {
        assertEquals(1.0, engine.fitCompatibility("Fitted", "Slim"), 0.001)
    }

    @Test
    fun fitCompat_slim_regular() {
        assertEquals(0.7, engine.fitCompatibility("Regular", "Slim"), 0.001)
    }

    @Test
    fun fitCompat_slim_oversized() {
        assertEquals(0.6, engine.fitCompatibility("Oversized", "Slim"), 0.001)
    }

    @Test
    fun fitCompat_slim_relaxed() {
        assertEquals(0.5, engine.fitCompatibility("Relaxed", "Slim"), 0.001)
    }

    // ================================================================
    // fitCompatibility — athletic
    // ================================================================

    @Test
    fun fitCompat_athletic_fitted() {
        assertEquals(1.0, engine.fitCompatibility("Fitted", "Athletic"), 0.001)
    }

    @Test
    fun fitCompat_athletic_regular() {
        assertEquals(0.8, engine.fitCompatibility("Regular", "Athletic"), 0.001)
    }

    @Test
    fun fitCompat_athletic_relaxed() {
        assertEquals(0.5, engine.fitCompatibility("Relaxed", "Athletic"), 0.001)
    }

    @Test
    fun fitCompat_athletic_oversized() {
        assertEquals(0.3, engine.fitCompatibility("Oversized", "Athletic"), 0.001)
    }

    // ================================================================
    // fitCompatibility — plus-size
    // ================================================================

    @Test
    fun fitCompat_plusSize_relaxed() {
        assertEquals(1.0, engine.fitCompatibility("Relaxed", "Plus-size"), 0.001)
    }

    @Test
    fun fitCompat_plusSize_oversized() {
        assertEquals(0.8, engine.fitCompatibility("Oversized", "Plus-size"), 0.001)
    }

    @Test
    fun fitCompat_plusSize_regular() {
        assertEquals(0.7, engine.fitCompatibility("Regular", "Plus-size"), 0.001)
    }

    @Test
    fun fitCompat_plusSize_fitted() {
        assertEquals(0.3, engine.fitCompatibility("Fitted", "Plus-size"), 0.001)
    }

    // ================================================================
    // fitCompatibility — average
    // ================================================================

    @Test
    fun fitCompat_average_regular() {
        assertEquals(0.8, engine.fitCompatibility("Regular", "Average"), 0.001)
    }

    @Test
    fun fitCompat_average_fitted() {
        assertEquals(0.7, engine.fitCompatibility("Fitted", "Average"), 0.001)
    }

    @Test
    fun fitCompat_average_relaxed() {
        assertEquals(0.7, engine.fitCompatibility("Relaxed", "Average"), 0.001)
    }

    @Test
    fun fitCompat_average_oversized() {
        assertEquals(0.6, engine.fitCompatibility("Oversized", "Average"), 0.001)
    }

    // ================================================================
    // fitCompatibility — edge cases
    // ================================================================

    @Test
    fun fitCompat_unknownBodyType_returnsNeutral() {
        assertEquals(0.7, engine.fitCompatibility("Fitted", "Petite"), 0.001)
        assertEquals(0.7, engine.fitCompatibility("Regular", "Petite"), 0.001)
        assertEquals(0.7, engine.fitCompatibility("Oversized", "Petite"), 0.001)
        assertEquals(0.7, engine.fitCompatibility("Relaxed", "Petite"), 0.001)
    }

    @Test
    fun fitCompat_unknownFit_returnsNeutral() {
        assertEquals(0.7, engine.fitCompatibility("Slim-fit", "Athletic"), 0.001)
        assertEquals(0.7, engine.fitCompatibility("Tailored", "Slim"), 0.001)
        assertEquals(0.7, engine.fitCompatibility("Custom", "Plus-size"), 0.001)
    }

    @Test
    fun fitCompat_caseInsensitive() {
        assertEquals(1.0, engine.fitCompatibility("FITTED", "SLIM"), 0.001)
        assertEquals(0.3, engine.fitCompatibility("fitted", "plus-size"), 0.001)
        assertEquals(1.0, engine.fitCompatibility("Relaxed", "PLUS-SIZE"), 0.001)
        assertEquals(0.8, engine.fitCompatibility("REGULAR", "athletic"), 0.001)
    }

    // ================================================================
    // outfitFitScore
    // ================================================================

    @Test
    fun outfitFitScore_emptyOutfit_returnsZero() {
        assertEquals(0.0, engine.outfitFitScore(emptyList(), prefs()), 0.001)
    }

    @Test
    fun outfitFitScore_singleItem() {
        val outfit = listOf(item(1, fit = "Fitted"))
        assertEquals(1.0, engine.outfitFitScore(outfit, prefs("Athletic")), 0.001)
    }

    @Test
    fun outfitFitScore_allSameFit_perfectMatch() {
        val outfit = listOf(
            item(1, "Top", fit = "Fitted"),
            item(2, "Bottom", fit = "Fitted"),
            item(3, "Shoes", fit = "Fitted")
        )
        assertEquals(1.0, engine.outfitFitScore(outfit, prefs("Athletic")), 0.001)
    }

    @Test
    fun outfitFitScore_mixedFits_averageCompat() {
        val outfit = listOf(
            item(1, "Top", fit = "Fitted"),     // 1.0 for athletic
            item(2, "Bottom", fit = "Regular"),  // 0.8 for athletic
            item(3, "Shoes", fit = "Oversized")  // 0.3 for athletic
        )
        assertEquals(0.7, engine.outfitFitScore(outfit, prefs("Athletic")), 0.001)
    }

    @Test
    fun outfitFitScore_defaultRegular_averageBody() {
        val outfit = listOf(
            item(1, "Top"),       // Regular = 0.8 for Average
            item(2, "Bottom"),    // Regular = 0.8
            item(3, "Shoes")     // Regular = 0.8
        )
        assertEquals(0.8, engine.outfitFitScore(outfit, prefs("Average")), 0.001)
    }

    @Test
    fun outfitFitScore_allMismatched() {
        val outfit = listOf(
            item(1, "Top", fit = "Fitted"),     // 0.3 for plus-size
            item(2, "Bottom", fit = "Fitted"),  // 0.3
            item(3, "Shoes", fit = "Fitted")    // 0.3
        )
        assertEquals(0.3, engine.outfitFitScore(outfit, prefs("Plus-size")), 0.001)
    }

    // ================================================================
    // fitMismatchPenalty
    // ================================================================

    @Test
    fun fitMismatchPenalty_emptyOutfit_returnsZero() {
        assertEquals(0.0, engine.fitMismatchPenalty(emptyList(), prefs()), 0.001)
    }

    @Test
    fun fitMismatchPenalty_noMismatches_returnsZero() {
        val outfit = listOf(
            item(1, "Top", fit = "Fitted"),
            item(2, "Bottom", fit = "Regular")
        )
        // Athletic: fitted=1.0, regular=0.8 — both well above threshold
        assertEquals(0.0, engine.fitMismatchPenalty(outfit, prefs("Athletic")), 0.001)
    }

    @Test
    fun fitMismatchPenalty_allMismatched_fullPenalty() {
        val outfit = listOf(
            item(1, "Top", fit = "Fitted"),     // 0.3 for plus-size
            item(2, "Bottom", fit = "Fitted"),  // 0.3
            item(3, "Shoes", fit = "Fitted")    // 0.3
        )
        // All below 0.4 → mismatch ratio = 1.0
        val expected = 1.0 * OutfitRecommendationEngine.WEIGHT_FIT_PENALTY
        assertEquals(expected, engine.fitMismatchPenalty(outfit, prefs("Plus-size")), 0.001)
    }

    @Test
    fun fitMismatchPenalty_partialMismatch_proportional() {
        val outfit = listOf(
            item(1, "Top", fit = "Oversized"),   // 0.3 for athletic — mismatched
            item(2, "Bottom", fit = "Fitted"),   // 1.0 for athletic — OK
            item(3, "Shoes", fit = "Regular")    // 0.8 for athletic — OK
        )
        val expected = (1.0 / 3.0) * OutfitRecommendationEngine.WEIGHT_FIT_PENALTY
        assertEquals(expected, engine.fitMismatchPenalty(outfit, prefs("Athletic")), 0.001)
    }

    @Test
    fun fitMismatchPenalty_defaultRegular_neverMismatched() {
        val outfit = listOf(item(1, "Top"), item(2, "Bottom"), item(3, "Shoes"))
        for (bodyType in listOf("Slim", "Athletic", "Plus-size", "Average")) {
            assertEquals(
                "Regular fit should not be penalized for $bodyType",
                0.0,
                engine.fitMismatchPenalty(outfit, prefs(bodyType)),
                0.001
            )
        }
    }

    // ================================================================
    // Engine integration — scoring formula
    // ================================================================

    @Test
    fun integration_wellMatchedScoresHigherThanMismatched_athletic() {
        val fittedItems = listOf(
            item(1, "Top", "Blue", fit = "Fitted"),
            item(2, "Bottom", "Black", fit = "Fitted"),
            item(3, "Shoes", "White", fit = "Regular")
        )
        val oversizedItems = listOf(
            item(4, "Top", "Blue", fit = "Oversized"),
            item(5, "Bottom", "Black", fit = "Oversized"),
            item(6, "Shoes", "White", fit = "Relaxed")
        )
        val recs = engine.recommend(
            items = fittedItems + oversizedItems,
            preferences = prefs("Athletic")
        )
        val topIds = recs.first().items.map { it.id }.toSet()
        assertTrue(
            "Athletic body should prefer fitted outfit, got $topIds",
            topIds.intersect(setOf(1, 2, 3)).size >= 2
        )
    }

    @Test
    fun integration_plusSize_prefersRelaxed() {
        val relaxedItems = listOf(
            item(1, "Top", "Blue", fit = "Relaxed"),
            item(2, "Bottom", "Black", fit = "Relaxed"),
            item(3, "Shoes", "White", fit = "Regular")
        )
        val fittedItems = listOf(
            item(4, "Top", "Blue", fit = "Fitted"),
            item(5, "Bottom", "Black", fit = "Fitted"),
            item(6, "Shoes", "White", fit = "Fitted")
        )
        val recs = engine.recommend(
            items = relaxedItems + fittedItems,
            preferences = prefs("Plus-size")
        )
        val topIds = recs.first().items.map { it.id }.toSet()
        assertTrue(
            "Plus-size body should prefer relaxed outfit, got $topIds",
            topIds.intersect(setOf(1, 2, 3)).size >= 2
        )
    }

    @Test
    fun integration_slim_prefersFitted() {
        val fittedItems = listOf(
            item(1, "Top", "Blue", fit = "Fitted"),
            item(2, "Bottom", "Black", fit = "Fitted"),
            item(3, "Shoes", "White", fit = "Regular")
        )
        val relaxedItems = listOf(
            item(4, "Top", "Blue", fit = "Relaxed"),
            item(5, "Bottom", "Black", fit = "Relaxed"),
            item(6, "Shoes", "White", fit = "Relaxed")
        )
        val recs = engine.recommend(
            items = fittedItems + relaxedItems,
            preferences = prefs("Slim")
        )
        val topIds = recs.first().items.map { it.id }.toSet()
        assertTrue(
            "Slim body should prefer fitted outfit, got $topIds",
            topIds.intersect(setOf(1, 2, 3)).size >= 2
        )
    }

    @Test
    fun integration_defaultFit_producesResults() {
        val items = listOf(
            item(1, "Top"),
            item(2, "Bottom"),
            item(3, "Shoes")
        )
        val recs = engine.recommend(items = items, preferences = prefs())
        assertTrue("Should produce at least one recommendation", recs.isNotEmpty())
        assertTrue("Scores should be positive", recs.all { it.score > 0 })
    }

    @Test
    fun integration_fitCompatBonus_reflectedInScore() {
        val goodFitTop = item(1, "Top", "Blue", fit = "Fitted")
        val badFitTop = item(2, "Top", "Blue", fit = "Oversized")
        val bottom = item(3, "Bottom", "Black", fit = "Regular")

        val p = prefs("Athletic")
        val goodRecs = engine.recommend(items = listOf(goodFitTop, bottom), preferences = p)
        val badRecs = engine.recommend(items = listOf(badFitTop, bottom), preferences = p)

        assertTrue(
            "Well-fitted outfit should score higher",
            goodRecs.first().score > badRecs.first().score
        )
    }

    @Test
    fun integration_mismatchPenalty_lowersTotalScore() {
        val allFitted = listOf(
            item(1, "Top", "Blue", fit = "Fitted"),
            item(2, "Bottom", "Black", fit = "Fitted"),
            item(3, "Shoes", "White", fit = "Fitted")
        )
        val allRelaxed = listOf(
            item(4, "Top", "Blue", fit = "Relaxed"),
            item(5, "Bottom", "Black", fit = "Relaxed"),
            item(6, "Shoes", "White", fit = "Relaxed")
        )

        val p = prefs("Plus-size")
        val fittedRecs = engine.recommend(items = allFitted, preferences = p)
        val relaxedRecs = engine.recommend(items = allRelaxed, preferences = p)

        assertTrue(
            "Mismatched (fitted on plus-size) should score lower than relaxed",
            fittedRecs.first().score < relaxedRecs.first().score
        )
    }

    // ================================================================
    // Multi-body-type — same wardrobe, different rankings
    // ================================================================

    @Test
    fun multiBody_sameWardrobe_differentTopPick() {
        val items = listOf(
            item(1, "Top", "Blue", fit = "Fitted"),
            item(2, "Top", "Blue", fit = "Relaxed"),
            item(3, "Bottom", "Black", fit = "Fitted"),
            item(4, "Bottom", "Black", fit = "Relaxed"),
            item(5, "Shoes", "White", fit = "Regular")
        )
        val athleticRecs = engine.recommend(items = items, preferences = prefs("Athletic"))
        val plusRecs = engine.recommend(items = items, preferences = prefs("Plus-size"))

        val athleticTopIds = athleticRecs.first().items.map { it.id }.toSet()
        val plusTopIds = plusRecs.first().items.map { it.id }.toSet()

        assertNotEquals(
            "Athletic and Plus-size should rank differently",
            athleticTopIds,
            plusTopIds
        )
    }

    @Test
    fun multiBody_athletic_ranksFittedHigher() {
        val items = listOf(
            item(1, "Top", "Blue", fit = "Fitted"),
            item(2, "Top", "Blue", fit = "Oversized"),
            item(3, "Bottom", "Black", fit = "Regular"),
            item(4, "Shoes", "White", fit = "Regular")
        )
        val recs = engine.recommend(items = items, preferences = prefs("Athletic"))
        assertTrue(
            "Athletic should prefer fitted top",
            recs.first().items.any { it.id == 1 }
        )
    }

    @Test
    fun multiBody_plusSize_ranksRelaxedHigher() {
        val items = listOf(
            item(1, "Top", "Blue", fit = "Relaxed"),
            item(2, "Top", "Blue", fit = "Fitted"),
            item(3, "Bottom", "Black", fit = "Regular"),
            item(4, "Shoes", "White", fit = "Regular")
        )
        val recs = engine.recommend(items = items, preferences = prefs("Plus-size"))
        assertTrue(
            "Plus-size should prefer relaxed top",
            recs.first().items.any { it.id == 1 }
        )
    }

    @Test
    fun multiBody_unknownBodyType_neutralScoring() {
        val items = listOf(
            item(1, "Top", "Blue", fit = "Fitted"),
            item(2, "Top", "Blue", fit = "Relaxed"),
            item(3, "Bottom", "Black", fit = "Regular"),
            item(4, "Shoes", "White", fit = "Regular")
        )
        val recs = engine.recommend(items = items, preferences = prefs("Petite"))
        assertTrue("Should produce results for unknown body type", recs.isNotEmpty())
        // No penalty since all fits return 0.7 for unknown body type (above threshold)
        val outfit = listOf(item(1, fit = "Fitted"), item(2, fit = "Relaxed"))
        assertEquals(0.0, engine.fitMismatchPenalty(outfit, prefs("Petite")), 0.001)
    }

    // ================================================================
    // Default fit safety
    // ================================================================

    @Test
    fun defaultFit_regularOnAllBodyTypes_noPenalty() {
        val outfit = listOf(item(1, "Top"), item(2, "Bottom"), item(3, "Shoes"))
        for (bodyType in listOf("Slim", "Athletic", "Plus-size", "Average", "Unknown")) {
            assertEquals(
                "Regular fit should never trigger penalty for $bodyType",
                0.0,
                engine.fitMismatchPenalty(outfit, prefs(bodyType)),
                0.001
            )
        }
    }

    @Test
    fun defaultFit_regularCompatibility_alwaysAboveThreshold() {
        for (bodyType in listOf("Slim", "Athletic", "Plus-size", "Average", "Unknown")) {
            val compat = engine.fitCompatibility("Regular", bodyType)
            assertTrue(
                "Regular fit compat ($compat) should be >= threshold for $bodyType",
                compat >= OutfitRecommendationEngine.FIT_MISMATCH_THRESHOLD
            )
        }
    }

    @Test
    fun defaultFit_averageBodyType_highCompat() {
        val compat = engine.fitCompatibility("Regular", "Average")
        assertTrue("Regular + Average should have high compat", compat >= 0.7)
    }

    // ================================================================
    // Performance
    // ================================================================

    @Test
    fun performance_fitCompatibility_10000calls_fast() {
        val fits = listOf("Fitted", "Regular", "Relaxed", "Oversized")
        val bodyTypes = listOf("Slim", "Athletic", "Plus-size", "Average")
        val start = System.nanoTime()
        repeat(10_000) {
            engine.fitCompatibility(fits[it % fits.size], bodyTypes[it % bodyTypes.size])
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertTrue(
            "10000 fitCompatibility calls should take < 100ms (took ${elapsedMs}ms)",
            elapsedMs < 100
        )
    }

    @Test
    fun performance_largeWardrobe_noRegression() {
        val items = mutableListOf<ClothingItem>()
        val fits = listOf("Fitted", "Regular", "Relaxed", "Oversized")
        val colors = listOf("Black", "White", "Blue", "Red", "Green")
        var id = 1
        for (fit in fits) {
            repeat(5) { i ->
                items.add(item(id++, "Top", colors[i], fit = fit))
                items.add(item(id++, "Bottom", colors[i], fit = fit))
                items.add(item(id++, "Shoes", colors[i], fit = fit))
            }
        }

        val start = System.nanoTime()
        val recs = engine.recommend(items = items, preferences = prefs("Athletic"))
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertTrue("Should produce recommendations", recs.isNotEmpty())
        assertTrue(
            "Large wardrobe should complete in < 500ms (took ${elapsedMs}ms)",
            elapsedMs < 500
        )
    }

    // ================================================================
    // Explanation
    // ================================================================

    @Test
    fun explanation_highFitCompat_mentionsBuild() {
        val outfit = listOf(
            item(1, "Top", "Blue", fit = "Fitted"),
            item(2, "Bottom", "Black", fit = "Fitted"),
            item(3, "Shoes", "White", fit = "Fitted")
        )
        val recs = engine.recommend(items = outfit, preferences = prefs("Athletic"))
        assertTrue(recs.isNotEmpty())
        val explanation = recs.first().explanation.lowercase()
        assertTrue(
            "High fit compat explanation should mention build or fit: $explanation",
            explanation.contains("build") || explanation.contains("fit")
        )
    }

    @Test
    fun explanation_lowFitCompat_suggestsAlternatives() {
        val outfit = listOf(
            item(1, "Top", "Blue", fit = "Fitted"),
            item(2, "Bottom", "Black", fit = "Fitted"),
            item(3, "Shoes", "White", fit = "Fitted")
        )
        val recs = engine.recommend(items = outfit, preferences = prefs("Plus-size"))
        assertTrue(recs.isNotEmpty())
        val explanation = recs.first().explanation.lowercase()
        assertTrue(
            "Low fit compat should suggest trying different fits: $explanation",
            explanation.contains("fit") || explanation.contains("consider")
        )
    }

    // ================================================================
    // Combined with other scoring factors
    // ================================================================

    @Test
    fun combined_fitCompatWithTime() {
        val items = listOf(
            item(1, "Top", "Blue", fit = "Fitted"),
            item(2, "Bottom", "Black", fit = "Fitted"),
            item(3, "Shoes", "White", fit = "Regular")
        )
        val recs = engine.recommend(
            items = items,
            preferences = prefs("Athletic"),
            timeCategory = TimeCategory.AFTERNOON
        )
        assertTrue("Should produce results with fit + time", recs.isNotEmpty())
        assertTrue("Scores should be positive", recs.all { it.score > 0 })
    }

    @Test
    fun combined_fitCompatWithTemperature() {
        val items = listOf(
            item(1, "Top", "Black", "Summer", 4, "Fitted"),
            item(2, "Bottom", "White", "Summer", 4, "Fitted"),
            item(3, "Shoes", "Black", "All", 3, "Regular")
        )
        val recs = engine.recommend(
            items = items,
            preferences = prefs("Athletic"),
            temperatureCategory = TemperatureCategory.HOT
        )
        assertTrue("Should produce results with fit + temperature", recs.isNotEmpty())
        assertTrue("Scores should be positive", recs.all { it.score > 0 })
    }

    @Test
    fun combined_fitCompatWithFreshnessAndSaved() {
        val items = listOf(
            item(1, "Top", "Blue", fit = "Fitted"),
            item(2, "Top", "Blue", fit = "Relaxed"),
            item(3, "Bottom", "Black", fit = "Regular"),
            item(4, "Shoes", "White", fit = "Regular")
        )
        val history = setOf(setOf(1, 3, 4))
        val saved = setOf(setOf(2, 3, 4))
        val recs = engine.recommend(
            items = items,
            preferences = prefs("Athletic"),
            recentlyShown = history,
            savedOutfitIds = saved
        )
        assertTrue("Should produce results with all factors combined", recs.isNotEmpty())
        assertTrue("Scores should be positive", recs.all { it.score > 0 })
    }

    @Test
    fun combined_allContexts_producesResults() {
        val items = listOf(
            item(1, "Top", "Blue", "Summer", 4, "Fitted"),
            item(2, "Top", "Red", "Summer", 3, "Relaxed"),
            item(3, "Bottom", "Black", "All", 3, "Regular"),
            item(4, "Bottom", "White", "Summer", 4, "Fitted"),
            item(5, "Shoes", "White", "All", 3, "Regular")
        )
        val recs = engine.recommend(
            items = items,
            preferences = prefs("Athletic"),
            recentlyShown = setOf(setOf(1, 3, 5)),
            plannedItemIds = setOf(2),
            timeCategory = TimeCategory.AFTERNOON,
            temperatureCategory = TemperatureCategory.WARM,
            savedOutfitIds = setOf(setOf(1, 4, 5))
        )
        assertTrue("All contexts combined should produce results", recs.isNotEmpty())
        assertTrue("Scores should be positive", recs.all { it.score > 0 })
    }
}
