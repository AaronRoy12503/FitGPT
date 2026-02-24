package com.fitgpt.app

import com.fitgpt.app.ai.OutfitRecommendationEngine
import com.fitgpt.app.data.model.ClothingItem
import com.fitgpt.app.data.model.OutfitRecommendation
import com.fitgpt.app.data.model.UserPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * PII safety tests for OutfitRecommendationEngine.
 *
 * Verifies that recommendation output (explanations, item explanations,
 * and the overall OutfitRecommendation structure) does not leak personally
 * identifiable information or sensitive user preference values that could
 * identify a specific user.
 */
class OutfitRecommendationEnginePiiTest {

    private lateinit var engine: OutfitRecommendationEngine

    // --- Test fixtures ---

    private val sampleItems = listOf(
        ClothingItem(1, "Top", "Black", "Summer", 4, fit = "Fitted"),
        ClothingItem(2, "Bottom", "Blue", "Summer", 3, fit = "Regular"),
        ClothingItem(3, "Outerwear", "Navy", "Winter", 3, fit = "Oversized"),
        ClothingItem(4, "Shoes", "White", "All", 5, fit = "Regular"),
        ClothingItem(5, "Accessory", "Gold", "All", 5, fit = "Regular")
    )

    /** Preferences with distinctive values that would be PII if leaked verbatim. */
    private val sensitivePreferences = UserPreferences(
        bodyType = "plus-size",
        stylePreference = "Streetwear",
        comfortPreference = 5,
        preferredSeasons = listOf("Summer", "Spring")
    )

    private val allBodyTypes = listOf("slim", "athletic", "plus-size", "Average")
    private val allStyles = listOf("Casual", "Formal", "Sporty", "Streetwear")

    @Before
    fun setUp() {
        engine = OutfitRecommendationEngine()
    }

    // -----------------------------------------------------------------------
    // 1. Explanation strings must not contain user IDs or PII
    // -----------------------------------------------------------------------

    @Test
    fun explanation_doesNotContainNumericUserIds() {
        // Use item IDs that look like user database IDs to make sure they
        // don't leak into explanations.
        val items = listOf(
            ClothingItem(99001, "Top", "White", "Summer", 4),
            ClothingItem(99002, "Bottom", "Black", "Summer", 3)
        )
        val result = engine.recommend(items, sensitivePreferences)

        for (rec in result) {
            assertFalse(
                "Explanation must not contain item ID 99001: ${rec.explanation}",
                rec.explanation.contains("99001")
            )
            assertFalse(
                "Explanation must not contain item ID 99002: ${rec.explanation}",
                rec.explanation.contains("99002")
            )
        }
    }

    @Test
    fun explanation_doesNotContainRawComfortPreferenceValue() {
        val prefs = sensitivePreferences.copy(comfortPreference = 5)
        val items = listOf(
            ClothingItem(1, "Top", "Black", "Summer", 4),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3)
        )
        val result = engine.recommend(items, prefs)

        for (rec in result) {
            // The explanation should use descriptive language, not raw numeric
            // preference values like "comfort preference: 5" or "comfortPreference=5"
            assertFalse(
                "Explanation must not expose raw comfort preference identifier: ${rec.explanation}",
                rec.explanation.contains("comfortPreference", ignoreCase = true)
            )
            assertFalse(
                "Explanation must not expose preference as key=value: ${rec.explanation}",
                rec.explanation.contains("preference=", ignoreCase = true)
            )
            assertFalse(
                "Explanation must not expose preference as key: value: ${rec.explanation}",
                rec.explanation.contains("preference:", ignoreCase = true)
            )
        }
    }

    @Test
    fun explanation_doesNotContainRawBodyTypeFieldName() {
        val result = engine.recommend(sampleItems, sensitivePreferences)

        for (rec in result) {
            assertFalse(
                "Explanation must not contain raw field name 'bodyType': ${rec.explanation}",
                rec.explanation.contains("bodyType", ignoreCase = false)
            )
            assertFalse(
                "Explanation must not contain raw field name 'body_type': ${rec.explanation}",
                rec.explanation.contains("body_type", ignoreCase = true)
            )
        }
    }

    @Test
    fun explanation_doesNotContainRawStylePreferenceFieldName() {
        val result = engine.recommend(sampleItems, sensitivePreferences)

        for (rec in result) {
            assertFalse(
                "Explanation must not contain raw field name 'stylePreference': ${rec.explanation}",
                rec.explanation.contains("stylePreference", ignoreCase = false)
            )
        }
    }

    // -----------------------------------------------------------------------
    // 2. itemExplanations must not leak sensitive preference details
    // -----------------------------------------------------------------------

    @Test
    fun itemExplanation_doesNotContainRawFieldNames() {
        val result = engine.recommend(sampleItems, sensitivePreferences)

        for (rec in result) {
            for ((itemId, explanation) in rec.itemExplanations) {
                assertFalse(
                    "Item $itemId explanation must not contain 'bodyType': $explanation",
                    explanation.contains("bodyType", ignoreCase = false)
                )
                assertFalse(
                    "Item $itemId explanation must not contain 'stylePreference': $explanation",
                    explanation.contains("stylePreference", ignoreCase = false)
                )
                assertFalse(
                    "Item $itemId explanation must not contain 'comfortPreference': $explanation",
                    explanation.contains("comfortPreference", ignoreCase = false)
                )
                assertFalse(
                    "Item $itemId explanation must not contain 'preferredSeasons': $explanation",
                    explanation.contains("preferredSeasons", ignoreCase = false)
                )
            }
        }
    }

    @Test
    fun itemExplanation_doesNotContainRawPreferenceKeyValuePairs() {
        for (bodyType in allBodyTypes) {
            for (style in allStyles) {
                val prefs = UserPreferences(
                    bodyType = bodyType,
                    stylePreference = style,
                    comfortPreference = 3,
                    preferredSeasons = listOf("Summer")
                )
                val result = engine.recommend(sampleItems, prefs)

                for (rec in result) {
                    for ((itemId, explanation) in rec.itemExplanations) {
                        // Must not contain key=value or key: value patterns exposing
                        // user preference data model internals
                        assertFalse(
                            "Item $itemId explanation for body=$bodyType style=$style must not contain '=': $explanation",
                            explanation.matches(Regex(".*\\b(bodyType|stylePreference|comfortPreference)\\s*[=:].*", RegexOption.IGNORE_CASE))
                        )
                    }
                }
            }
        }
    }

    @Test
    fun itemExplanation_doesNotExposeNumericComfortPreference() {
        val prefs = sensitivePreferences.copy(comfortPreference = 5)
        val result = engine.recommend(sampleItems, prefs)

        for (rec in result) {
            for ((itemId, explanation) in rec.itemExplanations) {
                assertFalse(
                    "Item $itemId explanation must not expose raw 'comfort preference' with number: $explanation",
                    explanation.contains(Regex("comfort\\s*preference\\s*[:=]?\\s*\\d", RegexOption.IGNORE_CASE))
                )
            }
        }
    }

    // -----------------------------------------------------------------------
    // 3. Output only contains clothing attributes (no user-specific data)
    // -----------------------------------------------------------------------

    @Test
    fun recommendationOutput_itemsContainOnlyClothingAttributes() {
        val result = engine.recommend(sampleItems, sensitivePreferences)

        for (rec in result) {
            for (item in rec.items) {
                // Each item in the output should be one of the original input items.
                // This ensures no user data was injected into the clothing items.
                assertTrue(
                    "Output item ${item.id} must be from original wardrobe",
                    sampleItems.contains(item)
                )
            }
        }
    }

    @Test
    fun recommendationOutput_explanationContainsOnlyClothingTerms() {
        val result = engine.recommend(sampleItems, sensitivePreferences)

        // These are user-data field names / identifiers that should never appear
        val forbiddenPatterns = listOf(
            "userId", "user_id", "email", "phone", "address",
            "password", "token", "session", "account"
        )

        for (rec in result) {
            for (pattern in forbiddenPatterns) {
                assertFalse(
                    "Explanation must not contain '$pattern': ${rec.explanation}",
                    rec.explanation.contains(pattern, ignoreCase = true)
                )
            }
            for ((itemId, explanation) in rec.itemExplanations) {
                for (pattern in forbiddenPatterns) {
                    assertFalse(
                        "Item $itemId explanation must not contain '$pattern': $explanation",
                        explanation.contains(pattern, ignoreCase = true)
                    )
                }
            }
        }
    }

    @Test
    fun recommendationOutput_scoreIsNumericOnly() {
        val result = engine.recommend(sampleItems, sensitivePreferences)

        for (rec in result) {
            assertTrue("Score must be finite", rec.score.isFinite())
            assertTrue("Score must be non-negative", rec.score >= 0.0)
        }
    }

    @Test
    fun recommendationOutput_itemExplanationKeysAreItemIds() {
        val result = engine.recommend(sampleItems, sensitivePreferences)
        val validIds = sampleItems.map { it.id }.toSet()

        for (rec in result) {
            for (key in rec.itemExplanations.keys) {
                assertTrue(
                    "itemExplanation key $key must correspond to a clothing item ID",
                    key in validIds
                )
            }
        }
    }

    @Test
    fun recommendationOutput_noImageUrlsInExplanations() {
        // Items with imageUrls should not have those URLs appear in explanations
        val itemsWithUrls = listOf(
            ClothingItem(1, "Top", "Black", "Summer", 4, imageUrl = "https://example.com/img/user123/shirt.jpg"),
            ClothingItem(2, "Bottom", "Blue", "Summer", 3, imageUrl = "https://cdn.fitgpt.com/private/abc.png")
        )
        val result = engine.recommend(itemsWithUrls, sensitivePreferences)

        for (rec in result) {
            assertFalse(
                "Explanation must not contain image URLs: ${rec.explanation}",
                rec.explanation.contains("https://", ignoreCase = true)
            )
            assertFalse(
                "Explanation must not contain image URLs: ${rec.explanation}",
                rec.explanation.contains("http://", ignoreCase = true)
            )
            for ((_, explanation) in rec.itemExplanations) {
                assertFalse(
                    "Item explanation must not contain image URLs: $explanation",
                    explanation.contains("https://", ignoreCase = true)
                )
            }
        }
    }

    // -----------------------------------------------------------------------
    // 4. generateItemExplanation() output is safe for display
    // -----------------------------------------------------------------------

    @Test
    fun generateItemExplanation_outputIsSafeForDisplay_noHtmlOrScript() {
        for (bodyType in allBodyTypes) {
            for (style in allStyles) {
                val prefs = UserPreferences(
                    bodyType = bodyType,
                    stylePreference = style,
                    comfortPreference = 3,
                    preferredSeasons = listOf("Summer")
                )
                for (item in sampleItems) {
                    val explanation = engine.generateItemExplanation(item, prefs)
                    assertFalse(
                        "Explanation must not contain HTML tags: $explanation",
                        explanation.contains("<", ignoreCase = true) && explanation.contains(">", ignoreCase = true)
                    )
                    assertFalse(
                        "Explanation must not contain script tags: $explanation",
                        explanation.contains("script", ignoreCase = true)
                    )
                }
            }
        }
    }

    @Test
    fun generateItemExplanation_outputIsNonEmpty() {
        for (bodyType in allBodyTypes) {
            for (style in allStyles) {
                val prefs = UserPreferences(
                    bodyType = bodyType,
                    stylePreference = style,
                    comfortPreference = 3,
                    preferredSeasons = listOf("Summer")
                )
                for (item in sampleItems) {
                    val explanation = engine.generateItemExplanation(item, prefs)
                    assertTrue(
                        "Explanation must not be blank for body=$bodyType style=$style item=${item.id}",
                        explanation.isNotBlank()
                    )
                }
            }
        }
    }

    @Test
    fun generateItemExplanation_outputEndsWithPeriod() {
        for (item in sampleItems) {
            val explanation = engine.generateItemExplanation(item, sensitivePreferences)
            assertTrue(
                "Explanation must end with period: $explanation",
                explanation.endsWith(".")
            )
        }
    }

    @Test
    fun generateItemExplanation_doesNotContainItemId() {
        val item = ClothingItem(42, "Top", "Red", "Summer", 4)
        val explanation = engine.generateItemExplanation(item, sensitivePreferences)
        assertFalse(
            "Explanation must not contain raw item ID '42': $explanation",
            explanation.contains("42")
        )
    }

    @Test
    fun generateItemExplanation_usesDescriptiveLanguageNotRawValues() {
        // Ensure comfort is described in natural language, not raw numbers
        val item = ClothingItem(1, "Top", "Black", "Summer", 4)
        val prefs = sensitivePreferences.copy(comfortPreference = 3)
        val explanation = engine.generateItemExplanation(item, prefs)

        // Should use words like "exceeds", "matches", "slightly below" etc.
        // rather than "comfort: 4" or "comfort level 4 vs preference 3"
        assertFalse(
            "Should not expose raw comfort numbers like 'comfort: N': $explanation",
            explanation.contains(Regex("comfort\\s*:\\s*\\d"))
        )
        assertFalse(
            "Should not expose 'comfort level N': $explanation",
            explanation.contains(Regex("comfort\\s+level\\s+\\d", RegexOption.IGNORE_CASE))
        )
    }

    @Test
    fun generateItemExplanation_safeAcrossAllBodyTypes() {
        val item = ClothingItem(1, "Top", "Black", "Summer", 4, fit = "Fitted")

        for (bodyType in allBodyTypes) {
            val prefs = UserPreferences(
                bodyType = bodyType,
                stylePreference = "Casual",
                comfortPreference = 3,
                preferredSeasons = listOf("Summer")
            )
            val explanation = engine.generateItemExplanation(item, prefs)

            // Body type is used for styling advice but should never appear as a
            // raw preference identifier
            assertFalse(
                "Must not contain 'bodyType' as a data field for body=$bodyType: $explanation",
                explanation.contains("bodyType", ignoreCase = false)
            )
            assertFalse(
                "Must not contain 'body_type' for body=$bodyType: $explanation",
                explanation.contains("body_type", ignoreCase = true)
            )
        }
    }

    @Test
    fun generateItemExplanation_onlyReferencesClothingAttributes() {
        // The output should reference clothing-related terms (color, season,
        // comfort, fit, style) but not user-account-level PII terms
        val item = ClothingItem(1, "Top", "Black", "Summer", 4)
        val explanation = engine.generateItemExplanation(item, sensitivePreferences)

        val piiTerms = listOf(
            "email", "phone", "address", "name", "userId",
            "user_id", "password", "ssn", "social security"
        )
        for (term in piiTerms) {
            assertFalse(
                "Explanation must not reference PII term '$term': $explanation",
                explanation.contains(term, ignoreCase = true)
            )
        }
    }

    // -----------------------------------------------------------------------
    // 5. Full recommend() PII sweep across body types and styles
    // -----------------------------------------------------------------------

    @Test
    fun recommend_fullSweep_noPiiInAnyOutput() {
        for (bodyType in allBodyTypes) {
            for (style in allStyles) {
                val prefs = UserPreferences(
                    bodyType = bodyType,
                    stylePreference = style,
                    comfortPreference = 3,
                    preferredSeasons = listOf("Summer", "Winter")
                )
                val result = engine.recommend(sampleItems, prefs)

                for (rec in result) {
                    // Check explanation
                    assertFalse(
                        "body=$bodyType style=$style: explanation must not contain 'bodyType': ${rec.explanation}",
                        rec.explanation.contains("bodyType", ignoreCase = false)
                    )
                    assertFalse(
                        "body=$bodyType style=$style: explanation must not contain 'stylePreference': ${rec.explanation}",
                        rec.explanation.contains("stylePreference", ignoreCase = false)
                    )
                    assertFalse(
                        "body=$bodyType style=$style: explanation must not contain 'comfortPreference': ${rec.explanation}",
                        rec.explanation.contains("comfortPreference", ignoreCase = false)
                    )

                    // Check item explanations
                    for ((itemId, itemExpl) in rec.itemExplanations) {
                        assertFalse(
                            "body=$bodyType style=$style item=$itemId: must not contain 'bodyType': $itemExpl",
                            itemExpl.contains("bodyType", ignoreCase = false)
                        )
                        assertFalse(
                            "body=$bodyType style=$style item=$itemId: must not contain 'stylePreference': $itemExpl",
                            itemExpl.contains("stylePreference", ignoreCase = false)
                        )
                    }
                }
            }
        }
    }

    @Test
    fun recommend_explanationsDoNotContainUserPreferencesObjectDump() {
        // Guard against accidentally calling toString() on UserPreferences
        val result = engine.recommend(sampleItems, sensitivePreferences)

        for (rec in result) {
            assertFalse(
                "Explanation must not contain UserPreferences toString dump: ${rec.explanation}",
                rec.explanation.contains("UserPreferences(", ignoreCase = true)
            )
            for ((_, itemExpl) in rec.itemExplanations) {
                assertFalse(
                    "Item explanation must not contain UserPreferences toString dump: $itemExpl",
                    itemExpl.contains("UserPreferences(", ignoreCase = true)
                )
            }
        }
    }

    @Test
    fun recommend_explanationsDoNotContainClothingItemObjectDump() {
        // Guard against accidentally calling toString() on ClothingItem
        val result = engine.recommend(sampleItems, sensitivePreferences)

        for (rec in result) {
            assertFalse(
                "Explanation must not contain ClothingItem toString dump: ${rec.explanation}",
                rec.explanation.contains("ClothingItem(", ignoreCase = true)
            )
            for ((_, itemExpl) in rec.itemExplanations) {
                assertFalse(
                    "Item explanation must not contain ClothingItem toString dump: $itemExpl",
                    itemExpl.contains("ClothingItem(", ignoreCase = true)
                )
            }
        }
    }
}
