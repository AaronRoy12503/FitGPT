package com.fitgpt.app

import com.fitgpt.app.data.model.ClothingItem
import com.fitgpt.app.data.model.UserPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Method

/**
 * Unit tests verifying that GroqRecommendationService does not send PII
 * (user IDs, emails, auth tokens, device info, timestamps, imageUrls) to
 * the Groq API. Only safe clothing attributes (category, color, season,
 * comfortLevel, fit) and safe preference fields (bodyType, stylePreference,
 * comfortPreference, preferredSeasons) should appear in prompts.
 */
class GroqRecommendationServicePiiTest {

    // We use reflection to invoke the private prompt-building methods directly,
    // avoiding the need for a live API call or modifying production visibility.
    private lateinit var service: Any
    private lateinit var buildPromptMethod: Method
    private lateinit var buildItemPromptMethod: Method

    private val safePreferences = UserPreferences(
        bodyType = "Athletic",
        stylePreference = "Casual",
        comfortPreference = 4,
        preferredSeasons = listOf("Summer", "Spring")
    )

    @Before
    fun setUp() {
        val serviceClass = Class.forName("com.fitgpt.app.ai.GroqRecommendationService")
        service = serviceClass.getDeclaredConstructor().newInstance()

        buildPromptMethod = serviceClass.getDeclaredMethod(
            "buildPrompt",
            Map::class.java,
            UserPreferences::class.java
        ).apply { isAccessible = true }

        buildItemPromptMethod = serviceClass.getDeclaredMethod(
            "buildItemPrompt",
            ClothingItem::class.java,
            UserPreferences::class.java
        ).apply { isAccessible = true }
    }

    // -----------------------------------------------------------------------
    // Helper to invoke private buildPrompt
    // -----------------------------------------------------------------------

    private fun invokeBuildPrompt(
        indexToItem: Map<Int, ClothingItem>,
        prefs: UserPreferences = safePreferences
    ): String {
        @Suppress("UNCHECKED_CAST")
        return buildPromptMethod.invoke(service, indexToItem, prefs) as String
    }

    private fun invokeBuildItemPrompt(
        item: ClothingItem,
        prefs: UserPreferences = safePreferences
    ): String {
        return buildItemPromptMethod.invoke(service, item, prefs) as String
    }

    // -----------------------------------------------------------------------
    // buildPrompt: No real item IDs in the prompt
    // -----------------------------------------------------------------------

    @Test
    fun buildPrompt_usesAnonymousIndicesNotRealIds() {
        val items = listOf(
            ClothingItem(id = 42, category = "Top", color = "Black", season = "Summer", comfortLevel = 3),
            ClothingItem(id = 99, category = "Bottom", color = "Blue", season = "Winter", comfortLevel = 4),
            ClothingItem(id = 1337, category = "Shoes", color = "White", season = "All", comfortLevel = 5)
        )
        val indexToItem = items.mapIndexed { index, item -> index to item }.toMap()
        val prompt = invokeBuildPrompt(indexToItem)

        // Real IDs must NOT appear in the prompt
        assertFalse("Real item ID 42 must not appear in prompt", prompt.contains("42"))
        assertFalse("Real item ID 99 must not appear in prompt", prompt.contains("99"))
        assertFalse("Real item ID 1337 must not appear in prompt", prompt.contains("1337"))

        // Anonymous indices 0, 1, 2 SHOULD appear
        assertTrue("Anonymous index 0 should appear", prompt.contains("0"))
        assertTrue("Anonymous index 1 should appear", prompt.contains("1"))
        assertTrue("Anonymous index 2 should appear", prompt.contains("2"))
    }

    @Test
    fun buildPrompt_largeRealIds_neverLeakIntoPrompt() {
        // Use IDs that are large numbers (e.g. auto-increment database IDs)
        val items = listOf(
            ClothingItem(id = 100500, category = "Top", color = "Red", season = "Spring", comfortLevel = 2),
            ClothingItem(id = 200600, category = "Bottom", color = "Gray", season = "Fall", comfortLevel = 3)
        )
        val indexToItem = items.mapIndexed { index, item -> index to item }.toMap()
        val prompt = invokeBuildPrompt(indexToItem)

        assertFalse("Database ID 100500 must not appear", prompt.contains("100500"))
        assertFalse("Database ID 200600 must not appear", prompt.contains("200600"))
    }

    // -----------------------------------------------------------------------
    // buildPrompt: No user-identifying data
    // -----------------------------------------------------------------------

    @Test
    fun buildPrompt_doesNotContainEmailPatterns() {
        val indexToItem = mapOf(
            0 to ClothingItem(id = 1, category = "Top", color = "Black", season = "Summer", comfortLevel = 3)
        )
        val prompt = invokeBuildPrompt(indexToItem)

        // No email patterns
        assertFalse("Prompt must not contain email-like patterns", prompt.contains("@"))
    }

    @Test
    fun buildPrompt_doesNotContainAuthTokenPatterns() {
        val indexToItem = mapOf(
            0 to ClothingItem(id = 1, category = "Top", color = "Black", season = "Summer", comfortLevel = 3)
        )
        val prompt = invokeBuildPrompt(indexToItem)

        // Auth tokens typically contain "Bearer" or "token" — only "Bearer" in Authorization header,
        // which is in callApi, not in the prompt itself
        assertFalse("Prompt must not contain 'Bearer'", prompt.contains("Bearer"))
        assertFalse("Prompt must not contain 'token'", prompt.lowercase().contains("token"))
    }

    @Test
    fun buildPrompt_doesNotContainTimestamps() {
        val indexToItem = mapOf(
            0 to ClothingItem(id = 1, category = "Top", color = "Black", season = "Summer", comfortLevel = 3)
        )
        val prompt = invokeBuildPrompt(indexToItem)

        // ISO timestamps like 2024-01-15T10:30:00
        val isoTimestampPattern = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}""")
        assertFalse(
            "Prompt must not contain ISO timestamps",
            isoTimestampPattern.containsMatchIn(prompt)
        )

        // Unix timestamps (10+ digit numbers)
        val unixTimestampPattern = Regex("""\b\d{10,}\b""")
        assertFalse(
            "Prompt must not contain Unix timestamps",
            unixTimestampPattern.containsMatchIn(prompt)
        )
    }

    @Test
    fun buildPrompt_doesNotContainUserIdField() {
        val indexToItem = mapOf(
            0 to ClothingItem(id = 1, category = "Top", color = "Black", season = "Summer", comfortLevel = 3)
        )
        val prompt = invokeBuildPrompt(indexToItem)

        assertFalse("Prompt must not contain 'user_id'", prompt.lowercase().contains("user_id"))
        assertFalse("Prompt must not contain 'userId'", prompt.contains("userId"))
        assertFalse("Prompt must not contain 'user id'", prompt.lowercase().contains("user id"))
    }

    @Test
    fun buildPrompt_doesNotContainDeviceInfo() {
        val indexToItem = mapOf(
            0 to ClothingItem(id = 1, category = "Top", color = "Black", season = "Summer", comfortLevel = 3)
        )
        val prompt = invokeBuildPrompt(indexToItem)

        assertFalse("Prompt must not contain 'device'", prompt.lowercase().contains("device"))
        assertFalse("Prompt must not contain 'android'", prompt.lowercase().contains("android"))
        assertFalse("Prompt must not contain 'IMEI'", prompt.contains("IMEI"))
    }

    // -----------------------------------------------------------------------
    // buildPrompt: Only safe clothing attributes present
    // -----------------------------------------------------------------------

    @Test
    fun buildPrompt_containsOnlySafeClothingAttributes() {
        val items = listOf(
            ClothingItem(
                id = 777,
                category = "Top",
                color = "Navy",
                season = "Winter",
                comfortLevel = 4,
                fit = "Relaxed",
                imageUrl = "https://storage.example.com/users/abc123/images/shirt.jpg"
            )
        )
        val indexToItem = items.mapIndexed { index, item -> index to item }.toMap()
        val prompt = invokeBuildPrompt(indexToItem)

        // Safe attributes SHOULD be present
        assertTrue("Category should be in prompt", prompt.contains("Top"))
        assertTrue("Color should be in prompt", prompt.contains("Navy"))
        assertTrue("Season should be in prompt", prompt.contains("Winter"))
        assertTrue("Comfort level should be in prompt", prompt.contains("4"))
        assertTrue("Fit should be in prompt", prompt.contains("Relaxed"))

        // imageUrl must NOT leak into the prompt
        assertFalse("imageUrl must not appear in prompt", prompt.contains("storage.example.com"))
        assertFalse("imageUrl path must not appear in prompt", prompt.contains("abc123"))
        assertFalse("imageUrl must not appear in prompt", prompt.contains("shirt.jpg"))
        assertFalse("Real ID 777 must not appear in prompt", prompt.contains("777"))
    }

    @Test
    fun buildPrompt_imageUrlWithUserPath_neverLeaks() {
        val item = ClothingItem(
            id = 5,
            category = "Bottom",
            color = "Black",
            season = "All",
            comfortLevel = 3,
            fit = "Fitted",
            imageUrl = "/data/user/0/com.fitgpt.app/files/user_12345/pants.png"
        )
        val indexToItem = mapOf(0 to item)
        val prompt = invokeBuildPrompt(indexToItem)

        assertFalse("Local file path must not appear", prompt.contains("/data/user"))
        assertFalse("User-specific path must not appear", prompt.contains("user_12345"))
        assertFalse("Image filename must not appear", prompt.contains("pants.png"))
    }

    // -----------------------------------------------------------------------
    // buildItemPrompt: Same PII safety checks
    // -----------------------------------------------------------------------

    @Test
    fun buildItemPrompt_doesNotContainItemId() {
        val item = ClothingItem(id = 555, category = "Shoes", color = "White", season = "Spring", comfortLevel = 5)
        val prompt = invokeBuildItemPrompt(item)

        assertFalse("Item ID 555 must not appear in item prompt", prompt.contains("555"))
    }

    @Test
    fun buildItemPrompt_doesNotContainImageUrl() {
        val item = ClothingItem(
            id = 10,
            category = "Outerwear",
            color = "Brown",
            season = "Winter",
            comfortLevel = 3,
            imageUrl = "https://cdn.example.com/u/user42/jacket.jpg"
        )
        val prompt = invokeBuildItemPrompt(item)

        assertFalse("imageUrl domain must not appear", prompt.contains("cdn.example.com"))
        assertFalse("imageUrl user path must not appear", prompt.contains("user42"))
        assertFalse("imageUrl filename must not appear", prompt.contains("jacket.jpg"))
    }

    @Test
    fun buildItemPrompt_containsOnlySafeAttributes() {
        val item = ClothingItem(
            id = 999,
            category = "Accessory",
            color = "Gold",
            season = "All",
            comfortLevel = 5,
            fit = "Regular",
            imageUrl = "https://example.com/secret-path/img.png"
        )
        val prompt = invokeBuildItemPrompt(item)

        // Safe attributes present
        assertTrue("Category should be in prompt", prompt.contains("Accessory"))
        assertTrue("Color should be in prompt", prompt.contains("Gold"))
        assertTrue("Season should be in prompt", prompt.contains("All"))
        assertTrue("Comfort level should be in prompt", prompt.contains("5"))

        // Unsafe fields absent
        assertFalse("Item ID must not appear", prompt.contains("999"))
        assertFalse("imageUrl must not appear", prompt.contains("secret-path"))
    }

    @Test
    fun buildItemPrompt_doesNotContainEmailOrAuthData() {
        val item = ClothingItem(id = 1, category = "Top", color = "Red", season = "Summer", comfortLevel = 3)
        val prompt = invokeBuildItemPrompt(item)

        assertFalse("No @ symbol in prompt", prompt.contains("@"))
        assertFalse("No Bearer in prompt", prompt.contains("Bearer"))
        assertFalse("No token reference in prompt", prompt.lowercase().contains("token"))
    }

    @Test
    fun buildItemPrompt_doesNotContainTimestamps() {
        val item = ClothingItem(id = 1, category = "Top", color = "Blue", season = "Fall", comfortLevel = 2)
        val prompt = invokeBuildItemPrompt(item)

        val isoTimestampPattern = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}""")
        assertFalse("No ISO timestamps", isoTimestampPattern.containsMatchIn(prompt))
    }

    // -----------------------------------------------------------------------
    // UserPreferences sanitization: only style fields in prompts
    // -----------------------------------------------------------------------

    @Test
    fun buildPrompt_includesOnlySafePreferenceFields() {
        val prefs = UserPreferences(
            bodyType = "Slim",
            stylePreference = "Formal",
            comfortPreference = 2,
            preferredSeasons = listOf("Winter", "Fall"),
            accessibilityModeEnabled = true
        )
        val indexToItem = mapOf(
            0 to ClothingItem(id = 1, category = "Top", color = "White", season = "Winter", comfortLevel = 3)
        )
        val prompt = invokeBuildPrompt(indexToItem, prefs)

        // Safe preference fields present
        assertTrue("Body type should be in prompt", prompt.contains("Slim"))
        assertTrue("Style preference should be in prompt", prompt.contains("Formal"))
        assertTrue("Comfort preference should be in prompt", prompt.contains("2"))
        assertTrue("Preferred season 'Winter' should be in prompt", prompt.contains("Winter"))
        assertTrue("Preferred season 'Fall' should be in prompt", prompt.contains("Fall"))

        // accessibilityModeEnabled is not a style attribute and should not be sent
        assertFalse(
            "accessibilityModeEnabled must not appear in prompt",
            prompt.lowercase().contains("accessibility")
        )
    }

    @Test
    fun buildItemPrompt_includesOnlySafePreferenceFields() {
        val prefs = UserPreferences(
            bodyType = "Plus-size",
            stylePreference = "Streetwear",
            comfortPreference = 5,
            preferredSeasons = listOf("Summer"),
            accessibilityModeEnabled = true
        )
        val item = ClothingItem(id = 1, category = "Top", color = "Black", season = "Summer", comfortLevel = 4)
        val prompt = invokeBuildItemPrompt(item, prefs)

        assertTrue("Body type should be in prompt", prompt.contains("Plus-size"))
        assertTrue("Style preference should be in prompt", prompt.contains("Streetwear"))

        assertFalse(
            "accessibilityModeEnabled must not appear",
            prompt.lowercase().contains("accessibility")
        )
    }

    // -----------------------------------------------------------------------
    // Edge cases
    // -----------------------------------------------------------------------

    @Test
    fun buildPrompt_emptyImageUrl_doesNotLeakNullText() {
        val item = ClothingItem(
            id = 1, category = "Top", color = "Red", season = "Summer",
            comfortLevel = 3, imageUrl = null
        )
        val indexToItem = mapOf(0 to item)
        val prompt = invokeBuildPrompt(indexToItem)

        assertFalse("null text must not appear", prompt.contains("null"))
    }

    @Test
    fun buildPrompt_manyItems_noRealIdsLeak() {
        val items = (100..120).map { id ->
            ClothingItem(id = id, category = "Top", color = "Black", season = "All", comfortLevel = 3)
        }
        val indexToItem = items.mapIndexed { index, item -> index to item }.toMap()
        val prompt = invokeBuildPrompt(indexToItem)

        // None of the real IDs (100-120) should appear in the prompt as item identifiers.
        // The prompt may contain numbers like 100-120 in other contexts (e.g. scoring rules),
        // but the wardrobe section should only use 0-20 indices.
        val wardrobeSection = prompt.substringAfter("WARDROBE").substringBefore("The ONLY valid item IDs")
        for (id in 100..120) {
            assertFalse(
                "Real ID $id must not appear in wardrobe section",
                wardrobeSection.contains("$id |")
            )
        }
    }

    @Test
    fun buildPrompt_specialCharactersInColor_arePassedSafely() {
        val item = ClothingItem(id = 1, category = "Top", color = "Light Blue", season = "Summer", comfortLevel = 3)
        val indexToItem = mapOf(0 to item)
        val prompt = invokeBuildPrompt(indexToItem)

        assertTrue("Color with space should be preserved", prompt.contains("Light Blue"))
        assertFalse("No ID leak", prompt.contains("1 |"))
    }

    @Test
    fun buildItemPrompt_fitValueNotIncludedInItemPrompt() {
        // buildItemPrompt currently sends category, color, season, comfort but NOT fit
        // This is acceptable - verifying no extra fields leak
        val item = ClothingItem(
            id = 50, category = "Bottom", color = "Navy", season = "Fall",
            comfortLevel = 4, fit = "Oversized", imageUrl = "https://img.example.com/50.jpg"
        )
        val prompt = invokeBuildItemPrompt(item)

        assertFalse("ID 50 must not appear", prompt.contains("50"))
        assertFalse("imageUrl must not appear", prompt.contains("img.example.com"))
    }

    // -----------------------------------------------------------------------
    // Verify anonymous index mapping correctness
    // -----------------------------------------------------------------------

    @Test
    fun buildPrompt_anonymousIndicesAreSequential() {
        val items = listOf(
            ClothingItem(id = 500, category = "Top", color = "Black", season = "Summer", comfortLevel = 3),
            ClothingItem(id = 600, category = "Bottom", color = "White", season = "Winter", comfortLevel = 4),
            ClothingItem(id = 700, category = "Shoes", color = "Brown", season = "All", comfortLevel = 5)
        )
        val indexToItem = items.mapIndexed { index, item -> index to item }.toMap()
        val prompt = invokeBuildPrompt(indexToItem)

        // The wardrobe lines should start with 0, 1, 2
        val wardrobeSection = prompt.substringAfter("WARDROBE").substringBefore("The ONLY valid")
        assertTrue("Index 0 should be in wardrobe", wardrobeSection.contains("0 |"))
        assertTrue("Index 1 should be in wardrobe", wardrobeSection.contains("1 |"))
        assertTrue("Index 2 should be in wardrobe", wardrobeSection.contains("2 |"))

        // Real IDs should not be in wardrobe lines
        assertFalse("Real ID 500 must not be in wardrobe", wardrobeSection.contains("500"))
        assertFalse("Real ID 600 must not be in wardrobe", wardrobeSection.contains("600"))
        assertFalse("Real ID 700 must not be in wardrobe", wardrobeSection.contains("700"))
    }

    @Test
    fun buildPrompt_validItemIdsLineUsesAnonymousIndices() {
        val items = listOf(
            ClothingItem(id = 42, category = "Top", color = "Red", season = "Spring", comfortLevel = 2),
            ClothingItem(id = 84, category = "Bottom", color = "Blue", season = "Summer", comfortLevel = 3)
        )
        val indexToItem = items.mapIndexed { index, item -> index to item }.toMap()
        val prompt = invokeBuildPrompt(indexToItem)

        // The "valid item IDs" line should list 0, 1 not 42, 84
        val validIdsLine = prompt.lines().first { it.contains("ONLY valid item IDs") }
        assertTrue("Valid IDs line should contain '0'", validIdsLine.contains("0"))
        assertTrue("Valid IDs line should contain '1'", validIdsLine.contains("1"))
        assertFalse("Valid IDs line must not contain real ID 42", validIdsLine.contains("42"))
        assertFalse("Valid IDs line must not contain real ID 84", validIdsLine.contains("84"))
    }
}
