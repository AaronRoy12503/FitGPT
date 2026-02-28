package com.fitgpt.app

import com.fitgpt.app.ai.OutfitRecommendationEngine
import com.fitgpt.app.data.model.ClothingItem
import com.fitgpt.app.data.model.TemperatureCategory
import com.fitgpt.app.data.model.UserPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TemperatureRecommendationTest {

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
    // TemperatureCategory.fromCelsius()
    // -----------------------------------------------------------------------

    @Test
    fun fromCelsius_negativeTen_returnsCold() {
        assertEquals(TemperatureCategory.COLD, TemperatureCategory.fromCelsius(-10))
    }

    @Test
    fun fromCelsius_four_returnsCold() {
        assertEquals(TemperatureCategory.COLD, TemperatureCategory.fromCelsius(4))
    }

    @Test
    fun fromCelsius_five_returnsCool() {
        assertEquals(TemperatureCategory.COOL, TemperatureCategory.fromCelsius(5))
    }

    @Test
    fun fromCelsius_fourteen_returnsCool() {
        assertEquals(TemperatureCategory.COOL, TemperatureCategory.fromCelsius(14))
    }

    @Test
    fun fromCelsius_fifteen_returnsMild() {
        assertEquals(TemperatureCategory.MILD, TemperatureCategory.fromCelsius(15))
    }

    @Test
    fun fromCelsius_twentyTwo_returnsWarm() {
        assertEquals(TemperatureCategory.WARM, TemperatureCategory.fromCelsius(22))
    }

    @Test
    fun fromCelsius_thirty_returnsHot() {
        assertEquals(TemperatureCategory.HOT, TemperatureCategory.fromCelsius(30))
    }

    // -----------------------------------------------------------------------
    // TemperatureCategory.seasonSuitability()
    // -----------------------------------------------------------------------

    @Test
    fun seasonSuitability_coldWinter_returns1() {
        assertEquals(1.0, TemperatureCategory.COLD.seasonSuitability("Winter"), 0.001)
    }

    @Test
    fun seasonSuitability_coldSummer_returns0() {
        assertEquals(0.0, TemperatureCategory.COLD.seasonSuitability("Summer"), 0.001)
    }

    @Test
    fun seasonSuitability_hotSummer_returns1() {
        assertEquals(1.0, TemperatureCategory.HOT.seasonSuitability("Summer"), 0.001)
    }

    @Test
    fun seasonSuitability_hotWinter_returns0() {
        assertEquals(0.0, TemperatureCategory.HOT.seasonSuitability("Winter"), 0.001)
    }

    @Test
    fun seasonSuitability_mildAll_returns0point9() {
        assertEquals(0.9, TemperatureCategory.MILD.seasonSuitability("All"), 0.001)
    }

    @Test
    fun seasonSuitability_warmSpring_returns0point6() {
        assertEquals(0.6, TemperatureCategory.WARM.seasonSuitability("Spring"), 0.001)
    }

    @Test
    fun seasonSuitability_coolFall_returns1() {
        assertEquals(1.0, TemperatureCategory.COOL.seasonSuitability("Fall"), 0.001)
    }

    @Test
    fun seasonSuitability_caseInsensitive() {
        assertEquals(1.0, TemperatureCategory.COLD.seasonSuitability("winter"), 0.001)
        assertEquals(1.0, TemperatureCategory.COLD.seasonSuitability("WINTER"), 0.001)
    }

    // -----------------------------------------------------------------------
    // temperatureSeasonScore()
    // -----------------------------------------------------------------------

    @Test
    fun temperatureSeasonScore_nullCategory_returns1() {
        val item = ClothingItem(1, "Top", "Black", "Summer", 3)
        assertEquals(1.0, engine.temperatureSeasonScore(item, null), 0.001)
    }

    @Test
    fun temperatureSeasonScore_hotSummer_returns1() {
        val item = ClothingItem(1, "Top", "Black", "Summer", 3)
        assertEquals(1.0, engine.temperatureSeasonScore(item, TemperatureCategory.HOT), 0.001)
    }

    @Test
    fun temperatureSeasonScore_hotWinter_returnsFloor() {
        val item = ClothingItem(1, "Top", "Black", "Winter", 3)
        assertEquals(0.05, engine.temperatureSeasonScore(item, TemperatureCategory.HOT), 0.001)
    }

    @Test
    fun temperatureSeasonScore_coldAll_returns0point7() {
        val item = ClothingItem(1, "Top", "Black", "All", 3)
        assertEquals(0.7, engine.temperatureSeasonScore(item, TemperatureCategory.COLD), 0.001)
    }

    @Test
    fun temperatureSeasonScore_mildSpring_returns1() {
        val item = ClothingItem(1, "Top", "Black", "Spring", 3)
        assertEquals(1.0, engine.temperatureSeasonScore(item, TemperatureCategory.MILD), 0.001)
    }

    // -----------------------------------------------------------------------
    // temperatureComfortBonus()
    // -----------------------------------------------------------------------

    @Test
    fun temperatureComfortBonus_nullCategory_returns0() {
        val outfit = listOf(ClothingItem(1, "Top", "Black", "Summer", 5))
        assertEquals(0.0, engine.temperatureComfortBonus(outfit, null), 0.001)
    }

    @Test
    fun temperatureComfortBonus_mildCategory_returns0() {
        val outfit = listOf(ClothingItem(1, "Top", "Black", "Summer", 5))
        assertEquals(0.0, engine.temperatureComfortBonus(outfit, TemperatureCategory.MILD), 0.001)
    }

    @Test
    fun temperatureComfortBonus_emptyOutfit_returns0() {
        assertEquals(0.0, engine.temperatureComfortBonus(emptyList(), TemperatureCategory.HOT), 0.001)
    }

    @Test
    fun temperatureComfortBonus_hotHighComfort_returnsHigh() {
        val outfit = listOf(
            ClothingItem(1, "Top", "White", "Summer", 5),
            ClothingItem(2, "Bottom", "Blue", "Summer", 5)
        )
        // avgComfort=5, (5-1)/4 = 1.0
        assertEquals(1.0, engine.temperatureComfortBonus(outfit, TemperatureCategory.HOT), 0.001)
    }

    @Test
    fun temperatureComfortBonus_coldLowComfort_returnsLow() {
        val outfit = listOf(
            ClothingItem(1, "Top", "Black", "Winter", 1),
            ClothingItem(2, "Bottom", "Navy", "Winter", 1)
        )
        // avgComfort=1, (1-1)/4 = 0.0
        assertEquals(0.0, engine.temperatureComfortBonus(outfit, TemperatureCategory.COLD), 0.001)
    }

    @Test
    fun temperatureComfortBonus_hotMixedComfort_returnsProportional() {
        val outfit = listOf(
            ClothingItem(1, "Top", "White", "Summer", 5),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3)
        )
        // avgComfort=4, (4-1)/4 = 0.75
        assertEquals(0.75, engine.temperatureComfortBonus(outfit, TemperatureCategory.HOT), 0.001)
    }

    // -----------------------------------------------------------------------
    // scoreItem with temperature
    // -----------------------------------------------------------------------

    @Test
    fun scoreItem_nullTemp_sameAsWithout() {
        val item = ClothingItem(1, "Top", "Black", "Summer", 3)
        val withNull = engine.scoreItem(item, defaultPreferences, null)
        val without = engine.scoreItem(item, defaultPreferences)
        assertEquals(without, withNull, 0.001)
    }

    @Test
    fun scoreItem_hotSummer_unchanged() {
        val item = ClothingItem(1, "Top", "Black", "Summer", 3)
        val withoutTemp = engine.scoreItem(item, defaultPreferences)
        val withTemp = engine.scoreItem(item, defaultPreferences, TemperatureCategory.HOT)
        // HOT+Summer suitability = 1.0, so score is unchanged
        assertEquals(withoutTemp, withTemp, 0.001)
    }

    @Test
    fun scoreItem_hotWinter_reducedTo5Percent() {
        val item = ClothingItem(1, "Top", "Black", "Winter", 3)
        val withoutTemp = engine.scoreItem(item, defaultPreferences)
        val withTemp = engine.scoreItem(item, defaultPreferences, TemperatureCategory.HOT)
        // HOT+Winter suitability = 0.0 → floor 0.05
        assertEquals(withoutTemp * 0.05, withTemp, 0.001)
    }

    @Test
    fun scoreItem_coldAll_reducedTo70Percent() {
        val item = ClothingItem(1, "Top", "Black", "All", 3)
        val withoutTemp = engine.scoreItem(item, defaultPreferences)
        val withTemp = engine.scoreItem(item, defaultPreferences, TemperatureCategory.COLD)
        // COLD+All suitability = 0.7
        assertEquals(withoutTemp * 0.7, withTemp, 0.001)
    }

    // -----------------------------------------------------------------------
    // Engine integration
    // -----------------------------------------------------------------------

    @Test
    fun recommend_nullTemp_defaultBehavior() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 4),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3)
        )
        val withNull = engine.recommend(items, defaultPreferences, temperatureCategory = null)
        val without = engine.recommend(items, defaultPreferences)
        assertEquals(without.size, withNull.size)
    }

    @Test
    fun recommend_withTemp_producesResults() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 4),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3)
        )
        val result = engine.recommend(items, defaultPreferences, temperatureCategory = TemperatureCategory.HOT)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun recommend_hotTemp_ranksSummerOverWinter() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 3),
            ClothingItem(2, "Top", "Black", "Winter", 3),
            ClothingItem(3, "Bottom", "Blue", "Summer", 3),
            ClothingItem(4, "Bottom", "Navy", "Winter", 3)
        )
        val result = engine.recommend(items, defaultPreferences, temperatureCategory = TemperatureCategory.HOT)
        assertTrue(result.isNotEmpty())
        val topOutfit = result[0]
        val summerCount = topOutfit.items.count { it.season == "Summer" }
        val winterCount = topOutfit.items.count { it.season == "Winter" }
        assertTrue("HOT should rank summer items above winter", summerCount > winterCount)
    }

    @Test
    fun recommend_coldTemp_ranksWinterOverSummer() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 3),
            ClothingItem(2, "Top", "Black", "Winter", 3),
            ClothingItem(3, "Bottom", "Blue", "Summer", 3),
            ClothingItem(4, "Bottom", "Navy", "Winter", 3)
        )
        val prefs = defaultPreferences.copy(preferredSeasons = listOf("Winter", "Summer"))
        val result = engine.recommend(items, prefs, temperatureCategory = TemperatureCategory.COLD)
        assertTrue(result.isNotEmpty())
        val topOutfit = result[0]
        val winterCount = topOutfit.items.count { it.season == "Winter" }
        val summerCount = topOutfit.items.count { it.season == "Summer" }
        assertTrue("COLD should rank winter items above summer", winterCount > summerCount)
    }

    @Test
    fun recommend_allMismatchedItems_stillReturnsResults() {
        // Only winter items but HOT temperature — soft filtering should still produce results
        val items = listOf(
            ClothingItem(1, "Top", "Black", "Winter", 3),
            ClothingItem(2, "Bottom", "Navy", "Winter", 3)
        )
        val result = engine.recommend(items, defaultPreferences, temperatureCategory = TemperatureCategory.HOT)
        assertTrue("Should return results even when all items are mismatched", result.isNotEmpty())
        assertTrue("Mismatched items should still have positive scores", result[0].score > 0.0)
    }

    @Test
    fun recommend_comfortBonusBoostsHighComfort() {
        // In HOT weather, high-comfort summer items should score higher than low-comfort ones
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 5),
            ClothingItem(2, "Top", "Blue", "Summer", 1),
            ClothingItem(3, "Bottom", "Beige", "Summer", 5),
            ClothingItem(4, "Bottom", "Gray", "Summer", 1)
        )
        val result = engine.recommend(items, defaultPreferences, temperatureCategory = TemperatureCategory.HOT)
        assertTrue(result.isNotEmpty())
        val topOutfit = result[0]
        val avgComfort = topOutfit.items.sumOf { it.comfortLevel }.toDouble() / topOutfit.items.size
        assertTrue("High comfort items should be ranked higher in HOT weather", avgComfort >= 3.0)
    }

    // -----------------------------------------------------------------------
    // Combined contexts
    // -----------------------------------------------------------------------

    @Test
    fun recommend_tempAndTime_bothApplied() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 4),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3),
            ClothingItem(3, "Shoes", "White", "All", 4)
        )
        val result = engine.recommend(
            items, defaultPreferences,
            timeCategory = com.fitgpt.app.data.model.TimeCategory.MORNING,
            temperatureCategory = TemperatureCategory.HOT
        )
        assertTrue(result.isNotEmpty())
        assertTrue(result[0].score > 0.0)
    }

    @Test
    fun recommend_tempAndPlanner_bothApplied() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 4),
            ClothingItem(2, "Top", "Blue", "Summer", 3),
            ClothingItem(3, "Bottom", "Beige", "Summer", 4),
            ClothingItem(4, "Bottom", "Gray", "Summer", 3)
        )
        val plannedIds = setOf(1, 3)
        val withPlanner = engine.recommend(
            items, defaultPreferences,
            plannedItemIds = plannedIds,
            temperatureCategory = TemperatureCategory.HOT
        )
        val withoutPlanner = engine.recommend(
            items, defaultPreferences,
            temperatureCategory = TemperatureCategory.HOT
        )
        // Planner penalty should still apply: planned items should score lower
        assertTrue(withPlanner.isNotEmpty())
        assertTrue(withoutPlanner.isNotEmpty())
    }

    @Test
    fun recommend_tempAndOverlap_bothApplied() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 4),
            ClothingItem(2, "Top", "Blue", "Summer", 3),
            ClothingItem(3, "Bottom", "Beige", "Summer", 4),
            ClothingItem(4, "Bottom", "Gray", "Summer", 3)
        )
        val shown = setOf(setOf(1, 3))
        val result = engine.recommend(
            items, defaultPreferences,
            recentlyShown = shown,
            temperatureCategory = TemperatureCategory.WARM
        )
        assertTrue(result.isNotEmpty())
        // The exact shown combo should be excluded
        for (rec in result) {
            val key = rec.items.map { it.id }.toSet()
            assertFalse("Recently shown should still be excluded with temp", key in shown)
        }
    }

    @Test
    fun recommend_allFourContexts_combined() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 4),
            ClothingItem(2, "Top", "Blue", "Summer", 3),
            ClothingItem(3, "Bottom", "Beige", "Summer", 4),
            ClothingItem(4, "Bottom", "Gray", "Summer", 3),
            ClothingItem(5, "Shoes", "White", "All", 4)
        )
        val result = engine.recommend(
            items, defaultPreferences,
            recentlyShown = setOf(setOf(1, 3)),
            plannedItemIds = setOf(2),
            timeCategory = com.fitgpt.app.data.model.TimeCategory.AFTERNOON,
            temperatureCategory = TemperatureCategory.WARM
        )
        assertTrue(result.isNotEmpty())
        assertTrue(result[0].score > 0.0)
    }

    // -----------------------------------------------------------------------
    // Explanation
    // -----------------------------------------------------------------------

    @Test
    fun explanation_hotTemp_mentionsHeat() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 4),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3)
        )
        val result = engine.recommend(items, defaultPreferences, temperatureCategory = TemperatureCategory.HOT)
        assertTrue(result.isNotEmpty())
        assertTrue(
            "HOT explanation should mention heat",
            result[0].explanation.contains("heat", ignoreCase = true)
        )
    }

    @Test
    fun explanation_coldTemp_mentionsCold() {
        val items = listOf(
            ClothingItem(1, "Top", "Black", "Winter", 4),
            ClothingItem(2, "Bottom", "Navy", "Winter", 3)
        )
        val prefs = defaultPreferences.copy(preferredSeasons = listOf("Winter"))
        val result = engine.recommend(items, prefs, temperatureCategory = TemperatureCategory.COLD)
        assertTrue(result.isNotEmpty())
        assertTrue(
            "COLD explanation should mention cold",
            result[0].explanation.contains("cold", ignoreCase = true)
        )
    }

    // -----------------------------------------------------------------------
    // ViewModel integration
    // -----------------------------------------------------------------------

    @Test
    fun viewModel_nullProvider_works() {
        // WardrobeViewModel with null temperatureProvider should not crash
        // and should produce recommendations (tested via engine directly since
        // ViewModel requires Android context)
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 4),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3)
        )
        val result = engine.recommend(items, defaultPreferences, temperatureCategory = null)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun viewModel_providerFlowsThrough() {
        // Simulate what ViewModel does: convert provider result to TemperatureCategory
        val temperatureProvider: (() -> Int) = { 35 }
        val temperatureCategory = TemperatureCategory.fromCelsius(temperatureProvider())
        assertEquals(TemperatureCategory.HOT, temperatureCategory)

        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 4),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3)
        )
        val result = engine.recommend(items, defaultPreferences, temperatureCategory = temperatureCategory)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun viewModel_differentTemps_differentScores() {
        val items = listOf(
            ClothingItem(1, "Top", "Black", "Winter", 3),
            ClothingItem(2, "Bottom", "Navy", "Winter", 3)
        )
        val hotResult = engine.recommend(items, defaultPreferences, temperatureCategory = TemperatureCategory.HOT)
        val coldResult = engine.recommend(items, defaultPreferences, temperatureCategory = TemperatureCategory.COLD)
        assertTrue(hotResult.isNotEmpty())
        assertTrue(coldResult.isNotEmpty())
        // Winter items should score much higher in COLD than HOT
        assertTrue(
            "Winter items should score higher in COLD than HOT",
            coldResult[0].score > hotResult[0].score
        )
    }

    // -----------------------------------------------------------------------
    // TemperatureCategory properties
    // -----------------------------------------------------------------------

    @Test
    fun temperatureCategory_labels() {
        assertEquals("cold", TemperatureCategory.COLD.label)
        assertEquals("cool", TemperatureCategory.COOL.label)
        assertEquals("mild", TemperatureCategory.MILD.label)
        assertEquals("warm", TemperatureCategory.WARM.label)
        assertEquals("hot", TemperatureCategory.HOT.label)
    }

    @Test
    fun temperatureCategory_isExtreme() {
        assertTrue(TemperatureCategory.COLD.isExtreme)
        assertFalse(TemperatureCategory.COOL.isExtreme)
        assertFalse(TemperatureCategory.MILD.isExtreme)
        assertFalse(TemperatureCategory.WARM.isExtreme)
        assertTrue(TemperatureCategory.HOT.isExtreme)
    }

    @Test
    fun seasonSuitability_unknownSeason_returnsDefault() {
        assertEquals(0.5, TemperatureCategory.COLD.seasonSuitability("Monsoon"), 0.001)
        assertEquals(0.5, TemperatureCategory.HOT.seasonSuitability("Rainy"), 0.001)
    }

    // -----------------------------------------------------------------------
    // Weight constants
    // -----------------------------------------------------------------------

    @Test
    fun weightTemperature_isCorrect() {
        assertEquals(0.10, OutfitRecommendationEngine.WEIGHT_TEMPERATURE, 0.001)
    }

    @Test
    fun temperatureSuitabilityFloor_isCorrect() {
        assertEquals(0.05, OutfitRecommendationEngine.TEMPERATURE_SUITABILITY_FLOOR, 0.001)
    }

    @Test
    fun baseWeights_stillSumToOne() {
        // The 6 base weights should still sum to 1.0 (WEIGHT_TEMPERATURE is additive, not part of base)
        val totalWeight = OutfitRecommendationEngine.WEIGHT_SEASON +
            OutfitRecommendationEngine.WEIGHT_COMFORT +
            OutfitRecommendationEngine.WEIGHT_STYLE +
            OutfitRecommendationEngine.WEIGHT_FIT +
            OutfitRecommendationEngine.WEIGHT_HARMONY +
            OutfitRecommendationEngine.WEIGHT_COVERAGE
        assertEquals(1.0, totalWeight, 0.001)
    }
}
