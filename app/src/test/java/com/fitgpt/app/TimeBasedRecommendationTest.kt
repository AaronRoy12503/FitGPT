package com.fitgpt.app

import com.fitgpt.app.ai.OutfitRecommendationEngine
import com.fitgpt.app.data.model.ClothingItem
import com.fitgpt.app.data.model.TimeCategory
import com.fitgpt.app.data.model.UserPreferences
import com.fitgpt.app.viewmodel.WardrobeViewModel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TimeBasedRecommendationTest {

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

    // ===================================================================
    // TimeCategory.fromHour() — 8 tests
    // ===================================================================

    @Test
    fun fromHour_6_isMorning() {
        assertEquals(TimeCategory.MORNING, TimeCategory.fromHour(6))
    }

    @Test
    fun fromHour_11_isMorning() {
        assertEquals(TimeCategory.MORNING, TimeCategory.fromHour(11))
    }

    @Test
    fun fromHour_12_isAfternoon() {
        assertEquals(TimeCategory.AFTERNOON, TimeCategory.fromHour(12))
    }

    @Test
    fun fromHour_16_isAfternoon() {
        assertEquals(TimeCategory.AFTERNOON, TimeCategory.fromHour(16))
    }

    @Test
    fun fromHour_17_isEvening() {
        assertEquals(TimeCategory.EVENING, TimeCategory.fromHour(17))
    }

    @Test
    fun fromHour_20_isEvening() {
        assertEquals(TimeCategory.EVENING, TimeCategory.fromHour(20))
    }

    @Test
    fun fromHour_21_isNight() {
        assertEquals(TimeCategory.NIGHT, TimeCategory.fromHour(21))
    }

    @Test
    fun fromHour_5_isNight() {
        assertEquals(TimeCategory.NIGHT, TimeCategory.fromHour(5))
    }

    // ===================================================================
    // inferFormality() — 8 tests
    // ===================================================================

    @Test
    fun inferFormality_outerwearFittedBlack_is5() {
        // Base 4 (outerwear) + 1 (fitted) + 1 (black) = 6, clamped to 5
        val item = ClothingItem(1, "Outerwear", "Black", "Winter", 3, fit = "Fitted")
        assertEquals(5, engine.inferFormality(item))
    }

    @Test
    fun inferFormality_accessoryRelaxedYellow_is1() {
        // Base 2 (accessory) + (-1) (relaxed) + (-1) (yellow) = 0, clamped to 1
        val item = ClothingItem(2, "Accessory", "Yellow", "Summer", 5, fit = "Relaxed")
        assertEquals(1, engine.inferFormality(item))
    }

    @Test
    fun inferFormality_topRegularNavy_is4() {
        // Base 3 (top) + 0 (regular) + 1 (navy) = 4
        val item = ClothingItem(3, "Top", "Navy", "All", 3, fit = "Regular")
        assertEquals(4, engine.inferFormality(item))
    }

    @Test
    fun inferFormality_bottomOversizedPink_is1() {
        // Base 3 (bottom) + (-1) (oversized) + (-1) (pink) = 1
        val item = ClothingItem(4, "Bottom", "Pink", "Spring", 4, fit = "Oversized")
        assertEquals(1, engine.inferFormality(item))
    }

    @Test
    fun inferFormality_shoesRegularWhite_is3() {
        // Base 3 (shoes) + 0 (regular) + 0 (white is neutral, not formal/casual) = 3
        val item = ClothingItem(5, "Shoes", "White", "All", 4, fit = "Regular")
        assertEquals(3, engine.inferFormality(item))
    }

    @Test
    fun inferFormality_topFittedGray_is5() {
        // Base 3 (top) + 1 (fitted) + 1 (gray) = 5
        val item = ClothingItem(6, "Top", "Gray", "Fall", 3, fit = "Fitted")
        assertEquals(5, engine.inferFormality(item))
    }

    @Test
    fun inferFormality_outerwearOversizedOrange_is2() {
        // Base 4 (outerwear) + (-1) (oversized) + (-1) (orange) = 2
        val item = ClothingItem(7, "Outerwear", "Orange", "Spring", 3, fit = "Oversized")
        assertEquals(2, engine.inferFormality(item))
    }

    @Test
    fun inferFormality_accessoryFittedGrey_is4() {
        // Base 2 (accessory) + 1 (fitted) + 1 (grey) = 4
        val item = ClothingItem(8, "Accessory", "Grey", "All", 3, fit = "Fitted")
        assertEquals(4, engine.inferFormality(item))
    }

    // ===================================================================
    // timeContextScore() — 8 tests
    // ===================================================================

    @Test
    fun timeContextScore_nullTimeCategory_returnsZero() {
        val outfit = listOf(ClothingItem(1, "Top", "Black", "Summer", 3))
        assertEquals(0.0, engine.timeContextScore(outfit, null), 0.001)
    }

    @Test
    fun timeContextScore_emptyOutfit_returnsZero() {
        assertEquals(0.0, engine.timeContextScore(emptyList(), TimeCategory.MORNING), 0.001)
    }

    @Test
    fun timeContextScore_casualOutfitMorning_highScore() {
        // Casual items → low formality → matches MORNING (ideal 1–2)
        val outfit = listOf(
            ClothingItem(1, "Top", "Yellow", "Summer", 5, fit = "Relaxed"),     // formality 1
            ClothingItem(2, "Bottom", "Pink", "Summer", 4, fit = "Oversized")   // formality 1
        )
        val score = engine.timeContextScore(outfit, TimeCategory.MORNING)
        assertEquals(1.0, score, 0.001) // avg formality 1.0, in range [1,2]
    }

    @Test
    fun timeContextScore_formalOutfitEvening_highScore() {
        // Formal items → high formality → matches EVENING (ideal 3–5)
        val outfit = listOf(
            ClothingItem(1, "Outerwear", "Black", "Winter", 3, fit = "Fitted"),  // formality 5
            ClothingItem(2, "Top", "Navy", "All", 3, fit = "Fitted")             // formality 5
        )
        val score = engine.timeContextScore(outfit, TimeCategory.EVENING)
        assertEquals(1.0, score, 0.001) // avg formality 5.0, in range [3,5]
    }

    @Test
    fun timeContextScore_formalOutfitMorning_lowScore() {
        // Formal items at morning time → mismatch
        val outfit = listOf(
            ClothingItem(1, "Outerwear", "Black", "Winter", 3, fit = "Fitted"),  // formality 5
            ClothingItem(2, "Top", "Navy", "All", 3, fit = "Fitted")             // formality 5
        )
        val score = engine.timeContextScore(outfit, TimeCategory.MORNING)
        // avg formality 5.0, MORNING ideal [1,2], distance = 3, score = 1.0 - 0.75 = 0.25
        assertEquals(0.25, score, 0.001)
    }

    @Test
    fun timeContextScore_casualOutfitEvening_lowScore() {
        // Casual items at evening → mismatch
        val outfit = listOf(
            ClothingItem(1, "Top", "Yellow", "Summer", 5, fit = "Relaxed"),     // formality 1
            ClothingItem(2, "Bottom", "Pink", "Summer", 4, fit = "Oversized")   // formality 1
        )
        val score = engine.timeContextScore(outfit, TimeCategory.EVENING)
        // avg formality 1.0, EVENING ideal [3,5], distance = 2, score = 1.0 - 0.50 = 0.50
        assertEquals(0.50, score, 0.001)
    }

    @Test
    fun timeContextScore_midFormalityAfternoon_highScore() {
        // Mid-formality items → matches AFTERNOON (ideal 2–4)
        val outfit = listOf(
            ClothingItem(1, "Top", "White", "Summer", 3, fit = "Regular"),   // formality 3
            ClothingItem(2, "Bottom", "Blue", "Summer", 3, fit = "Regular")  // formality 3
        )
        val score = engine.timeContextScore(outfit, TimeCategory.AFTERNOON)
        assertEquals(1.0, score, 0.001) // avg 3.0, in range [2,4]
    }

    @Test
    fun timeContextScore_nightCasual_highScore() {
        // Casual → matches NIGHT (ideal 1–2)
        val outfit = listOf(
            ClothingItem(1, "Accessory", "Yellow", "All", 5, fit = "Relaxed"),   // formality 1
            ClothingItem(2, "Bottom", "Pink", "Summer", 5, fit = "Oversized")    // formality 1
        )
        val score = engine.timeContextScore(outfit, TimeCategory.NIGHT)
        assertEquals(1.0, score, 0.001) // avg 1.0, in range [1,2]
    }

    // ===================================================================
    // Engine integration — 5 tests
    // ===================================================================

    @Test
    fun recommend_withTimeCategory_producesResults() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 4),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3)
        )
        val result = engine.recommend(items, defaultPreferences, timeCategory = TimeCategory.MORNING)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun recommend_morningRanksCasualHigher() {
        // Verify that morning time bonus boosts casual outfits relative to no-time baseline
        val casualTop = ClothingItem(1, "Top", "Yellow", "Summer", 5, fit = "Relaxed")
        val bottom = ClothingItem(3, "Bottom", "Pink", "Summer", 4, fit = "Oversized")

        val items = listOf(casualTop, bottom)
        val withMorning = engine.recommend(items, defaultPreferences, timeCategory = TimeCategory.MORNING)
        val withoutTime = engine.recommend(items, defaultPreferences, timeCategory = null)

        assertTrue(withMorning.isNotEmpty())
        assertTrue(withoutTime.isNotEmpty())
        // Casual outfit + MORNING should get a positive time bonus
        assertTrue(
            "Morning should boost casual outfit score",
            withMorning[0].score > withoutTime[0].score
        )
    }

    @Test
    fun recommend_eveningRanksFormalHigher() {
        // Verify that evening time bonus boosts formal outfits relative to no-time baseline
        val formalTop = ClothingItem(2, "Top", "Navy", "Summer", 3, fit = "Fitted")
        val bottom = ClothingItem(3, "Bottom", "Black", "Summer", 3, fit = "Fitted")

        val items = listOf(formalTop, bottom)
        val withEvening = engine.recommend(items, defaultPreferences, timeCategory = TimeCategory.EVENING)
        val withoutTime = engine.recommend(items, defaultPreferences, timeCategory = null)

        assertTrue(withEvening.isNotEmpty())
        assertTrue(withoutTime.isNotEmpty())
        // Formal outfit + EVENING should get a positive time bonus
        assertTrue(
            "Evening should boost formal outfit score",
            withEvening[0].score > withoutTime[0].score
        )
    }

    @Test
    fun recommend_nullTimeCategory_sameAsDefault() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 4),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3)
        )
        val withNull = engine.recommend(items, defaultPreferences, timeCategory = null)
        val withDefault = engine.recommend(items, defaultPreferences)

        assertEquals(withNull.size, withDefault.size)
        for (i in withNull.indices) {
            assertEquals(withNull[i].score, withDefault[i].score, 0.001)
        }
    }

    @Test
    fun recommend_timeBonus_increasesScore() {
        val items = listOf(
            ClothingItem(1, "Top", "Yellow", "Summer", 5, fit = "Relaxed"),
            ClothingItem(2, "Bottom", "Pink", "Summer", 4, fit = "Oversized")
        )
        val withoutTime = engine.recommend(items, defaultPreferences, timeCategory = null)
        val withTime = engine.recommend(items, defaultPreferences, timeCategory = TimeCategory.MORNING)

        assertTrue(withoutTime.isNotEmpty())
        assertTrue(withTime.isNotEmpty())
        // Casual outfit + MORNING = good match, so score should be higher with time
        assertTrue(
            "Time bonus should increase score for matching outfits",
            withTime[0].score >= withoutTime[0].score
        )
    }

    // ===================================================================
    // Combined contexts — 5 tests
    // ===================================================================

    @Test
    fun recommend_timePlusSeason_producesResults() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 4),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3)
        )
        val prefs = defaultPreferences.copy(preferredSeasons = listOf("Summer"))
        val result = engine.recommend(items, prefs, timeCategory = TimeCategory.AFTERNOON)
        assertTrue(result.isNotEmpty())
        assertTrue(result[0].score > 0.0)
    }

    @Test
    fun recommend_timePlusPlanner_producesResults() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 4),
            ClothingItem(2, "Top", "Black", "Summer", 3),
            ClothingItem(3, "Bottom", "Blue", "Summer", 3)
        )
        val result = engine.recommend(
            items, defaultPreferences,
            plannedItemIds = setOf(1),
            timeCategory = TimeCategory.MORNING
        )
        assertTrue(result.isNotEmpty())
        assertTrue(result[0].score > 0.0)
    }

    @Test
    fun recommend_timePlusOverlap_producesResults() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 4),
            ClothingItem(2, "Top", "Black", "Summer", 3),
            ClothingItem(3, "Bottom", "Blue", "Summer", 3)
        )
        val shown = setOf(setOf(1, 3))
        val result = engine.recommend(
            items, defaultPreferences,
            recentlyShown = shown,
            timeCategory = TimeCategory.EVENING
        )
        assertTrue(result.isNotEmpty())
        assertTrue(result[0].score > 0.0)
    }

    @Test
    fun recommend_allThreeContexts_producesResults() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 4),
            ClothingItem(2, "Top", "Black", "Summer", 3),
            ClothingItem(3, "Bottom", "Blue", "Summer", 3),
            ClothingItem(4, "Bottom", "Gray", "Summer", 3)
        )
        val shown = setOf(setOf(1, 3))
        val result = engine.recommend(
            items, defaultPreferences,
            recentlyShown = shown,
            plannedItemIds = setOf(1),
            timeCategory = TimeCategory.AFTERNOON
        )
        assertTrue(result.isNotEmpty())
        assertTrue(result[0].score > 0.0)
    }

    @Test
    fun recommend_allContexts_scoresStayAboveMinimum() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 4),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3)
        )
        val shown = setOf(setOf(2, 3)) // partial overlap
        val result = engine.recommend(
            items, defaultPreferences,
            recentlyShown = shown,
            plannedItemIds = setOf(1),
            timeCategory = TimeCategory.NIGHT
        )
        assertTrue(result.isNotEmpty())
        result.forEach { rec ->
            assertTrue(
                "Score should never drop below 0.01",
                rec.score >= 0.01
            )
        }
    }

    // ===================================================================
    // Conflict cap — 4 tests
    // ===================================================================

    @Test
    fun conflictCap_preventsExcessiveStacking() {
        // Create scenario with max overlap AND max planner penalty
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 4),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3)
        )
        // All items are planned and the combo was recently shown
        val shown = setOf(setOf(1, 2))
        val result = engine.recommend(
            items, defaultPreferences,
            recentlyShown = shown,
            plannedItemIds = setOf(1, 2),
            timeCategory = TimeCategory.MORNING
        )
        assertTrue(result.isNotEmpty())
        result.forEach { rec ->
            assertTrue("Score floor should hold at 0.01", rec.score >= 0.01)
        }
    }

    @Test
    fun conflictCap_noTimeCategory_currentBehavior() {
        // Without time, the formula should behave identically to the old one
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 4),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3)
        )
        val withNullTime = engine.recommend(items, defaultPreferences, timeCategory = null)
        val withoutParam = engine.recommend(items, defaultPreferences)

        assertEquals(withNullTime.size, withoutParam.size)
        for (i in withNullTime.indices) {
            assertEquals(withNullTime[i].score, withoutParam[i].score, 0.001)
        }
    }

    @Test
    fun conflictCap_maxPenalties_hitsFloor() {
        // Force maximum penalties: all items planned + full overlap
        val items = listOf(
            ClothingItem(1, "Top", "Pink", "Winter", 1, fit = "Oversized"),
            ClothingItem(2, "Bottom", "Orange", "Winter", 1, fit = "Oversized")
        )
        val prefs = defaultPreferences.copy(
            comfortPreference = 5,
            preferredSeasons = listOf("Summer")
        )
        // Mark combo as shown (will fall through to graceful reset) + plan all items
        val shown = setOf(setOf(1, 2))
        val result = engine.recommend(
            items, prefs,
            recentlyShown = shown,
            plannedItemIds = setOf(1, 2),
            timeCategory = TimeCategory.EVENING // worst mismatch for casual items
        )
        assertTrue(result.isNotEmpty())
        result.forEach { rec ->
            assertTrue(
                "Even with max penalties, score should not drop below 0.01",
                rec.score >= 0.01
            )
        }
    }

    @Test
    fun conflictCap_timeBonusPartiallyOffsetsPenalties() {
        val items = listOf(
            ClothingItem(1, "Top", "Yellow", "Summer", 5, fit = "Relaxed"),
            ClothingItem(2, "Bottom", "Pink", "Summer", 4, fit = "Oversized")
        )
        // Apply planner penalty
        val withPenalty = engine.recommend(
            items, defaultPreferences,
            plannedItemIds = setOf(1),
            timeCategory = TimeCategory.MORNING // good match for casual
        )
        val withPenaltyNoTime = engine.recommend(
            items, defaultPreferences,
            plannedItemIds = setOf(1),
            timeCategory = null
        )
        assertTrue(withPenalty.isNotEmpty())
        assertTrue(withPenaltyNoTime.isNotEmpty())
        assertTrue(
            "Time bonus should partially offset planner penalty",
            withPenalty[0].score >= withPenaltyNoTime[0].score
        )
    }

    // ===================================================================
    // ViewModel integration — 3 tests
    // ===================================================================

    @Test
    fun viewModel_hourProviderFlowsThrough() {
        // Morning hour → should produce recommendations
        val vm = WardrobeViewModel(hourProvider = { 8 })
        val recs = vm.recommendations.value
        assertTrue(recs.isNotEmpty())
    }

    @Test
    fun viewModel_differentHoursProduceDifferentScores() {
        val vmMorning = WardrobeViewModel(hourProvider = { 7 })
        val vmEvening = WardrobeViewModel(hourProvider = { 19 })

        val morningScores = vmMorning.recommendations.value.map { it.score }
        val eveningScores = vmEvening.recommendations.value.map { it.score }

        // Scores should differ because time bonus varies
        assertNotEquals(
            "Morning and evening should produce different scores",
            morningScores,
            eveningScores
        )
    }

    @Test
    fun viewModel_defaultHourProvider_doesNotCrash() {
        val vm = WardrobeViewModel()
        val recs = vm.recommendations.value
        assertTrue(recs.isNotEmpty())
    }

    // ===================================================================
    // Backward compatibility — 2 tests
    // ===================================================================

    @Test
    fun backwardCompat_nullTime_zeroContribution() {
        val outfit = listOf(
            ClothingItem(1, "Top", "Black", "Summer", 3),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3)
        )
        val scoreWithNull = engine.timeContextScore(outfit, null)
        assertEquals(0.0, scoreWithNull, 0.001)
    }

    @Test
    fun backwardCompat_existingWeightSumUnchanged() {
        // The 6 base weights should still sum to 1.0
        val baseSum = OutfitRecommendationEngine.WEIGHT_SEASON +
            OutfitRecommendationEngine.WEIGHT_COMFORT +
            OutfitRecommendationEngine.WEIGHT_STYLE +
            OutfitRecommendationEngine.WEIGHT_FIT +
            OutfitRecommendationEngine.WEIGHT_HARMONY +
            OutfitRecommendationEngine.WEIGHT_COVERAGE
        assertEquals(1.0, baseSum, 0.001)
    }
}
