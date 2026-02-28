package com.fitgpt.app

import com.fitgpt.app.ai.OutfitRecommendationEngine
import com.fitgpt.app.data.model.ClothingItem
import com.fitgpt.app.data.model.OutfitRecommendation
import com.fitgpt.app.data.model.UserPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests that the recommendation engine produces diverse results across
 * multiple refreshes and handles edge cases gracefully.
 *
 * Covers:
 *  - Small wardrobes (1-3 items): no crashes, graceful history reset
 *  - Medium wardrobes (8-10 items): different results across refreshes
 *  - Large wardrobes (20+ items): no crashes, item diversity, capped combos
 *  - Season / fit / comfort preferences are respected
 *  - Overlap penalty promotes variety on refresh
 *  - History larger than total combinations triggers graceful reset
 *  - Scores always stay positive
 *  - Explanations are always present
 */
class RefreshDiversityTest {

    private lateinit var engine: OutfitRecommendationEngine

    private val defaultPrefs = UserPreferences(
        bodyType = "Average",
        stylePreference = "Casual",
        comfortPreference = 3,
        preferredSeasons = listOf("Spring", "Summer", "Fall", "Winter")
    )

    @Before
    fun setUp() {
        engine = OutfitRecommendationEngine()
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun makeItem(
        id: Int, category: String, color: String,
        season: String = "All", comfort: Int = 3, fit: String = "Regular"
    ) = ClothingItem(id, category, color, season, comfort, fit)

    /** Simulate multiple refreshes, accumulating history. */
    private fun simulateRefreshes(
        items: List<ClothingItem>,
        preferences: UserPreferences,
        count: Int
    ): List<List<OutfitRecommendation>> {
        val history = mutableSetOf<Set<Int>>()
        val allRounds = mutableListOf<List<OutfitRecommendation>>()

        repeat(count) {
            val recs = engine.recommend(items, preferences, history)
            allRounds.add(recs)
            // Record shown outfit IDs
            for (rec in recs) {
                history.add(rec.items.map { it.id }.toSet())
            }
            // Cap history like the ViewModel does
            while (history.size > OutfitRecommendationEngine.MAX_HISTORY_SIZE) {
                history.remove(history.first())
            }
        }
        return allRounds
    }

    // ------------------------------------------------------------------
    // Small wardrobe (1-3 items)
    // ------------------------------------------------------------------

    @Test
    fun smallWardrobe_singleItem_noCrash() {
        val items = listOf(makeItem(1, "Top", "Black"))
        val rounds = simulateRefreshes(items, defaultPrefs, 10)

        for (round in rounds) {
            // May have individual-item fallback
            for (rec in round) {
                assertTrue("Score must be positive", rec.score > 0)
                assertTrue("Must have at least one item", rec.items.isNotEmpty())
                assertTrue("Explanation must be non-empty", rec.explanation.isNotBlank())
            }
        }
    }

    @Test
    fun smallWardrobe_twoItems_noCrash() {
        val items = listOf(
            makeItem(1, "Top", "Black"),
            makeItem(2, "Bottom", "Blue")
        )
        val rounds = simulateRefreshes(items, defaultPrefs, 10)

        for (round in rounds) {
            assertTrue("Should produce recommendations", round.isNotEmpty())
        }
    }

    @Test
    fun smallWardrobe_threeItems_gracefulHistoryReset() {
        val items = listOf(
            makeItem(1, "Top", "Black"),
            makeItem(2, "Bottom", "Blue"),
            makeItem(3, "Shoes", "White")
        )
        // With only a few combinations possible, history will fill up quickly.
        // The engine should gracefully reset and still return results.
        val rounds = simulateRefreshes(items, defaultPrefs, 20)

        for ((i, round) in rounds.withIndex()) {
            assertTrue("Round $i must produce recommendations", round.isNotEmpty())
            for (rec in round) {
                assertTrue("Score must be positive in round $i", rec.score > 0)
            }
        }
    }

    @Test
    fun smallWardrobe_onlyTops_noCrash() {
        val items = listOf(
            makeItem(1, "Top", "Black"),
            makeItem(2, "Top", "White"),
            makeItem(3, "Top", "Red")
        )
        // No bottoms — can't form outfits, falls back to individual items
        val rounds = simulateRefreshes(items, defaultPrefs, 5)

        for (round in rounds) {
            assertTrue("Should produce individual-item recommendations", round.isNotEmpty())
        }
    }

    @Test
    fun emptyWardrobe_returnsEmpty() {
        val recs = engine.recommend(emptyList(), defaultPrefs, emptySet())
        assertTrue("Empty wardrobe should return empty recommendations", recs.isEmpty())
    }

    // ------------------------------------------------------------------
    // Medium wardrobe (8-10 items)
    // ------------------------------------------------------------------

    private val mediumWardrobe = listOf(
        makeItem(1, "Top", "Black", "Fall", 4),
        makeItem(2, "Top", "White", "Summer", 5),
        makeItem(3, "Top", "Navy", "Winter", 3),
        makeItem(4, "Bottom", "Blue", "All", 4),
        makeItem(5, "Bottom", "Black", "Fall", 3),
        makeItem(6, "Bottom", "Khaki", "Summer", 5),
        makeItem(7, "Shoes", "White", "All", 4),
        makeItem(8, "Shoes", "Black", "All", 3),
        makeItem(9, "Outerwear", "Gray", "Winter", 2),
        makeItem(10, "Accessory", "Gold", "All", 5)
    )

    @Test
    fun mediumWardrobe_differentResultsAcrossRefreshes() {
        val rounds = simulateRefreshes(mediumWardrobe, defaultPrefs, 5)

        // Collect the sets of outfit IDs from each round
        val roundSets = rounds.map { round ->
            round.map { rec -> rec.items.map { it.id }.toSet() }.toSet()
        }

        // At least some rounds should differ
        val uniqueRoundSets = roundSets.toSet()
        assertTrue(
            "Should have at least 2 different recommendation sets across 5 refreshes, got ${uniqueRoundSets.size}",
            uniqueRoundSets.size >= 2
        )
    }

    @Test
    fun mediumWardrobe_noExactDuplicatesInSameRound() {
        val rounds = simulateRefreshes(mediumWardrobe, defaultPrefs, 5)

        for ((i, round) in rounds.withIndex()) {
            val outfitSets = round.map { rec -> rec.items.map { it.id }.sorted() }
            val uniqueSets = outfitSets.toSet()
            assertEquals(
                "Round $i should not contain duplicate outfit combos",
                outfitSets.size, uniqueSets.size
            )
        }
    }

    @Test
    fun mediumWardrobe_scoresAlwaysPositive() {
        val rounds = simulateRefreshes(mediumWardrobe, defaultPrefs, 10)

        for ((i, round) in rounds.withIndex()) {
            for (rec in round) {
                assertTrue(
                    "Score in round $i must be positive, got ${rec.score}",
                    rec.score > 0
                )
            }
        }
    }

    @Test
    fun mediumWardrobe_explanationsAlwaysPresent() {
        val rounds = simulateRefreshes(mediumWardrobe, defaultPrefs, 10)

        for ((i, round) in rounds.withIndex()) {
            for (rec in round) {
                assertTrue(
                    "Explanation in round $i must be non-blank",
                    rec.explanation.isNotBlank()
                )
                for (item in rec.items) {
                    assertTrue(
                        "itemExplanation for item ${item.id} in round $i must exist",
                        rec.itemExplanations.containsKey(item.id)
                    )
                }
            }
        }
    }

    @Test
    fun mediumWardrobe_itemDiversityAcrossRefreshes() {
        val rounds = simulateRefreshes(mediumWardrobe, defaultPrefs, 5)

        // Collect all item IDs referenced across all rounds
        val allUsedIds = rounds.flatMap { round ->
            round.flatMap { rec -> rec.items.map { it.id } }
        }.toSet()

        // With 10 items the engine should use at least half of them
        assertTrue(
            "Should use at least 5 of 10 items across 5 refreshes, used ${allUsedIds.size}",
            allUsedIds.size >= 5
        )
    }

    // ------------------------------------------------------------------
    // Season / fit / comfort preferences respected
    // ------------------------------------------------------------------

    @Test
    fun preferences_seasonFilterAffectsScoring() {
        val summerPrefs = defaultPrefs.copy(preferredSeasons = listOf("Summer"))

        val summerItems = listOf(
            makeItem(1, "Top", "White", "Summer", 4),
            makeItem(2, "Top", "Black", "Winter", 4),
            makeItem(3, "Bottom", "Blue", "Summer", 4),
            makeItem(4, "Bottom", "Gray", "Winter", 4)
        )

        val recs = engine.recommend(summerItems, summerPrefs)

        // The top-scoring recommendation should prefer summer items
        val topRec = recs.maxByOrNull { it.score }!!
        val summerCount = topRec.items.count { it.season.equals("Summer", ignoreCase = true) }
        val winterCount = topRec.items.count { it.season.equals("Winter", ignoreCase = true) }

        assertTrue(
            "Top recommendation should favor summer items (summer=$summerCount, winter=$winterCount)",
            summerCount >= winterCount
        )
    }

    @Test
    fun preferences_comfortPreferenceAffectsScoring() {
        val highComfortPrefs = defaultPrefs.copy(comfortPreference = 5)

        val items = listOf(
            makeItem(1, "Top", "Black", "All", 5),   // high comfort
            makeItem(2, "Top", "Red", "All", 1),      // low comfort
            makeItem(3, "Bottom", "Blue", "All", 5),   // high comfort
            makeItem(4, "Bottom", "Gray", "All", 1)    // low comfort
        )

        val recs = engine.recommend(items, highComfortPrefs)
        val topRec = recs.maxByOrNull { it.score }!!

        val avgComfort = topRec.items.sumOf { it.comfortLevel }.toDouble() / topRec.items.size
        assertTrue(
            "Top recommendation should have high avg comfort for comfort-preference=5, got $avgComfort",
            avgComfort >= 3.0
        )
    }

    @Test
    fun preferences_bodyTypeFitAffectsScoring() {
        val slimPrefs = defaultPrefs.copy(bodyType = "Slim")

        val items = listOf(
            makeItem(1, "Top", "Black", "All", 3, "Fitted"),     // good for slim
            makeItem(2, "Top", "White", "All", 3, "Regular"),
            makeItem(3, "Bottom", "Blue", "All", 3, "Fitted"),   // good for slim
            makeItem(4, "Bottom", "Gray", "All", 3, "Regular")
        )

        val fittedScore = engine.scoreOutfit(
            listOf(items[0], items[2]), slimPrefs
        )
        val regularScore = engine.scoreOutfit(
            listOf(items[1], items[3]), slimPrefs
        )

        assertTrue(
            "Fitted outfit should score higher for Slim body type ($fittedScore vs $regularScore)",
            fittedScore >= regularScore
        )
    }

    // ------------------------------------------------------------------
    // Overlap penalty
    // ------------------------------------------------------------------

    @Test
    fun overlapPenalty_noHistoryReturnsZero() {
        val items = listOf(makeItem(1, "Top", "Black"), makeItem(2, "Bottom", "Blue"))
        val penalty = engine.overlapPenalty(items, emptySet())
        assertEquals("No history should produce zero penalty", 0.0, penalty, 0.001)
    }

    @Test
    fun overlapPenalty_fullOverlapGivesMaxPenalty() {
        val items = listOf(makeItem(1, "Top", "Black"), makeItem(2, "Bottom", "Blue"))
        val history = setOf(setOf(1, 2, 3)) // IDs 1 and 2 overlap with a 3-item set
        val penalty = engine.overlapPenalty(items, history)
        // 2 out of 2 overlap with {1,2,3} => 100% overlap => WEIGHT_DIVERSITY
        assertEquals(
            "Full overlap should produce max penalty",
            OutfitRecommendationEngine.WEIGHT_DIVERSITY, penalty, 0.001
        )
    }

    @Test
    fun overlapPenalty_partialOverlapGivesProportionalPenalty() {
        val items = listOf(
            makeItem(1, "Top", "Black"),
            makeItem(2, "Bottom", "Blue"),
            makeItem(3, "Shoes", "White"),
            makeItem(4, "Outerwear", "Gray")
        )
        // Only 2 of 4 items overlap
        val history = setOf(setOf(1, 2))
        val penalty = engine.overlapPenalty(items, history)

        val expectedPenalty = 0.5 * OutfitRecommendationEngine.WEIGHT_DIVERSITY
        assertEquals(
            "50% overlap should give half the max penalty",
            expectedPenalty, penalty, 0.001
        )
    }

    // ------------------------------------------------------------------
    // Large wardrobe (20+ items) — no crashes, capped combinations
    // ------------------------------------------------------------------

    private val largeWardrobe = buildList {
        var id = 1
        val colors = listOf("Black", "White", "Blue", "Red", "Green", "Gray", "Navy", "Beige")
        val seasons = listOf("Spring", "Summer", "Fall", "Winter", "All")
        val fits = listOf("Regular", "Fitted", "Relaxed", "Oversized")

        // 8 tops
        for (i in 0 until 8) {
            add(makeItem(id++, "Top", colors[i], seasons[i % seasons.size], (i % 5) + 1, fits[i % fits.size]))
        }
        // 6 bottoms
        for (i in 0 until 6) {
            add(makeItem(id++, "Bottom", colors[i % colors.size], seasons[i % seasons.size], (i % 5) + 1, fits[i % fits.size]))
        }
        // 4 shoes
        for (i in 0 until 4) {
            add(makeItem(id++, "Shoes", colors[i % colors.size], "All", (i % 5) + 1))
        }
        // 3 outerwear
        for (i in 0 until 3) {
            add(makeItem(id++, "Outerwear", colors[i % colors.size], seasons[i % seasons.size], (i % 5) + 1))
        }
        // 3 accessories
        for (i in 0 until 3) {
            add(makeItem(id++, "Accessory", colors[i % colors.size], "All", (i % 5) + 1))
        }
    }

    @Test
    fun largeWardrobe_noCrashAcross20Refreshes() {
        val rounds = simulateRefreshes(largeWardrobe, defaultPrefs, 20)

        assertEquals("Should produce 20 rounds of results", 20, rounds.size)
        for ((i, round) in rounds.withIndex()) {
            assertTrue("Round $i must have recommendations", round.isNotEmpty())
        }
    }

    @Test
    fun largeWardrobe_scoresAlwaysPositive() {
        val rounds = simulateRefreshes(largeWardrobe, defaultPrefs, 20)

        for ((i, round) in rounds.withIndex()) {
            for (rec in round) {
                assertTrue(
                    "Score in round $i must be positive, got ${rec.score}",
                    rec.score > 0
                )
            }
        }
    }

    @Test
    fun largeWardrobe_explanationsAlwaysPresent() {
        val rounds = simulateRefreshes(largeWardrobe, defaultPrefs, 10)

        for ((i, round) in rounds.withIndex()) {
            for (rec in round) {
                assertTrue("Explanation must be non-blank in round $i", rec.explanation.isNotBlank())
                for (item in rec.items) {
                    assertTrue(
                        "Item ${item.id} must have an explanation in round $i",
                        rec.itemExplanations.containsKey(item.id)
                    )
                }
            }
        }
    }

    @Test
    fun largeWardrobe_broadItemCoverageAcrossRefreshes() {
        val rounds = simulateRefreshes(largeWardrobe, defaultPrefs, 20)

        val allUsedIds = rounds.flatMap { round ->
            round.flatMap { rec -> rec.items.map { it.id } }
        }.toSet()

        // With 24 items and 20 refreshes, we should touch a good portion
        assertTrue(
            "Should use at least 10 of ${largeWardrobe.size} items across 20 refreshes, used ${allUsedIds.size}",
            allUsedIds.size >= 10
        )
    }

    @Test
    fun largeWardrobe_maxRecommendationsPerRound() {
        val rounds = simulateRefreshes(largeWardrobe, defaultPrefs, 5)

        for ((i, round) in rounds.withIndex()) {
            assertTrue(
                "Round $i should have at most 5 recommendations, got ${round.size}",
                round.size <= 5
            )
        }
    }

    // ------------------------------------------------------------------
    // History exhaustion — graceful reset
    // ------------------------------------------------------------------

    @Test
    fun historyLargerThanCombinations_gracefulReset() {
        // Very small wardrobe: only 1 possible combination (top + bottom)
        val tinyWardrobe = listOf(
            makeItem(1, "Top", "Black"),
            makeItem(2, "Bottom", "Blue")
        )

        // Simulate many refreshes — history will fill up but only 1 combo exists
        val rounds = simulateRefreshes(tinyWardrobe, defaultPrefs, 15)

        for ((i, round) in rounds.withIndex()) {
            assertTrue("Round $i must still produce results despite history exhaustion", round.isNotEmpty())
            for (rec in round) {
                assertTrue("Score in round $i must be positive", rec.score > 0)
            }
        }
    }

    // ------------------------------------------------------------------
    // Diversity within a single round
    // ------------------------------------------------------------------

    @Test
    fun singleRound_diverseItemSelection() {
        // All different items so diversity selection can spread out
        val items = listOf(
            makeItem(1, "Top", "Black", "All", 4),
            makeItem(2, "Top", "White", "All", 4),
            makeItem(3, "Top", "Red", "All", 4),
            makeItem(4, "Bottom", "Blue", "All", 4),
            makeItem(5, "Bottom", "Gray", "All", 4),
            makeItem(6, "Bottom", "Khaki", "All", 4),
            makeItem(7, "Shoes", "White", "All", 4),
            makeItem(8, "Shoes", "Black", "All", 4)
        )

        val recs = engine.recommend(items, defaultPrefs)

        // With greedy diversity selection, the 5 chosen outfits should use
        // a variety of items, not all the same top+bottom pair
        val allUsedIds = recs.flatMap { rec -> rec.items.map { it.id } }.toSet()

        assertTrue(
            "A single round should use at least 4 different items, used ${allUsedIds.size}",
            allUsedIds.size >= 4
        )
    }
}
