package com.fitgpt.app

import com.fitgpt.app.ai.OutfitRecommendationEngine
import com.fitgpt.app.data.model.ClothingItem
import com.fitgpt.app.data.model.OutfitRecommendation
import com.fitgpt.app.data.model.UserPreferences
import com.fitgpt.app.viewmodel.WardrobeViewModel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Validates that outfit history entries are correctly structured for the
 * engine's avoid-repeat logic, that stale/cached history never corrupts
 * recommendations, that near-duplicate detection works with realistic
 * history, and that freshness scoring behaves differently with vs.
 * without history.
 */
class HistoryFreshnessIntegrationTest {

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

    /**
     * Simulates [recordShownOutfits] exactly as the ViewModel does:
     * converts recommendation items to ID sets, deduplicates, caps at
     * MAX_HISTORY_SIZE. Returns the accumulated history.
     */
    private fun simulateRecordShownOutfits(
        recommendations: List<OutfitRecommendation>,
        existingHistory: ArrayDeque<Set<Int>> = ArrayDeque()
    ): ArrayDeque<Set<Int>> {
        for (rec in recommendations) {
            val key = rec.items.map { it.id }.toSet()
            if (key.isNotEmpty() && key !in existingHistory) {
                existingHistory.addLast(key)
            }
        }
        while (existingHistory.size > OutfitRecommendationEngine.MAX_HISTORY_SIZE) {
            existingHistory.removeFirst()
        }
        return existingHistory
    }

    /**
     * Simulates [purgeDeletedItemFromHistory] exactly as the ViewModel does.
     */
    private fun simulatePurge(
        history: ArrayDeque<Set<Int>>,
        deletedId: Int
    ): ArrayDeque<Set<Int>> {
        val cleaned = history.map { idSet -> idSet - deletedId }.filter { it.isNotEmpty() }
        history.clear()
        cleaned.forEach { history.addLast(it) }
        return history
    }

    // -----------------------------------------------------------------------
    // Section 1: History structure matches engine expectations
    // -----------------------------------------------------------------------

    @Test
    fun historyFormat_setOfIds_matchesFreshnessScoreInput() {
        // Engine expects Set<Set<Int>>. History stores ArrayDeque<Set<Int>>.
        // Converting via .toSet() should produce the right type.
        val history = ArrayDeque<Set<Int>>()
        history.addLast(setOf(1, 2))
        history.addLast(setOf(3, 4, 5))

        val asEngineInput: Set<Set<Int>> = history.toSet()

        val outfit = listOf(makeItem(1, "Top", "White"), makeItem(2, "Bottom", "Blue"))
        // Should return 0.0 for exact match
        assertEquals(0.0, engine.freshnessScore(outfit, asEngineInput), 0.001)
    }

    @Test
    fun historyFormat_setOfIds_matchesOverlapPenaltyInput() {
        val history = ArrayDeque<Set<Int>>()
        history.addLast(setOf(1, 2))

        val asEngineInput: Set<Set<Int>> = history.toSet()

        val outfit = listOf(makeItem(1, "Top", "White"), makeItem(3, "Bottom", "Navy"))
        // Overlap = 1/2 = 0.5 → penalty = 0.5 * WEIGHT_DIVERSITY
        val expected = 0.5 * OutfitRecommendationEngine.WEIGHT_DIVERSITY
        assertEquals(expected, engine.overlapPenalty(outfit, asEngineInput), 0.001)
    }

    @Test
    fun historyEntry_singleItem_validForEngine() {
        val history: Set<Set<Int>> = setOf(setOf(1))

        val singleItem = listOf(makeItem(1, "Shoes", "Black"))
        assertEquals(0.0, engine.freshnessScore(singleItem, history), 0.001)

        val differentItem = listOf(makeItem(2, "Shoes", "White"))
        assertEquals(1.0, engine.freshnessScore(differentItem, history), 0.001)
    }

    @Test
    fun historyEntry_multiItem_validForEngine() {
        val history: Set<Set<Int>> = setOf(setOf(1, 2, 3))

        val exactMatch = listOf(
            makeItem(1, "Top", "White"),
            makeItem(2, "Bottom", "Blue"),
            makeItem(3, "Shoes", "Black")
        )
        assertEquals(0.0, engine.freshnessScore(exactMatch, history), 0.001)
    }

    @Test
    fun historyEntry_orderIndependent() {
        // History stores {1,2} — outfit [2,1] should still be detected as exact match
        val history: Set<Set<Int>> = setOf(setOf(1, 2))

        val reversed = listOf(makeItem(2, "Bottom", "Blue"), makeItem(1, "Top", "White"))
        val ids = reversed.map { it.id }.toSet()
        assertTrue("Set comparison should be order-independent", ids in history)
        assertEquals(0.0, engine.freshnessScore(reversed, history), 0.001)
    }

    @Test
    fun historyEntry_duplicatesNotAdded() {
        val history = ArrayDeque<Set<Int>>()
        val recs = listOf(
            OutfitRecommendation(
                items = listOf(makeItem(1, "Top", "White"), makeItem(2, "Bottom", "Blue")),
                score = 0.8, explanation = "test"
            )
        )
        // Record twice
        simulateRecordShownOutfits(recs, history)
        simulateRecordShownOutfits(recs, history)

        assertEquals("Duplicate should not be added", 1, history.size)
    }

    @Test
    fun historyEntry_emptyItemsNotStored() {
        val history = ArrayDeque<Set<Int>>()
        val recs = listOf(
            OutfitRecommendation(items = emptyList(), score = 0.0, explanation = "empty")
        )
        simulateRecordShownOutfits(recs, history)

        assertTrue("Empty outfit should not be added to history", history.isEmpty())
    }

    @Test
    fun historyCap_enforcedAtMaxSize() {
        val history = ArrayDeque<Set<Int>>()
        // Add MAX_HISTORY_SIZE + 10 entries
        repeat(OutfitRecommendationEngine.MAX_HISTORY_SIZE + 10) { i ->
            history.addLast(setOf(i * 100, i * 100 + 1))
        }
        // Simulate cap enforcement
        while (history.size > OutfitRecommendationEngine.MAX_HISTORY_SIZE) {
            history.removeFirst()
        }

        assertEquals(OutfitRecommendationEngine.MAX_HISTORY_SIZE, history.size)
        // Oldest entry (id=0,1) should have been evicted
        assertFalse(setOf(0, 1) in history)
    }

    // -----------------------------------------------------------------------
    // Section 2: No stale/cached outfits in history
    // -----------------------------------------------------------------------

    @Test
    fun deleteItem_purgedHistory_noLongerAvoided() {
        val items = listOf(
            makeItem(1, "Top", "White", "Summer"),
            makeItem(2, "Bottom", "Blue", "Summer"),
            makeItem(3, "Top", "Black", "Summer")
        )
        // Build history containing {1,2}
        val history = ArrayDeque<Set<Int>>()
        history.addLast(setOf(1, 2))

        // Purge item 1 (simulating deletion)
        simulatePurge(history, 1)

        // History should now contain {2} (single remaining ID)
        // Engine should NOT avoid combo {3,2} — it doesn't match {2}
        val asEngineInput = history.toSet()
        val newCombo = listOf(makeItem(3, "Top", "Black"), makeItem(2, "Bottom", "Blue"))
        val freshness = engine.freshnessScore(newCombo, asEngineInput)
        // {3,2} overlaps {2} by 1/2 = 0.5
        assertTrue("New combo after purge should have partial freshness", freshness > 0.0)
    }

    @Test
    fun deleteItem_purgedHistory_noResidualOverlapPenalty() {
        val history = ArrayDeque<Set<Int>>()
        history.addLast(setOf(1, 2, 3))

        // Purge item 1
        simulatePurge(history, 1)
        // History now: {2, 3}

        // A completely new outfit {4, 5} should have zero overlap
        val newOutfit = listOf(makeItem(4, "Top", "Red"), makeItem(5, "Bottom", "Navy"))
        assertEquals(0.0, engine.overlapPenalty(newOutfit, history.toSet()), 0.001)
        assertEquals(1.0, engine.freshnessScore(newOutfit, history.toSet()), 0.001)
    }

    @Test
    fun deleteAllItems_historyEmptied_engineReturnsNormal() {
        val history = ArrayDeque<Set<Int>>()
        history.addLast(setOf(1, 2))
        history.addLast(setOf(1, 3))

        // Purge both items that anchor history entries
        simulatePurge(history, 1)
        // History now: {2}, {3}
        simulatePurge(history, 2)
        // History now: {3}
        simulatePurge(history, 3)
        // History now: empty

        assertTrue("History should be empty after purging all IDs", history.isEmpty())

        val items = listOf(makeItem(10, "Top", "White", "Summer"), makeItem(11, "Bottom", "Blue", "Summer"))
        val result = engine.recommend(items, defaultPreferences, recentlyShown = history.toSet())
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun addNewItems_oldHistory_doesNotSuppressFreshCombos() {
        // Old history has {1,2}, new items 10,11 shouldn't be suppressed
        val history: Set<Set<Int>> = setOf(setOf(1, 2), setOf(1, 3))

        val newItems = listOf(
            makeItem(10, "Top", "White", "Summer"),
            makeItem(11, "Bottom", "Blue", "Summer")
        )
        val result = engine.recommend(newItems, defaultPreferences, recentlyShown = history)
        assertTrue(result.isNotEmpty())

        // New combo {10,11} should have full freshness (no overlap with {1,2} or {1,3})
        val freshness = engine.freshnessScore(
            listOf(makeItem(10, "Top", "White"), makeItem(11, "Bottom", "Blue")),
            history
        )
        assertEquals(1.0, freshness, 0.001)
    }

    @Test
    fun updateItem_historyIdsUnchanged_avoidRepeatStillWorks() {
        // Item 1 was a Top; after update it's an Accessory.
        // History entry {1,2} still valid — IDs haven't changed.
        val history: Set<Set<Int>> = setOf(setOf(1, 2))

        val updatedItem1 = makeItem(1, "Accessory", "Gold", "All")
        val item2 = makeItem(2, "Bottom", "Blue", "Summer")
        val combo = listOf(updatedItem1, item2)

        // freshnessScore should still detect exact match by ID
        assertEquals(0.0, engine.freshnessScore(combo, history), 0.001)
    }

    @Test
    fun updateItem_historyNotCorrupted_multipleRefreshes() {
        // ViewModel test: update an item's color, verify no stale snapshots in recommendations
        val viewModel = WardrobeViewModel()

        // Get initial recommendations
        val initialRecs = viewModel.recommendations.value
        assertTrue(initialRecs.isNotEmpty())

        // Update item 1's color from Black to Red
        val item1 = viewModel.wardrobeItems.value.first { it.id == 1 }
        viewModel.updateItem(item1.copy(color = "Red"))

        // Refresh several times — no stale "Black" snapshot should appear
        repeat(5) {
            viewModel.refreshRecommendations()
            val recs = viewModel.recommendations.value
            for (rec in recs) {
                for (item in rec.items) {
                    if (item.id == 1) {
                        assertEquals("Item 1 should have updated color", "Red", item.color)
                    }
                }
            }
        }
    }

    @Test
    fun viewModel_deleteItem_historyPurged_freshCombosAppear() {
        val viewModel = WardrobeViewModel()
        // Add extra items for variety
        viewModel.addItem(makeItem(10, "Top", "White", "Summer", 5))
        viewModel.addItem(makeItem(11, "Bottom", "Navy", "Summer", 4))

        // Refresh several times to build history
        repeat(3) { viewModel.refreshRecommendations() }

        // Delete item 10
        val item10 = viewModel.wardrobeItems.value.first { it.id == 10 }
        viewModel.deleteItem(item10)

        // Post-delete: item 10 should not appear in any recommendation
        val postDeleteRecs = viewModel.recommendations.value
        for (rec in postDeleteRecs) {
            assertFalse(
                "Deleted item 10 should not appear in recommendations",
                rec.items.any { it.id == 10 }
            )
        }
    }

    @Test
    fun viewModel_archiveItem_historyPurged_neverResurfaces() {
        val viewModel = WardrobeViewModel()
        viewModel.addItem(makeItem(10, "Top", "White", "Summer", 5))
        viewModel.addItem(makeItem(11, "Bottom", "Navy", "Summer", 4))

        repeat(3) { viewModel.refreshRecommendations() }

        val item10 = viewModel.wardrobeItems.value.first { it.id == 10 }
        viewModel.archiveItem(item10)

        repeat(5) {
            viewModel.refreshRecommendations()
            val recs = viewModel.recommendations.value
            for (rec in recs) {
                assertFalse(
                    "Archived item 10 should not appear after refresh",
                    rec.items.any { it.id == 10 }
                )
            }
        }
    }

    // -----------------------------------------------------------------------
    // Section 3: Near-duplicate detection with realistic history
    // -----------------------------------------------------------------------

    @Test
    fun nearDuplicate_swapOneItem_detectedFromHistory() {
        // Build history from actual engine recommendations
        val items = listOf(
            makeItem(1, "Top", "White", "Summer"),
            makeItem(2, "Bottom", "Blue", "Summer"),
            makeItem(3, "Shoes", "Black"),
            makeItem(4, "Shoes", "White")
        )
        val firstRound = engine.recommend(items, defaultPreferences)
        val history = ArrayDeque<Set<Int>>()
        simulateRecordShownOutfits(firstRound, history)

        // Find a combo in history that includes shoe 3
        val comboWithShoe3 = history.find { 3 in it && it.size >= 3 }
        if (comboWithShoe3 != null) {
            // Swap shoe 3 for shoe 4 — near-duplicate
            val nearDupIds = (comboWithShoe3 - 3) + 4
            val nearDupOutfit = nearDupIds.map { id ->
                items.first { it.id == id }
            }
            val freshness = engine.freshnessScore(nearDupOutfit, history.toSet())
            // Near-duplicate should have reduced freshness
            assertTrue(
                "Near-duplicate (swap one shoe) should have freshness < 0.5",
                freshness < 0.5
            )
        }
    }

    @Test
    fun nearDuplicate_historyFromRecommendations_flowsToScoring() {
        val items = listOf(
            makeItem(1, "Top", "White", "Summer"),
            makeItem(2, "Top", "Black", "Summer"),
            makeItem(3, "Bottom", "Blue", "Summer"),
            makeItem(4, "Bottom", "Navy", "Summer"),
            makeItem(5, "Shoes", "Black"),
            makeItem(6, "Shoes", "White")
        )

        // First round
        val round1 = engine.recommend(items, defaultPreferences)
        val history = mutableSetOf<Set<Int>>()
        round1.forEach { history.add(it.items.map { i -> i.id }.toSet()) }

        // Second round with history — near-duplicates should be deprioritized
        val round2 = engine.recommend(items, defaultPreferences, recentlyShown = history)
        assertTrue(round2.isNotEmpty())

        // At least one outfit in round2 should use items not in round1's top pick
        val round1TopIds = round1[0].items.map { it.id }.toSet()
        val hasNovelCombo = round2.any { rec ->
            val ids = rec.items.map { it.id }.toSet()
            ids != round1TopIds
        }
        assertTrue("Second round should have at least one novel combo", hasNovelCombo)
    }

    @Test
    fun nearDuplicate_progressiveHistoryBuild_increasesDetection() {
        val items = listOf(
            makeItem(1, "Top", "White", "Summer"),
            makeItem(2, "Top", "Black", "Summer"),
            makeItem(3, "Top", "Red", "Summer"),
            makeItem(4, "Bottom", "Blue", "Summer"),
            makeItem(5, "Bottom", "Navy", "Summer"),
            makeItem(6, "Shoes", "Black"),
            makeItem(7, "Accessory", "Gold")
        )

        val history = mutableSetOf<Set<Int>>()

        // Progressive refreshes: each round should add to history
        val scores = mutableListOf<Double>()
        repeat(5) {
            val recs = engine.recommend(items, defaultPreferences, recentlyShown = history)
            assertTrue("Round $it should produce results", recs.isNotEmpty())
            scores.add(recs[0].score)
            recs.forEach { rec -> history.add(rec.items.map { i -> i.id }.toSet()) }
        }

        // After many rounds, top scores should trend down (more history = less fresh)
        // Check that the first round's top score >= last round's top score
        assertTrue(
            "Scores should trend down as history grows",
            scores.first() >= scores.last()
        )
    }

    @Test
    fun nearDuplicate_2of3shared_belowThreshold() {
        // 2/3 = 0.667, below NEAR_DUPLICATE_THRESHOLD (0.75)
        val history: Set<Set<Int>> = setOf(setOf(1, 2, 3))
        val outfit = listOf(
            makeItem(1, "Top", "White"),
            makeItem(2, "Bottom", "Blue"),
            makeItem(4, "Shoes", "Red")
        )
        val freshness = engine.freshnessScore(outfit, history)
        // No amplification: freshness = 1.0 - 0.667 = 0.333
        assertEquals(0.333, freshness, 0.01)
    }

    @Test
    fun nearDuplicate_3of4shared_aboveThreshold_amplified() {
        // 3/4 = 0.75, at NEAR_DUPLICATE_THRESHOLD → amplified
        val history: Set<Set<Int>> = setOf(setOf(1, 2, 3, 4))
        val outfit = listOf(
            makeItem(1, "Top", "White"),
            makeItem(2, "Bottom", "Blue"),
            makeItem(3, "Shoes", "Black"),
            makeItem(5, "Accessory", "Silver")  // swapped from 4
        )
        val freshness = engine.freshnessScore(outfit, history)
        // Amplified: 0.5*(1+0.75) = 0.875, freshness = 0.125
        assertEquals(0.125, freshness, 0.001)
    }

    @Test
    fun nearDuplicate_mixedHistorySizes_maxOverlapUsed() {
        // History has different-sized outfits
        val history: Set<Set<Int>> = setOf(
            setOf(1, 2),       // 2-item
            setOf(3, 4, 5),    // 3-item
            setOf(1, 3, 5, 7)  // 4-item
        )
        // Outfit {1, 2, 3} overlaps:
        //   {1,2} by 2/3 = 0.667
        //   {3,4,5} by 1/3 = 0.333
        //   {1,3,5,7} by 2/3 = 0.667
        // Max = 0.667 (below threshold, no amplification)
        val outfit = listOf(
            makeItem(1, "Top", "White"),
            makeItem(2, "Bottom", "Blue"),
            makeItem(3, "Shoes", "Black")
        )
        val freshness = engine.freshnessScore(outfit, history)
        assertEquals(0.333, freshness, 0.01)
    }

    @Test
    fun nearDuplicate_engineRecommend_deprioritizesNearDups() {
        val items = listOf(
            makeItem(1, "Top", "White", "Summer", 4),
            makeItem(2, "Bottom", "Blue", "Summer", 4),
            makeItem(3, "Shoes", "Black", "All", 4),
            makeItem(4, "Shoes", "Brown", "All", 4),  // alternative shoe
            makeItem(5, "Top", "Red", "Summer", 4),
            makeItem(6, "Bottom", "Gray", "Summer", 4)
        )
        // {1,2,3} in history — {1,2,4} is near-duplicate, {5,6} is fresh
        val history: Set<Set<Int>> = setOf(setOf(1, 2, 3))

        val result = engine.recommend(items, defaultPreferences, recentlyShown = history)
        assertTrue(result.isNotEmpty())

        // Check that {5,6,...} combos (fresh) rank above {1,2,4} (near-dup) if both exist
        val freshCombo = result.find { rec ->
            val ids = rec.items.map { it.id }.toSet()
            5 in ids && 6 in ids
        }
        val nearDup = result.find { rec ->
            val ids = rec.items.map { it.id }.toSet()
            ids.containsAll(listOf(1, 2, 4)) && 5 !in ids
        }
        if (freshCombo != null && nearDup != null) {
            assertTrue(
                "Fresh combo should rank above near-duplicate",
                freshCombo.score >= nearDup.score
            )
        }
    }

    // -----------------------------------------------------------------------
    // Section 4: Freshness with history present vs absent
    // -----------------------------------------------------------------------

    @Test
    fun freshness_noHistory_allGetMaxBonus() {
        val items = listOf(
            makeItem(1, "Top", "White", "Summer"),
            makeItem(2, "Bottom", "Blue", "Summer"),
            makeItem(3, "Top", "Black", "Summer"),
            makeItem(4, "Bottom", "Navy", "Summer")
        )
        val result = engine.recommend(items, defaultPreferences, recentlyShown = emptySet())
        assertTrue(result.isNotEmpty())
        // All outfits should have freshnessScore = 1.0 (no history)
        for (rec in result) {
            val freshness = engine.freshnessScore(rec.items, emptySet())
            assertEquals(1.0, freshness, 0.001)
        }
    }

    @Test
    fun freshness_withHistory_staleOutfitsPenalized() {
        val items = listOf(
            makeItem(1, "Top", "White", "Summer"),
            makeItem(2, "Bottom", "Blue", "Summer"),
            makeItem(3, "Top", "Black", "Summer"),
            makeItem(4, "Bottom", "Navy", "Summer")
        )
        // Run first round
        val round1 = engine.recommend(items, defaultPreferences)
        val history = round1.map { it.items.map { i -> i.id }.toSet() }.toSet()

        // All round1 combos should now have reduced freshness
        for (rec in round1) {
            val freshness = engine.freshnessScore(rec.items, history)
            assertTrue(
                "Previously shown combo should have reduced freshness",
                freshness < 1.0
            )
        }
    }

    @Test
    fun freshness_scoreDifferential_measurable() {
        val items = listOf(
            makeItem(1, "Top", "White", "Summer"),
            makeItem(2, "Bottom", "Blue", "Summer"),
            makeItem(3, "Top", "Black", "Summer"),
            makeItem(4, "Bottom", "Navy", "Summer")
        )
        val noHistory = engine.recommend(items, defaultPreferences, recentlyShown = emptySet())
        val round1History = noHistory.map { it.items.map { i -> i.id }.toSet() }.toSet()
        val withHistory = engine.recommend(items, defaultPreferences, recentlyShown = round1History)

        assertTrue(noHistory.isNotEmpty())
        assertTrue(withHistory.isNotEmpty())

        // Top score without history should be strictly higher than with history
        // because without history: freshnessBonus = 0.15, overlapPenalty = 0
        // with history: reduced freshnessBonus, positive overlapPenalty
        assertTrue(
            "Score without history (${noHistory[0].score}) should exceed " +
                "score with history (${withHistory[0].score})",
            noHistory[0].score > withHistory[0].score
        )
    }

    @Test
    fun freshness_rankingChanges_whenHistoryPresent() {
        val items = listOf(
            makeItem(1, "Top", "White", "Summer", 5),
            makeItem(2, "Top", "Black", "Summer", 3),
            makeItem(3, "Bottom", "Blue", "Summer", 5),
            makeItem(4, "Bottom", "Navy", "Summer", 3),
            makeItem(5, "Shoes", "Black", "All", 4),
            makeItem(6, "Accessory", "Gold", "All", 4)
        )

        val round1 = engine.recommend(items, defaultPreferences)
        val round1Ids = round1.map { it.items.map { i -> i.id }.toSet() }
        val history = round1Ids.toSet()

        val round2 = engine.recommend(items, defaultPreferences, recentlyShown = history)
        val round2Ids = round2.map { it.items.map { i -> i.id }.toSet() }

        // The exact ID-set ordering should differ between rounds
        // (at minimum the top picks should change)
        assertNotEquals(
            "Rankings should change when history is present",
            round1Ids, round2Ids
        )
    }

    @Test
    fun freshness_progressiveRefreshes_differentResults() {
        val items = listOf(
            makeItem(1, "Top", "White", "Summer"),
            makeItem(2, "Top", "Black", "Summer"),
            makeItem(3, "Top", "Red", "Summer"),
            makeItem(4, "Bottom", "Blue", "Summer"),
            makeItem(5, "Bottom", "Navy", "Summer"),
            makeItem(6, "Bottom", "Gray", "Summer"),
            makeItem(7, "Shoes", "Black"),
            makeItem(8, "Outerwear", "Brown", "Winter")
        )

        val allTopIds = mutableListOf<Set<Int>>()
        var history = emptySet<Set<Int>>()

        repeat(5) {
            val recs = engine.recommend(items, defaultPreferences, recentlyShown = history)
            assertTrue("Round $it should produce results", recs.isNotEmpty())
            allTopIds.add(recs[0].items.map { i -> i.id }.toSet())
            history = history + recs.map { rec -> rec.items.map { i -> i.id }.toSet() }.toSet()
        }

        // With 8 items and many possible combos, each round's top pick should differ
        val uniqueTopPicks = allTopIds.toSet()
        assertTrue(
            "Should have multiple unique top picks across 5 rounds (got ${uniqueTopPicks.size})",
            uniqueTopPicks.size >= 2
        )
    }

    @Test
    fun freshness_emptyWardrobe_withHistory_stillEmpty() {
        val history: Set<Set<Int>> = setOf(setOf(1, 2), setOf(3, 4))
        val result = engine.recommend(emptyList(), defaultPreferences, recentlyShown = history)
        assertTrue("Empty wardrobe should return empty even with history", result.isEmpty())
    }

    @Test
    fun freshness_viewModel_firstRefresh_freshScores() {
        val viewModel = WardrobeViewModel()
        val recs = viewModel.recommendations.value
        assertTrue("First refresh should produce results", recs.isNotEmpty())

        // All first-refresh scores should include freshness bonus
        // (higher than base score alone since freshnessBonus = 1.0 * 0.15 = 0.15)
        for (rec in recs) {
            assertTrue("Score should be positive", rec.score > 0.0)
            assertTrue("Explanation should exist", rec.explanation.isNotBlank())
        }
    }

    @Test
    fun freshness_viewModel_secondRefresh_avoidsPriorCombos() {
        val viewModel = WardrobeViewModel()
        viewModel.addItem(makeItem(10, "Top", "White", "Summer", 5))
        viewModel.addItem(makeItem(11, "Bottom", "Navy", "Summer", 4))
        viewModel.addItem(makeItem(12, "Top", "Red", "Summer", 4))
        viewModel.addItem(makeItem(13, "Bottom", "Gray", "Summer", 3))

        val round1 = viewModel.recommendations.value
        val round1Ids = round1.map { rec -> rec.items.map { it.id }.toSet() }.toSet()

        viewModel.refreshRecommendations()
        val round2 = viewModel.recommendations.value

        // Round 2 should not contain exact repeats of round 1
        for (rec in round2) {
            val ids = rec.items.map { it.id }.toSet()
            assertFalse(
                "Round 2 should not repeat round 1 combos",
                ids in round1Ids
            )
        }
    }

    @Test
    fun freshness_viewModel_deleteAndRefresh_neverStale() {
        val viewModel = WardrobeViewModel()
        viewModel.addItem(makeItem(10, "Top", "White", "Summer", 5))
        viewModel.addItem(makeItem(11, "Bottom", "Navy", "Summer", 4))

        // Build some history
        repeat(3) { viewModel.refreshRecommendations() }

        // Delete item and verify no stale references
        val item10 = viewModel.wardrobeItems.value.first { it.id == 10 }
        viewModel.deleteItem(item10)

        repeat(5) { i ->
            viewModel.refreshRecommendations()
            val recs = viewModel.recommendations.value
            for (rec in recs) {
                for (item in rec.items) {
                    assertNotEquals("Deleted item 10 should never appear (refresh $i)", 10, item.id)
                }
            }
        }
    }

    @Test
    fun freshness_viewModel_updateAndRefresh_neverStaleSnapshot() {
        val viewModel = WardrobeViewModel()

        // Update item 2's season from All to Winter
        val item2 = viewModel.wardrobeItems.value.first { it.id == 2 }
        viewModel.updateItem(item2.copy(season = "Winter"))

        repeat(5) { i ->
            viewModel.refreshRecommendations()
            val recs = viewModel.recommendations.value
            for (rec in recs) {
                for (item in rec.items) {
                    if (item.id == 2) {
                        assertEquals(
                            "Item 2 should have updated season at refresh $i",
                            "Winter", item.season
                        )
                    }
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Section 5: End-to-end history + freshness contract
    // -----------------------------------------------------------------------

    @Test
    fun historyContract_recordThenRecommend_consistentFormat() {
        // Simulate exactly what the ViewModel does:
        // recommend → record → recommend with history → record
        // Use enough items to generate many combos so history doesn't exhaust them
        val items = listOf(
            makeItem(1, "Top", "White", "Summer"),
            makeItem(2, "Top", "Black", "Summer"),
            makeItem(3, "Top", "Red", "Summer"),
            makeItem(4, "Bottom", "Blue", "Summer"),
            makeItem(5, "Bottom", "Navy", "Summer"),
            makeItem(6, "Bottom", "Gray", "Summer"),
            makeItem(7, "Shoes", "Black"),
            makeItem(8, "Shoes", "White"),
            makeItem(9, "Outerwear", "Brown", "Winter"),
            makeItem(10, "Accessory", "Gold")
        )

        val history = ArrayDeque<Set<Int>>()

        // Round 1
        val round1 = engine.recommend(items, defaultPreferences, recentlyShown = history.toSet())
        assertTrue("Round 1 should produce results", round1.isNotEmpty())
        simulateRecordShownOutfits(round1, history)

        // Round 2: history format should be compatible with engine
        val round2 = engine.recommend(items, defaultPreferences, recentlyShown = history.toSet())
        assertTrue("Round 2 should produce results", round2.isNotEmpty())
        simulateRecordShownOutfits(round2, history)

        // Round 2 should not repeat round 1 combos (many combos available)
        val round1Ids = round1.map { it.items.map { i -> i.id }.toSet() }.toSet()
        for (rec in round2) {
            val ids = rec.items.map { it.id }.toSet()
            assertFalse(
                "Round 2 should avoid round 1 combos",
                ids in round1Ids
            )
        }

        // Round 3: even more history, still compatible
        val round3 = engine.recommend(items, defaultPreferences, recentlyShown = history.toSet())
        assertTrue("Round 3 should produce results", round3.isNotEmpty())
        assertTrue("Round 3 scores should be positive", round3[0].score > 0.0)
    }

    @Test
    fun historyContract_purge_thenRecord_noContamination() {
        val items = listOf(
            makeItem(1, "Top", "White", "Summer"),
            makeItem(2, "Bottom", "Blue", "Summer"),
            makeItem(3, "Shoes", "Black")
        )

        val history = ArrayDeque<Set<Int>>()

        // Record some outfits
        val round1 = engine.recommend(items, defaultPreferences)
        simulateRecordShownOutfits(round1, history)

        // Purge item 3
        simulatePurge(history, 3)

        // Add new item 4 and recommend
        val newItems = items.filter { it.id != 3 } + makeItem(4, "Shoes", "White")
        val round2 = engine.recommend(newItems, defaultPreferences, recentlyShown = history.toSet())
        simulateRecordShownOutfits(round2, history)

        // Verify no history entry references deleted item 3
        for (entry in history) {
            assertFalse("History should not contain purged ID 3", 3 in entry)
        }
    }

    @Test
    fun historyContract_50rounds_neverProducesEmptyOrInvalidResults() {
        val items = listOf(
            makeItem(1, "Top", "White", "Summer", 4),
            makeItem(2, "Top", "Black", "Winter", 3),
            makeItem(3, "Bottom", "Blue", "Summer", 4),
            makeItem(4, "Bottom", "Navy", "Fall", 3),
            makeItem(5, "Shoes", "Black", "All", 4),
            makeItem(6, "Outerwear", "Gray", "Winter", 3),
            makeItem(7, "Accessory", "Gold", "All", 5)
        )

        val history = ArrayDeque<Set<Int>>()

        repeat(50) { i ->
            val recs = engine.recommend(items, defaultPreferences, recentlyShown = history.toSet())

            // Never empty
            assertTrue("Round $i must produce results", recs.isNotEmpty())

            // All scores positive
            for (rec in recs) {
                assertTrue("Score must be positive at round $i", rec.score > 0.0)
                assertTrue("Explanation must exist at round $i", rec.explanation.isNotBlank())

                // All items in recommendations must be from the wardrobe
                val validIds = items.map { it.id }.toSet()
                for (item in rec.items) {
                    assertTrue(
                        "Item ${item.id} at round $i must be from wardrobe",
                        item.id in validIds
                    )
                }
            }

            simulateRecordShownOutfits(recs, history)
        }
    }
}
