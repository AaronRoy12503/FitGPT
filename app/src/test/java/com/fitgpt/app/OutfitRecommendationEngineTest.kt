package com.fitgpt.app

import com.fitgpt.app.ai.OutfitRecommendationEngine
import com.fitgpt.app.data.model.ClothingItem
import com.fitgpt.app.data.model.UserPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class OutfitRecommendationEngineTest {

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
    // recommend() - empty and single-item edge cases
    // -----------------------------------------------------------------------

    @Test
    fun recommend_emptyWardrobe_returnsEmptyList() {
        val result = engine.recommend(emptyList(), defaultPreferences)
        assertTrue(result.isEmpty())
    }

    @Test
    fun recommend_singleItem_returnsSingleRecommendation() {
        val item = ClothingItem(1, "Top", "Black", "Summer", 3)
        val result = engine.recommend(listOf(item), defaultPreferences)

        assertEquals(1, result.size)
        assertEquals(1, result[0].items.size)
        assertEquals(item, result[0].items[0])
    }

    @Test
    fun recommend_singleItem_hasPositiveScore() {
        val item = ClothingItem(1, "Top", "Black", "Summer", 3)
        val result = engine.recommend(listOf(item), defaultPreferences)

        assertTrue(result[0].score > 0.0)
    }

    @Test
    fun recommend_singleItem_hasNonEmptyExplanation() {
        val item = ClothingItem(1, "Top", "Red", "Winter", 2)
        val result = engine.recommend(listOf(item), defaultPreferences)

        assertTrue(result[0].explanation.isNotBlank())
    }

    // -----------------------------------------------------------------------
    // recommend() - multi-item outfit combinations
    // -----------------------------------------------------------------------

    @Test
    fun recommend_topAndBottom_producesOutfitCombination() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 4),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3)
        )
        val result = engine.recommend(items, defaultPreferences)

        assertTrue(result.isNotEmpty())
        // Should have at least one outfit with both items
        val multiItemOutfit = result.find { it.items.size == 2 }
        assertNotNull(multiItemOutfit)
    }

    @Test
    fun recommend_noTopOrBottom_fallsBackToIndividualScoring() {
        val items = listOf(
            ClothingItem(1, "Shoes", "Black", "All", 4),
            ClothingItem(2, "Accessory", "Gold", "All", 5)
        )
        val result = engine.recommend(items, defaultPreferences)

        assertTrue(result.isNotEmpty())
        // Each recommendation should have a single item since no top+bottom combos exist
        result.forEach { assertEquals(1, it.items.size) }
    }

    @Test
    fun recommend_resultsAreSortedByScoreDescending() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 5),
            ClothingItem(2, "Top", "Black", "Winter", 1),
            ClothingItem(3, "Bottom", "Blue", "Summer", 3),
            ClothingItem(4, "Bottom", "Gray", "Winter", 2)
        )
        val result = engine.recommend(items, defaultPreferences)

        for (i in 0 until result.size - 1) {
            assertTrue(
                "Results should be sorted descending by score",
                result[i].score >= result[i + 1].score
            )
        }
    }

    @Test
    fun recommend_limitsToMaxFiveRecommendations() {
        // Create many items to generate many combinations
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 3),
            ClothingItem(2, "Top", "Black", "Winter", 4),
            ClothingItem(3, "Top", "Blue", "Spring", 5),
            ClothingItem(4, "Bottom", "Navy", "Summer", 3),
            ClothingItem(5, "Bottom", "Gray", "Fall", 4),
            ClothingItem(6, "Bottom", "Beige", "Spring", 5),
            ClothingItem(7, "Shoes", "Black", "All", 3),
            ClothingItem(8, "Outerwear", "Brown", "Winter", 2)
        )
        val result = engine.recommend(items, defaultPreferences)

        assertTrue(result.size <= 5)
    }

    @Test
    fun recommend_allRecommendationsHaveExplanations() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 4),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3),
            ClothingItem(3, "Shoes", "Black", "All", 5)
        )
        val result = engine.recommend(items, defaultPreferences)

        result.forEach { rec ->
            assertTrue(
                "Each recommendation must have a non-empty explanation",
                rec.explanation.isNotBlank()
            )
        }
    }

    // -----------------------------------------------------------------------
    // seasonMatchScore()
    // -----------------------------------------------------------------------

    @Test
    fun seasonMatchScore_allSeason_returns0point8() {
        val item = ClothingItem(1, "Top", "Black", "All", 3)
        val score = engine.seasonMatchScore(item, defaultPreferences)
        assertEquals(0.8, score, 0.001)
    }

    @Test
    fun seasonMatchScore_preferredSeason_returns1point0() {
        val item = ClothingItem(1, "Top", "Black", "Summer", 3)
        val score = engine.seasonMatchScore(item, defaultPreferences)
        assertEquals(1.0, score, 0.001)
    }

    @Test
    fun seasonMatchScore_nonPreferredSeason_returns0point2() {
        val item = ClothingItem(1, "Top", "Black", "Winter", 3)
        val score = engine.seasonMatchScore(item, defaultPreferences)
        assertEquals(0.2, score, 0.001)
    }

    @Test
    fun seasonMatchScore_caseInsensitive() {
        val item = ClothingItem(1, "Top", "Black", "all", 3)
        val score = engine.seasonMatchScore(item, defaultPreferences)
        assertEquals(0.8, score, 0.001)
    }

    @Test
    fun seasonMatchScore_preferredSeasonCaseInsensitive() {
        val prefs = defaultPreferences.copy(preferredSeasons = listOf("summer"))
        val item = ClothingItem(1, "Top", "Black", "Summer", 3)
        val score = engine.seasonMatchScore(item, prefs)
        assertEquals(1.0, score, 0.001)
    }

    // -----------------------------------------------------------------------
    // comfortMatchScore()
    // -----------------------------------------------------------------------

    @Test
    fun comfortMatchScore_exactMatch_returns1point0() {
        val item = ClothingItem(1, "Top", "Black", "Summer", 3)
        val prefs = defaultPreferences.copy(comfortPreference = 3)
        assertEquals(1.0, engine.comfortMatchScore(item, prefs), 0.001)
    }

    @Test
    fun comfortMatchScore_diffOf1_returns0point7() {
        val item = ClothingItem(1, "Top", "Black", "Summer", 4)
        val prefs = defaultPreferences.copy(comfortPreference = 3)
        assertEquals(0.7, engine.comfortMatchScore(item, prefs), 0.001)
    }

    @Test
    fun comfortMatchScore_diffOf1Below_returns0point7() {
        val item = ClothingItem(1, "Top", "Black", "Summer", 2)
        val prefs = defaultPreferences.copy(comfortPreference = 3)
        assertEquals(0.7, engine.comfortMatchScore(item, prefs), 0.001)
    }

    @Test
    fun comfortMatchScore_diffOf2_returns0point4() {
        val item = ClothingItem(1, "Top", "Black", "Summer", 5)
        val prefs = defaultPreferences.copy(comfortPreference = 3)
        assertEquals(0.4, engine.comfortMatchScore(item, prefs), 0.001)
    }

    @Test
    fun comfortMatchScore_diffOf3OrMore_returns0point1() {
        val item = ClothingItem(1, "Top", "Black", "Summer", 1)
        val prefs = defaultPreferences.copy(comfortPreference = 5)
        assertEquals(0.1, engine.comfortMatchScore(item, prefs), 0.001)
    }

    // -----------------------------------------------------------------------
    // styleMatchScore()
    // -----------------------------------------------------------------------

    @Test
    fun styleMatchScore_casualTop_returns0point8() {
        val item = ClothingItem(1, "Top", "Black", "Summer", 3)
        val prefs = defaultPreferences.copy(stylePreference = "Casual")
        assertEquals(0.8, engine.styleMatchScore(item, prefs), 0.001)
    }

    @Test
    fun styleMatchScore_casualOuterwear_returns0point5() {
        val item = ClothingItem(1, "Outerwear", "Black", "Winter", 3)
        val prefs = defaultPreferences.copy(stylePreference = "Casual")
        // Casual does not include Outerwear
        assertEquals(0.5, engine.styleMatchScore(item, prefs), 0.001)
    }

    @Test
    fun styleMatchScore_formalOuterwear_returns0point8() {
        val item = ClothingItem(1, "Outerwear", "Black", "Winter", 3)
        val prefs = defaultPreferences.copy(stylePreference = "Formal")
        assertEquals(0.8, engine.styleMatchScore(item, prefs), 0.001)
    }

    @Test
    fun styleMatchScore_unknownStyle_returns0point5() {
        val item = ClothingItem(1, "Top", "Black", "Summer", 3)
        val prefs = defaultPreferences.copy(stylePreference = "Gothic")
        assertEquals(0.5, engine.styleMatchScore(item, prefs), 0.001)
    }

    @Test
    fun styleMatchScore_sportyShoes_returns0point8() {
        val item = ClothingItem(1, "Shoes", "White", "All", 5)
        val prefs = defaultPreferences.copy(stylePreference = "Sporty")
        assertEquals(0.8, engine.styleMatchScore(item, prefs), 0.001)
    }

    @Test
    fun styleMatchScore_streetwearAccessory_returns0point8() {
        val item = ClothingItem(1, "Accessory", "Gold", "All", 3)
        val prefs = defaultPreferences.copy(stylePreference = "Streetwear")
        assertEquals(0.8, engine.styleMatchScore(item, prefs), 0.001)
    }

    // -----------------------------------------------------------------------
    // scoreItem()
    // -----------------------------------------------------------------------

    @Test
    fun scoreItem_perfectMatch_hasHighScore() {
        val item = ClothingItem(1, "Top", "Black", "Summer", 3)
        val prefs = defaultPreferences.copy(
            comfortPreference = 3,
            preferredSeasons = listOf("Summer"),
            stylePreference = "Casual"
        )
        val score = engine.scoreItem(item, prefs)

        // season=1.0*0.25 + comfort=1.0*0.20 + style=0.8*0.15 + fit=0.7*0.10
        // = 0.25 + 0.20 + 0.12 + 0.07 = 0.64
        assertEquals(0.64, score, 0.001)
    }

    @Test
    fun scoreItem_mismatchedSeason_hasLowerScore() {
        val matchItem = ClothingItem(1, "Top", "Black", "Summer", 3)
        val mismatchItem = ClothingItem(2, "Top", "Black", "Winter", 3)
        val prefs = defaultPreferences.copy(preferredSeasons = listOf("Summer"))

        val matchScore = engine.scoreItem(matchItem, prefs)
        val mismatchScore = engine.scoreItem(mismatchItem, prefs)

        assertTrue(matchScore > mismatchScore)
    }

    @Test
    fun scoreItem_isAlwaysNonNegative() {
        val item = ClothingItem(1, "Top", "Black", "Winter", 1)
        val prefs = defaultPreferences.copy(
            comfortPreference = 5,
            preferredSeasons = listOf("Summer")
        )
        val score = engine.scoreItem(item, prefs)
        assertTrue(score >= 0.0)
    }

    // -----------------------------------------------------------------------
    // colorHarmonyBonus()
    // -----------------------------------------------------------------------

    @Test
    fun colorHarmonyBonus_singleItem_returnsZero() {
        val outfit = listOf(ClothingItem(1, "Top", "Black", "Summer", 3))
        assertEquals(0.0, engine.colorHarmonyBonus(outfit), 0.001)
    }

    @Test
    fun colorHarmonyBonus_allNeutrals_returns0point7() {
        val outfit = listOf(
            ClothingItem(1, "Top", "Black", "Summer", 3),
            ClothingItem(2, "Bottom", "White", "Summer", 3)
        )
        assertEquals(0.7, engine.colorHarmonyBonus(outfit), 0.001)
    }

    @Test
    fun colorHarmonyBonus_neutralPlusAccent_returns1point0() {
        val outfit = listOf(
            ClothingItem(1, "Top", "Black", "Summer", 3),
            ClothingItem(2, "Bottom", "Red", "Summer", 3)
        )
        assertEquals(1.0, engine.colorHarmonyBonus(outfit), 0.001)
    }

    @Test
    fun colorHarmonyBonus_complementaryColors_returns0point8() {
        val outfit = listOf(
            ClothingItem(1, "Top", "Blue", "Summer", 3),
            ClothingItem(2, "Bottom", "Orange", "Summer", 3)
        )
        assertEquals(0.8, engine.colorHarmonyBonus(outfit), 0.001)
    }

    @Test
    fun colorHarmonyBonus_analogousAccents_returns0point85() {
        // Red and pink are analogous colors
        val outfit = listOf(
            ClothingItem(1, "Top", "Red", "Summer", 3),
            ClothingItem(2, "Bottom", "Pink", "Summer", 3)
        )
        assertEquals(0.85, engine.colorHarmonyBonus(outfit), 0.001)
    }

    @Test
    fun colorHarmonyBonus_moreThanTwoAccents_returns0point2() {
        val outfit = listOf(
            ClothingItem(1, "Top", "Red", "Summer", 3),
            ClothingItem(2, "Bottom", "Green", "Summer", 3),
            ClothingItem(3, "Shoes", "Purple", "Summer", 3)
        )
        // 3 non-neutral, non-complementary would hit accentCount > 2.
        // However red-green ARE complementary, so let's use non-complementary colors.
        val nonCompOutfit = listOf(
            ClothingItem(1, "Top", "Red", "Summer", 3),
            ClothingItem(2, "Bottom", "Pink", "Summer", 3),
            ClothingItem(3, "Shoes", "Brown", "Summer", 3)
        )
        assertEquals(0.2, engine.colorHarmonyBonus(nonCompOutfit), 0.001)
    }

    @Test
    fun colorHarmonyBonus_emptyOutfit_returnsZero() {
        assertEquals(0.0, engine.colorHarmonyBonus(emptyList()), 0.001)
    }

    // -----------------------------------------------------------------------
    // categoryDiversityBonus()
    // -----------------------------------------------------------------------

    @Test
    fun categoryDiversityBonus_topAndBottom_returns0point8() {
        val outfit = listOf(
            ClothingItem(1, "Top", "Black", "Summer", 3),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3)
        )
        assertEquals(0.8, engine.categoryDiversityBonus(outfit), 0.001)
    }

    @Test
    fun categoryDiversityBonus_topBottomAndShoes_returns1point0() {
        val outfit = listOf(
            ClothingItem(1, "Top", "Black", "Summer", 3),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3),
            ClothingItem(3, "Shoes", "White", "All", 4)
        )
        assertEquals(1.0, engine.categoryDiversityBonus(outfit), 0.001)
    }

    @Test
    fun categoryDiversityBonus_onlyTop_returns0point4() {
        val outfit = listOf(
            ClothingItem(1, "Top", "Black", "Summer", 3)
        )
        assertEquals(0.4, engine.categoryDiversityBonus(outfit), 0.001)
    }

    @Test
    fun categoryDiversityBonus_onlyBottom_returns0point4() {
        val outfit = listOf(
            ClothingItem(1, "Bottom", "Blue", "Summer", 3)
        )
        assertEquals(0.4, engine.categoryDiversityBonus(outfit), 0.001)
    }

    @Test
    fun categoryDiversityBonus_noTopNoBottom_returns0point2() {
        val outfit = listOf(
            ClothingItem(1, "Shoes", "Black", "All", 3),
            ClothingItem(2, "Accessory", "Gold", "All", 5)
        )
        assertEquals(0.2, engine.categoryDiversityBonus(outfit), 0.001)
    }

    @Test
    fun categoryDiversityBonus_emptyOutfit_returns0point2() {
        assertEquals(0.2, engine.categoryDiversityBonus(emptyList()), 0.001)
    }

    // -----------------------------------------------------------------------
    // scoreOutfit()
    // -----------------------------------------------------------------------

    @Test
    fun scoreOutfit_emptyOutfit_returnsZero() {
        assertEquals(0.0, engine.scoreOutfit(emptyList(), defaultPreferences), 0.001)
    }

    @Test
    fun scoreOutfit_includesHarmonyAndCoverageBonus() {
        val outfit = listOf(
            ClothingItem(1, "Top", "Black", "Summer", 3),
            ClothingItem(2, "Bottom", "Red", "Summer", 3)
        )
        val score = engine.scoreOutfit(outfit, defaultPreferences)

        // Score should be avgItemScore + harmony*WEIGHT_HARMONY + coverage*WEIGHT_COVERAGE
        val item1Score = engine.scoreItem(outfit[0], defaultPreferences)
        val item2Score = engine.scoreItem(outfit[1], defaultPreferences)
        val avgItemScore = (item1Score + item2Score) / 2.0
        val harmony = engine.colorHarmonyBonus(outfit)
        val coverage = engine.categoryDiversityBonus(outfit)

        val expected = avgItemScore +
            (harmony * OutfitRecommendationEngine.WEIGHT_HARMONY) +
            (coverage * OutfitRecommendationEngine.WEIGHT_COVERAGE)
        assertEquals(expected, score, 0.001)
    }

    @Test
    fun scoreOutfit_betterOutfitScoresHigher() {
        val goodOutfit = listOf(
            ClothingItem(1, "Top", "Black", "Summer", 3),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3),
            ClothingItem(3, "Shoes", "White", "All", 4)
        )
        val weakOutfit = listOf(
            ClothingItem(4, "Top", "Pink", "Winter", 1),
            ClothingItem(5, "Bottom", "Orange", "Winter", 1)
        )
        val prefs = defaultPreferences.copy(
            comfortPreference = 3,
            preferredSeasons = listOf("Summer")
        )

        assertTrue(engine.scoreOutfit(goodOutfit, prefs) > engine.scoreOutfit(weakOutfit, prefs))
    }

    // -----------------------------------------------------------------------
    // generateItemExplanation()
    // -----------------------------------------------------------------------

    @Test
    fun generateItemExplanation_preferredSeason_mentionsPerfect() {
        val item = ClothingItem(1, "Top", "Black", "Summer", 3)
        val explanation = engine.generateItemExplanation(item, defaultPreferences)
        assertTrue(explanation.contains("Perfect", ignoreCase = true))
    }

    @Test
    fun generateItemExplanation_allSeason_mentionsVersatile() {
        val item = ClothingItem(1, "Top", "Black", "All", 3)
        val explanation = engine.generateItemExplanation(item, defaultPreferences)
        assertTrue(explanation.contains("Versatile", ignoreCase = true))
    }

    @Test
    fun generateItemExplanation_nonPreferredSeason_mentionsSuited() {
        val prefs = defaultPreferences.copy(preferredSeasons = listOf("Summer"))
        val item = ClothingItem(1, "Top", "Black", "Winter", 3)
        val explanation = engine.generateItemExplanation(item, prefs)
        assertTrue(explanation.contains("Suited", ignoreCase = true))
    }

    @Test
    fun generateItemExplanation_comfortExceeds_mentionsExceeds() {
        // Use Formal style so no style note competes for the top-2 slots
        val prefs = defaultPreferences.copy(comfortPreference = 2, stylePreference = "Formal")
        val item = ClothingItem(1, "Top", "Black", "Summer", 4)
        val explanation = engine.generateItemExplanation(item, prefs)
        assertTrue(explanation.contains("exceeds", ignoreCase = true))
    }

    @Test
    fun generateItemExplanation_comfortExact_mentionsMatches() {
        val prefs = defaultPreferences.copy(comfortPreference = 3)
        val item = ClothingItem(1, "Top", "Black", "Summer", 3)
        val explanation = engine.generateItemExplanation(item, prefs)
        assertTrue(explanation.contains("matches", ignoreCase = true))
    }

    @Test
    fun generateItemExplanation_comfortSlightlyBelow_mentionsSlightly() {
        val prefs = defaultPreferences.copy(comfortPreference = 4)
        val item = ClothingItem(1, "Top", "Black", "Summer", 3)
        val explanation = engine.generateItemExplanation(item, prefs)
        assertTrue(explanation.contains("slightly", ignoreCase = true))
    }

    @Test
    fun generateItemExplanation_comfortFarBelow_mentionsStyle() {
        val prefs = defaultPreferences.copy(comfortPreference = 5)
        val item = ClothingItem(1, "Top", "Black", "Summer", 1)
        val explanation = engine.generateItemExplanation(item, prefs)
        assertTrue(explanation.contains("style", ignoreCase = true))
    }

    @Test
    fun generateItemExplanation_includesColorCharacter() {
        val item = ClothingItem(1, "Top", "Black", "Summer", 3)
        val explanation = engine.generateItemExplanation(item, defaultPreferences)
        assertTrue(explanation.contains("sophistication", ignoreCase = true))
    }

    @Test
    fun generateItemExplanation_casualHighComfort_mentionsCasual() {
        val prefs = defaultPreferences.copy(stylePreference = "Casual")
        val item = ClothingItem(1, "Top", "Black", "Summer", 4)
        val explanation = engine.generateItemExplanation(item, prefs)
        assertTrue(explanation.contains("casual", ignoreCase = true))
    }

    @Test
    fun generateItemExplanation_endsWithPeriod() {
        val item = ClothingItem(1, "Top", "Black", "Summer", 3)
        val explanation = engine.generateItemExplanation(item, defaultPreferences)
        assertTrue(explanation.endsWith("."))
    }

    // -----------------------------------------------------------------------
    // UserPreferences integration tests
    // -----------------------------------------------------------------------

    @Test
    fun recommend_casualPreferenceRanksComfortableHigher() {
        val items = listOf(
            ClothingItem(1, "Top", "Black", "Summer", 5),
            ClothingItem(2, "Top", "White", "Summer", 1),
            ClothingItem(3, "Bottom", "Blue", "Summer", 5),
            ClothingItem(4, "Bottom", "Gray", "Summer", 1)
        )
        val prefs = defaultPreferences.copy(
            stylePreference = "Casual",
            comfortPreference = 5,
            preferredSeasons = listOf("Summer")
        )
        val result = engine.recommend(items, prefs)

        assertTrue(result.isNotEmpty())
        // The top result should contain the high-comfort items
        val topOutfit = result[0]
        val avgComfort = topOutfit.items.sumOf { it.comfortLevel }.toDouble() / topOutfit.items.size
        assertTrue("Top recommendation should favor high comfort", avgComfort >= 3.0)
    }

    @Test
    fun recommend_winterPreference_ranksWinterItemsHigher() {
        val items = listOf(
            ClothingItem(1, "Top", "Black", "Winter", 3),
            ClothingItem(2, "Top", "White", "Summer", 3),
            ClothingItem(3, "Bottom", "Navy", "Winter", 3),
            ClothingItem(4, "Bottom", "Beige", "Summer", 3)
        )
        val prefs = defaultPreferences.copy(
            preferredSeasons = listOf("Winter")
        )
        val result = engine.recommend(items, prefs)

        assertTrue(result.isNotEmpty())
        // The best-scored result should contain winter items
        val topOutfit = result[0]
        val winterItemCount = topOutfit.items.count { it.season == "Winter" }
        assertTrue("Top recommendation should favor winter items", winterItemCount >= 1)
    }

    @Test
    fun recommend_formalPreference_ranksOuterwearWell() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "All", 3),
            ClothingItem(2, "Bottom", "Black", "All", 3),
            ClothingItem(3, "Outerwear", "Navy", "All", 3)
        )
        val prefs = defaultPreferences.copy(stylePreference = "Formal")
        val result = engine.recommend(items, prefs)

        assertTrue(result.isNotEmpty())
        // There should be an outfit that includes outerwear
        val withOuterwear = result.find { rec ->
            rec.items.any { it.category == "Outerwear" }
        }
        assertNotNull("Formal recommendations should include outerwear options", withOuterwear)
    }

    @Test
    fun recommend_accessibilityMode_doesNotCrash() {
        val prefs = defaultPreferences.copy(accessibilityModeEnabled = true)
        val items = listOf(
            ClothingItem(1, "Top", "Black", "Summer", 3),
            ClothingItem(2, "Bottom", "Blue", "Summer", 4)
        )
        val result = engine.recommend(items, prefs)
        assertTrue(result.isNotEmpty())
    }

    // -----------------------------------------------------------------------
    // Scoring weight constants
    // -----------------------------------------------------------------------

    @Test
    fun weights_sumToReasonableValue() {
        val totalWeight = OutfitRecommendationEngine.WEIGHT_SEASON +
            OutfitRecommendationEngine.WEIGHT_COMFORT +
            OutfitRecommendationEngine.WEIGHT_STYLE +
            OutfitRecommendationEngine.WEIGHT_FIT +
            OutfitRecommendationEngine.WEIGHT_HARMONY +
            OutfitRecommendationEngine.WEIGHT_COVERAGE

        assertEquals(1.0, totalWeight, 0.001)
    }

    @Test
    fun weights_seasonIsLargest() {
        assertTrue(
            OutfitRecommendationEngine.WEIGHT_SEASON > OutfitRecommendationEngine.WEIGHT_COMFORT
        )
        assertTrue(
            OutfitRecommendationEngine.WEIGHT_SEASON > OutfitRecommendationEngine.WEIGHT_STYLE
        )
    }

    // -----------------------------------------------------------------------
    // Outfit combination builder (tested indirectly through recommend)
    // -----------------------------------------------------------------------

    @Test
    fun recommend_topBottomOuterwear_producesCombinationsIncludingOuterwear() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 3),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3),
            ClothingItem(3, "Outerwear", "Black", "Winter", 3)
        )
        val result = engine.recommend(items, defaultPreferences)

        val withOuterwear = result.any { rec ->
            rec.items.any { it.category == "Outerwear" }
        }
        assertTrue("Should produce outfits including outerwear", withOuterwear)
    }

    @Test
    fun recommend_topBottomShoes_producesCombinationsIncludingShoes() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 3),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3),
            ClothingItem(3, "Shoes", "Black", "All", 4)
        )
        val result = engine.recommend(items, defaultPreferences)

        val withShoes = result.any { rec ->
            rec.items.any { it.category == "Shoes" }
        }
        assertTrue("Should produce outfits including shoes", withShoes)
    }

    @Test
    fun recommend_topBottomAccessory_producesCombinationsIncludingAccessory() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 3),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3),
            ClothingItem(3, "Accessory", "Gold", "All", 5)
        )
        val result = engine.recommend(items, defaultPreferences)

        val withAccessory = result.any { rec ->
            rec.items.any { it.category == "Accessory" }
        }
        assertTrue("Should produce outfits including accessories", withAccessory)
    }

    // -----------------------------------------------------------------------
    // Diverse color handling
    // -----------------------------------------------------------------------

    @Test
    fun colorHarmonyBonus_grayIsNeutral() {
        val outfit = listOf(
            ClothingItem(1, "Top", "Gray", "Summer", 3),
            ClothingItem(2, "Bottom", "Grey", "Summer", 3)
        )
        assertEquals(0.7, engine.colorHarmonyBonus(outfit), 0.001)
    }

    @Test
    fun colorHarmonyBonus_beigeAndNavyAreNeutral() {
        val outfit = listOf(
            ClothingItem(1, "Top", "Beige", "Summer", 3),
            ClothingItem(2, "Bottom", "Navy", "Summer", 3)
        )
        assertEquals(0.7, engine.colorHarmonyBonus(outfit), 0.001)
    }

    @Test
    fun colorHarmonyBonus_redAndGreenComplementary() {
        // No neutral base — complementary without neutrals returns 0.8
        val outfit = listOf(
            ClothingItem(1, "Top", "Red", "Summer", 3),
            ClothingItem(2, "Bottom", "Green", "Summer", 3)
        )
        assertEquals(0.8, engine.colorHarmonyBonus(outfit), 0.001)
    }

    @Test
    fun colorHarmonyBonus_yellowAndPurpleComplementary() {
        // No neutral base — complementary without neutrals returns 0.8
        val outfit = listOf(
            ClothingItem(1, "Top", "Yellow", "Summer", 3),
            ClothingItem(2, "Bottom", "Purple", "Summer", 3)
        )
        assertEquals(0.8, engine.colorHarmonyBonus(outfit), 0.001)
    }

    // -----------------------------------------------------------------------
    // Repeat prevention — recentlyShown parameter
    // -----------------------------------------------------------------------

    @Test
    fun recommend_recentlyShown_excludesMatchingOutfits() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 5),
            ClothingItem(2, "Top", "Black", "Summer", 3),
            ClothingItem(3, "Top", "Blue", "Summer", 4),
            ClothingItem(4, "Bottom", "Navy", "Summer", 3),
            ClothingItem(5, "Bottom", "Gray", "Summer", 4),
            ClothingItem(6, "Shoes", "Black", "All", 4)
        )
        // Mark only a small subset as recently shown
        val shown = setOf(setOf(1, 4), setOf(1, 4, 6))

        val result = engine.recommend(items, defaultPreferences, recentlyShown = shown)

        assertTrue(result.isNotEmpty())
        for (rec in result) {
            val key = rec.items.map { it.id }.toSet()
            assertFalse(
                "Recently shown outfit $key should not repeat",
                key in shown
            )
        }
    }

    @Test
    fun recommend_allCombosRecentlyShown_fallsBackToShowingResults() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 4),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3)
        )
        // Only one possible combo: {1, 2}. Mark it as recently shown.
        val shown = setOf(setOf(1, 2))

        val result = engine.recommend(items, defaultPreferences, recentlyShown = shown)

        // Should still return results rather than an empty list
        assertTrue("Should return results even if all combos were recently shown", result.isNotEmpty())
    }

    @Test
    fun recommend_emptyHistory_behavesNormally() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 4),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3)
        )
        val withHistory = engine.recommend(items, defaultPreferences, recentlyShown = emptySet())
        val without = engine.recommend(items, defaultPreferences)

        assertEquals(withHistory.size, without.size)
        assertEquals(
            withHistory.map { it.items.map { i -> i.id }.toSet() },
            without.map { it.items.map { i -> i.id }.toSet() }
        )
    }

    @Test
    fun recommend_partialHistoryOverlap_excludesOnlyShownCombos() {
        val items = listOf(
            ClothingItem(1, "Top", "White", "Summer", 5),
            ClothingItem(2, "Top", "Black", "Summer", 3),
            ClothingItem(3, "Bottom", "Blue", "Summer", 3),
            ClothingItem(4, "Shoes", "Black", "All", 4)
        )
        // Mark only a couple of combos as shown
        val shown = setOf(setOf(1, 3), setOf(1, 3, 4))

        val result = engine.recommend(items, defaultPreferences, recentlyShown = shown)

        for (rec in result) {
            val key = rec.items.map { it.id }.toSet()
            assertFalse("Combo $key was recently shown and should be excluded", key in shown)
        }
    }

    @Test
    fun maxHistorySize_isReasonable() {
        assertTrue(
            "History size should be positive",
            OutfitRecommendationEngine.MAX_HISTORY_SIZE > 0
        )
        assertTrue(
            "History size should not be excessively large",
            OutfitRecommendationEngine.MAX_HISTORY_SIZE <= 50
        )
    }

    // -----------------------------------------------------------------------
    // Context penalty cap
    // -----------------------------------------------------------------------

    @Test
    fun weights_contextPenaltyCap_coversAllPenalties() {
        val maxPossiblePenalty = OutfitRecommendationEngine.WEIGHT_DIVERSITY +
            OutfitRecommendationEngine.WEIGHT_PLANNER
        assertTrue(
            "MAX_CONTEXT_PENALTY should cover combined diversity + planner penalties",
            OutfitRecommendationEngine.MAX_CONTEXT_PENALTY >= maxPossiblePenalty
        )
    }
}
