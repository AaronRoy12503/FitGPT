package com.fitgpt.app.ai

import com.fitgpt.app.BuildConfig
import com.fitgpt.app.data.model.ClothingCategory
import com.fitgpt.app.data.model.ClothingItem
import com.fitgpt.app.data.model.OutfitRecommendation
import com.fitgpt.app.data.model.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// DATA SAFETY: This service sends only non-identifying clothing attributes
// (category, color, season, comfortLevel, fit) and general style preferences
// (bodyType, stylePreference, comfortPreference, preferredSeasons) to the Groq API.
// Real item IDs are replaced with anonymous indices before sending and mapped back
// on response. No user IDs, names, emails, device info, or timestamps are transmitted.
class GroqRecommendationService {

    // API key sourced from BuildConfig (injected at build time, not hardcoded)
    private val apiKey: String = BuildConfig.GROQ_API_KEY

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val isAvailable: Boolean get() = apiKey.isNotBlank()

    companion object {
        private const val API_URL = "https://api.groq.com/openai/v1/chat/completions"
        private const val MODEL = "llama-3.3-70b-versatile"
        private const val TEMPERATURE = 0.7
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }

    suspend fun recommend(
        items: List<ClothingItem>,
        preferences: UserPreferences
    ): List<OutfitRecommendation> {
        if (!isAvailable || items.isEmpty()) return emptyList()

        // Map real item IDs to anonymous indices so no database IDs leave the device
        val indexToItem = items.mapIndexed { index, item -> index to item }.toMap()
        val prompt = buildPrompt(indexToItem, preferences)
        val text = callApi(prompt) ?: return emptyList()
        return parseResponse(text, indexToItem)
    }

    suspend fun generateItemExplanation(
        item: ClothingItem,
        preferences: UserPreferences
    ): String? {
        if (!isAvailable) return null

        val prompt = buildItemPrompt(item, preferences)
        return callApi(prompt)?.trim()
    }

    private suspend fun callApi(prompt: String): String? = withContext(Dispatchers.IO) {
        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            })
        }

        val body = JSONObject().apply {
            put("model", MODEL)
            put("messages", messages)
            put("temperature", TEMPERATURE)
        }

        val request = Request.Builder()
            .url(API_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw RuntimeException("Groq API error: ${response.code} ${response.message}")
        }

        val responseBody = response.body?.string()
            ?: throw RuntimeException("Groq API returned empty body")

        JSONObject(responseBody)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }

    private fun buildPrompt(
        indexToItem: Map<Int, ClothingItem>,
        preferences: UserPreferences
    ): String {
        // Only send non-identifying attributes; use anonymous indices instead of real IDs
        val itemLines = indexToItem.entries.joinToString("\n") { (index, item) ->
            "$index | ${item.category} | ${item.color} | ${item.season} | ${item.comfortLevel} | ${item.fit}"
        }

        return """
You are a fashion stylist AI. Given a wardrobe and user preferences, recommend exactly 5 unique outfit combinations.

USER PREFERENCES:
- Body type: ${preferences.bodyType}
- Style: ${preferences.stylePreference}
- Comfort preference: ${preferences.comfortPreference}/5
- Preferred seasons: ${preferences.preferredSeasons.joinToString(", ")}

VALID CATEGORIES: ${ClothingCategory.ALL.joinToString(", ")}

WARDROBE (ID | Category | Color | Season | Comfort | Fit):
$itemLines

The ONLY valid item IDs are: ${indexToItem.keys.joinToString(", ")}
Do NOT invent or use any IDs not listed above.

For each outfit, respond in EXACTLY this format (separate outfits with a blank line):

OUTFIT: id1, id2, id3
SCORE: 2.5
EXPLANATION: A brief reason why this outfit works well together.
ITEM_DETAILS: id1: why this item fits, id2: why this item fits, id3: why this item fits

Rules:
- ONLY use item IDs from the wardrobe above — never invent new IDs
- Recommend exactly 5 outfits, each one must be a unique combination (no duplicate outfits)
- Score from 0.0 to 3.0
- Each outfit should have 2-5 items
- COLOR COORDINATION IS THE TOP PRIORITY: build outfits around polished color palettes
  - Prefer neutral bases (black, white, gray, navy, beige) with 1 accent color
  - Analogous colors (next to each other on color wheel) create harmonious looks
  - Complementary colors (opposite on color wheel) create vibrant contrast
  - Monochromatic outfits with tonal variation look sophisticated
  - Avoid pairing 3+ unrelated bright colors without a neutral anchor
- Also consider season matching, comfort, and the user's style preference
- BODY TYPE FIT is important — tailor recommendations to flatter the user's proportions:
  - Slim: fitted cuts accentuate the frame; oversized adds volume; layering with outerwear adds dimension
  - Athletic: fitted cuts highlight the build; regular fits suit broad shoulders; avoid oversized on top
  - Plus-size: relaxed fits drape comfortably; structured oversized pieces create shape; comfort 4+ preferred
  - Average: balanced proportions suit most fits; focus on other factors
- GARMENT FIT matters: each item has a Fit value (Fitted, Regular, Relaxed, Oversized) — match it to the user's body type
- In EXPLANATION, explicitly describe why the colors work together and how the outfit flatters the user's body type
- In ITEM_DETAILS, mention how each item's color contributes to the outfit's palette and how it suits the user's body type
""".trim()
    }

    private fun buildItemPrompt(
        item: ClothingItem,
        preferences: UserPreferences
    ): String {
        return """
You are a fashion stylist AI. Give a brief 1-2 sentence explanation of how this clothing item fits the user's style.

ITEM: ${item.category}, ${item.color}, ${item.season} season, comfort ${item.comfortLevel}/5

USER PREFERENCES:
- Body type: ${preferences.bodyType}
- Style: ${preferences.stylePreference}
- Comfort preference: ${preferences.comfortPreference}/5
- Preferred seasons: ${preferences.preferredSeasons.joinToString(", ")}

Respond with ONLY the explanation, no labels or prefixes.
""".trim()
    }

    private fun parseResponse(
        text: String,
        indexToItem: Map<Int, ClothingItem>
    ): List<OutfitRecommendation> {
        // indexToItem maps anonymous prompt indices back to real ClothingItem objects
        val itemMap = indexToItem
        val blocks = text.split("\n\n").filter { it.isNotBlank() }
        val results = mutableListOf<OutfitRecommendation>()

        for (block in blocks) {
            try {
                val lines = block.trim().lines()

                val outfitLine = lines.firstOrNull {
                    it.startsWith("OUTFIT:", ignoreCase = true)
                } ?: continue

                val scoreLine = lines.firstOrNull {
                    it.startsWith("SCORE:", ignoreCase = true)
                }

                val explanationLine = lines.firstOrNull {
                    it.startsWith("EXPLANATION:", ignoreCase = true)
                }

                val itemDetailsLine = lines.firstOrNull {
                    it.startsWith("ITEM_DETAILS:", ignoreCase = true)
                }

                val ids = outfitLine.substringAfter(":").trim()
                    .split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                    .distinct()

                val outfitItems = ids.mapNotNull { itemMap[it] }
                if (outfitItems.isEmpty()) continue

                val score = scoreLine
                    ?.substringAfter(":")
                    ?.trim()
                    ?.toDoubleOrNull()
                    ?.coerceIn(0.0, 3.0)
                    ?: 1.5

                val explanation = explanationLine
                    ?.substringAfter(":")
                    ?.trim()
                    ?: "AI-recommended outfit combination."

                // Parse explanations keyed by anonymous index, then remap to real item IDs
                val indexedExplanations = parseItemDetails(itemDetailsLine, itemMap)
                val itemExplanations = indexedExplanations.mapNotNull { (index, reason) ->
                    itemMap[index]?.let { it.id to reason }
                }.toMap()

                results.add(
                    OutfitRecommendation(
                        items = outfitItems,
                        score = score,
                        explanation = explanation,
                        itemExplanations = itemExplanations
                    )
                )
            } catch (_: Exception) {
                // Skip malformed blocks silently
            }
        }

        return results
            .distinctBy { rec -> rec.items.map { it.id }.sorted() }
            .sortedByDescending { it.score }
            .take(5)
    }

    private fun parseItemDetails(
        line: String?,
        itemMap: Map<Int, ClothingItem>
    ): Map<Int, String> {
        if (line == null) return emptyMap()

        val content = line.substringAfter(":", missingDelimiterValue = "").trim()
        if (content.isBlank()) return emptyMap()

        val result = mutableMapOf<Int, String>()

        // Pattern: "id: reason" segments separated by commas — but reason text may
        // itself contain commas, so we split on the pattern ", <digits>:" which marks
        // the start of the next item entry.
        val segmentPattern = Regex("""(\d+)\s*:\s*""")
        val matchResults = segmentPattern.findAll(content).toList()

        for (i in matchResults.indices) {
            val id = matchResults[i].groupValues[1].toIntOrNull() ?: continue
            if (id !in itemMap) continue

            val reasonStart = matchResults[i].range.last + 1
            val reasonEnd = if (i + 1 < matchResults.size) matchResults[i + 1].range.first else content.length

            val reason = content.substring(reasonStart, reasonEnd)
                .trim()
                .trimEnd(',')
                .trim()

            if (reason.isNotBlank()) {
                result[id] = reason
            }
        }

        return result
    }
}
