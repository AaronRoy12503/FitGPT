package com.fitgpt.app

import com.fitgpt.app.ai.OutfitRecommendationEngine
import com.fitgpt.app.data.model.ClothingCategory
import com.fitgpt.app.data.model.ClothingItem
import com.fitgpt.app.data.model.UserPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ClothingCategoryTest {

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
    // ClothingCategory.ALL
    // -----------------------------------------------------------------------

    @Test
    fun `ALL contains exactly 5 categories`() {
        assertEquals(5, ClothingCategory.ALL.size)
    }

    @Test
    fun `ALL contains the expected categories`() {
        val expected = setOf("Top", "Bottom", "Shoes", "Outerwear", "Accessory")
        assertEquals(expected, ClothingCategory.ALL.toSet())
    }

    // -----------------------------------------------------------------------
    // Engine category filters match ClothingCategory.ALL
    // -----------------------------------------------------------------------

    @Test
    fun `engine filters items into all 5 categories`() {
        val items = ClothingCategory.ALL.mapIndexed { index, cat ->
            ClothingItem(index + 1, cat, "Black", "All", 3, "Regular")
        }

        val results = engine.recommend(items, defaultPreferences)

        // With one item per category, multi-item outfits should be formed
        assertTrue(
            "Expected multi-item outfits but got single-item results",
            results.any { it.items.size > 1 }
        )
    }

    @Test
    fun `engine forms outfits when all categories are present`() {
        val items = listOf(
            ClothingItem(1, ClothingCategory.TOP, "Black", "All", 3, "Regular"),
            ClothingItem(2, ClothingCategory.BOTTOM, "Blue", "All", 4, "Regular"),
            ClothingItem(3, ClothingCategory.SHOES, "White", "All", 4, "Regular"),
            ClothingItem(4, ClothingCategory.OUTERWEAR, "Navy", "Winter", 3, "Regular"),
            ClothingItem(5, ClothingCategory.ACCESSORY, "Brown", "All", 5, "Regular")
        )

        val results = engine.recommend(items, defaultPreferences)
        assertTrue("Expected recommendations", results.isNotEmpty())
        // Every outfit should contain at least a Top and Bottom
        results.filter { it.items.size >= 2 }.forEach { rec ->
            val categories = rec.items.map { it.category }
            assertTrue("Outfit missing Top", categories.contains(ClothingCategory.TOP))
            assertTrue("Outfit missing Bottom", categories.contains(ClothingCategory.BOTTOM))
        }
    }

    // -----------------------------------------------------------------------
    // Unrecognized categories fall through to individual-item scoring
    // -----------------------------------------------------------------------

    @Test
    fun `unrecognized categories fall through to individual item scoring`() {
        val items = listOf(
            ClothingItem(1, "Shirt", "Black", "All", 3, "Regular"),
            ClothingItem(2, "Pants", "Blue", "All", 4, "Regular"),
            ClothingItem(3, "Hat", "Red", "Summer", 3, "Regular")
        )

        val results = engine.recommend(items, defaultPreferences)

        // No valid Top/Bottom means no multi-item outfits — fallback to individual scoring
        assertTrue("Expected individual-item fallback results", results.isNotEmpty())
        assertTrue(
            "All results should be single-item (fallback)",
            results.all { it.items.size == 1 }
        )
    }

    // -----------------------------------------------------------------------
    // Dropdown constant list validation
    // -----------------------------------------------------------------------

    @Test
    fun `ALL list has no duplicates`() {
        assertEquals(ClothingCategory.ALL.size, ClothingCategory.ALL.toSet().size)
    }

    @Test
    fun `ALL list entries are non-blank`() {
        ClothingCategory.ALL.forEach { category ->
            assertTrue("Category should not be blank", category.isNotBlank())
        }
    }
}
