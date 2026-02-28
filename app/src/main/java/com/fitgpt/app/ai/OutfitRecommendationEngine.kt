package com.fitgpt.app.ai

import com.fitgpt.app.data.model.ClothingCategory
import com.fitgpt.app.data.model.ClothingItem
import com.fitgpt.app.data.model.OutfitRecommendation
import com.fitgpt.app.data.model.UserPreferences

class OutfitRecommendationEngine {

    fun recommend(
        items: List<ClothingItem>,
        preferences: UserPreferences,
        recentlyShown: Set<Set<Int>> = emptySet()
    ): List<OutfitRecommendation> {
        if (items.isEmpty()) return emptyList()

        val tops = items.filter { it.category.equals(ClothingCategory.TOP, ignoreCase = true) }
        val bottoms = items.filter { it.category.equals(ClothingCategory.BOTTOM, ignoreCase = true) }
        val outerwear = items.filter { it.category.equals(ClothingCategory.OUTERWEAR, ignoreCase = true) }
        val shoes = items.filter { it.category.equals(ClothingCategory.SHOES, ignoreCase = true) }
        val accessories = items.filter { it.category.equals(ClothingCategory.ACCESSORY, ignoreCase = true) }

        val allCombinations = buildOutfitCombinations(tops, bottoms, outerwear, shoes, accessories)
            .distinctBy { combo -> combo.map { it.id }.sorted() }

        // If we couldn't form any multi-item outfits, fall back to scoring individual items
        if (allCombinations.isEmpty()) {
            return items.map { item ->
                val score = scoreItem(item, preferences)
                val perItem = mapOf(item.id to generateItemExplanation(item, preferences))
                OutfitRecommendation(
                    items = listOf(item),
                    score = score,
                    explanation = generateExplanation(listOf(item), score, preferences),
                    itemExplanations = perItem
                )
            }
                .sortedByDescending { it.score }
                .take(MAX_RECOMMENDATIONS)
        }

        // Filter out recently shown outfits
        val fresh = allCombinations.filter { combo ->
            combo.map { it.id }.toSet() !in recentlyShown
        }

        // If every combination was recently shown, clear history and use all
        val outfitCombinations = fresh.ifEmpty { allCombinations }

        val scored = outfitCombinations
            .map { outfit ->
                val score = scoreOutfit(outfit, preferences)
                val perItem = outfit.associate { item ->
                    item.id to generateItemExplanation(item, preferences)
                }
                OutfitRecommendation(
                    items = outfit,
                    score = score,
                    explanation = generateExplanation(outfit, score, preferences),
                    itemExplanations = perItem
                )
            }

        // Add controlled randomness: pick from a wider pool so each refresh feels different.
        // Take more candidates than needed, then shuffle within score tiers to vary the results.
        return pickDiverseResults(scored, MAX_RECOMMENDATIONS)
    }

    /**
     * Selects [count] recommendations that balance quality (score) with variety.
     * Groups candidates into score tiers and shuffles within each tier so that
     * equally-good outfits rotate across refreshes instead of always returning
     * the same deterministic top-N.
     */
    private fun pickDiverseResults(
        candidates: List<OutfitRecommendation>,
        count: Int
    ): List<OutfitRecommendation> {
        if (candidates.size <= count) return candidates.sortedByDescending { it.score }

        // Sort descending by score, then partition into tiers (buckets of ~0.15 score width)
        val sorted = candidates.sortedByDescending { it.score }
        val tierWidth = 0.15
        val tiers = mutableListOf<MutableList<OutfitRecommendation>>()

        for (rec in sorted) {
            val lastTier = tiers.lastOrNull()
            if (lastTier == null || (lastTier.first().score - rec.score) > tierWidth) {
                tiers.add(mutableListOf(rec))
            } else {
                lastTier.add(rec)
            }
        }

        // Shuffle within each tier so equally-scored outfits rotate
        tiers.forEach { it.shuffle() }

        // Flatten and take the top N
        return tiers.flatten().take(count)
    }

    fun generateItemExplanation(item: ClothingItem, preferences: UserPreferences): String {
        // Collect candidate reasons — most specific first so take(2) keeps the best
        val candidates = mutableListOf<String>()

        // Style and body type are conditional and item-specific — prioritize them
        val styleNote = styleNote(item, preferences)
        if (styleNote != null) candidates.add(styleNote)

        val fitNote = bodyTypeFitNote(item, preferences)
        if (fitNote != null) candidates.add(fitNote)

        // Season
        val seasonScore = seasonMatchScore(item, preferences)
        when {
            seasonScore >= 1.0 -> candidates.add(
                "Perfect for ${item.season.lowercase()}"
            )
            item.season.equals("All", ignoreCase = true) -> candidates.add(
                "Versatile all-season piece"
            )
            else -> candidates.add(
                "Suited for ${item.season.lowercase()} weather"
            )
        }

        // Comfort
        val comfortDiff = item.comfortLevel - preferences.comfortPreference
        when {
            comfortDiff >= 1 -> candidates.add("exceeds your comfort preference")
            comfortDiff == 0 -> candidates.add("matches your comfort level exactly")
            comfortDiff == -1 -> candidates.add("slightly below your usual comfort preference")
            else -> candidates.add("prioritizes style over comfort")
        }

        // Pick the top 2 reasons, then always append color character
        val picked = candidates.take(2).toMutableList()
        picked.add("${item.color.lowercase()} adds ${colorCharacter(item.color)}")

        return picked.joinToString(". ") + "."
    }

    // --- Scoring ---

    internal fun scoreItem(item: ClothingItem, preferences: UserPreferences): Double {
        val seasonScore = seasonMatchScore(item, preferences) * WEIGHT_SEASON
        val comfortScore = comfortMatchScore(item, preferences) * WEIGHT_COMFORT
        val styleScore = styleMatchScore(item, preferences) * WEIGHT_STYLE
        val fitScore = bodyTypeFitScore(item, preferences) * WEIGHT_FIT
        return seasonScore + comfortScore + styleScore + fitScore
    }

    internal fun scoreOutfit(
        outfit: List<ClothingItem>,
        preferences: UserPreferences
    ): Double {
        if (outfit.isEmpty()) return 0.0

        val avgItemScore = outfit.sumOf { scoreItem(it, preferences) } / outfit.size
        val harmonyBonus = colorHarmonyBonus(outfit)
        val coverageBonus = categoryDiversityBonus(outfit)

        return avgItemScore + (harmonyBonus * WEIGHT_HARMONY) + (coverageBonus * WEIGHT_COVERAGE)
    }

    // --- Season ---

    internal fun seasonMatchScore(item: ClothingItem, preferences: UserPreferences): Double {
        if (item.season.equals("All", ignoreCase = true)) return 0.8
        return if (preferences.preferredSeasons.any { it.equals(item.season, ignoreCase = true) }) {
            1.0
        } else {
            0.2
        }
    }

    // --- Comfort ---

    internal fun comfortMatchScore(item: ClothingItem, preferences: UserPreferences): Double {
        val diff = Math.abs(item.comfortLevel - preferences.comfortPreference)
        return when (diff) {
            0 -> 1.0
            1 -> 0.7
            2 -> 0.4
            else -> 0.1
        }
    }

    // --- Style ---

    internal fun styleMatchScore(item: ClothingItem, preferences: UserPreferences): Double {
        val styleCategoryMap = mapOf(
            "Casual" to setOf(ClothingCategory.TOP, ClothingCategory.BOTTOM, ClothingCategory.SHOES, ClothingCategory.ACCESSORY),
            "Formal" to setOf(ClothingCategory.TOP, ClothingCategory.BOTTOM, ClothingCategory.OUTERWEAR, ClothingCategory.SHOES),
            "Sporty" to setOf(ClothingCategory.TOP, ClothingCategory.BOTTOM, ClothingCategory.SHOES),
            "Streetwear" to setOf(ClothingCategory.TOP, ClothingCategory.BOTTOM, ClothingCategory.OUTERWEAR, ClothingCategory.SHOES, ClothingCategory.ACCESSORY)
        )

        val categories = styleCategoryMap[preferences.stylePreference]
        return if (categories != null && categories.any { it.equals(item.category, ignoreCase = true) }) {
            0.8
        } else {
            0.5
        }
    }

    // --- Body type fit ---

    internal fun bodyTypeFitScore(item: ClothingItem, preferences: UserPreferences): Double {
        val category = item.category.lowercase()
        val fit = item.fit.lowercase()

        // Base score from body type + category
        val baseScore = when (preferences.bodyType.lowercase()) {
            "slim" -> when (category) {
                ClothingCategory.OUTERWEAR.lowercase() -> 0.9   // layering adds visual dimension
                ClothingCategory.ACCESSORY.lowercase() -> 0.85  // draws the eye, adds interest
                else -> 0.7
            }
            "athletic" -> when (category) {
                ClothingCategory.TOP.lowercase() -> 0.9         // accommodates broader shoulders
                ClothingCategory.SHOES.lowercase() -> 0.85      // sporty footwear complements build
                ClothingCategory.BOTTOM.lowercase() -> 0.8
                else -> 0.7
            }
            "plus-size" -> {
                val comfortBoost = if (item.comfortLevel >= 4) 0.1 else 0.0
                val base = when (category) {
                    ClothingCategory.OUTERWEAR.lowercase() -> 0.9
                    ClothingCategory.ACCESSORY.lowercase() -> 0.85
                    ClothingCategory.TOP.lowercase() -> 0.8
                    ClothingCategory.BOTTOM.lowercase() -> 0.75
                    else -> 0.7
                }
                (base + comfortBoost).coerceAtMost(1.0)
            }
            else -> 0.7 // "average" or unrecognized — neutral baseline
        }

        // Garment fit modifier — adjusts score based on body type + fit pairing
        val fitBonus = garmentFitBonus(fit, preferences.bodyType.lowercase())

        return (baseScore + fitBonus).coerceIn(0.0, 1.0)
    }

    internal fun garmentFitBonus(fit: String, bodyType: String): Double {
        return when (bodyType) {
            "slim" -> when (fit) {
                "fitted" -> 0.1       // accentuates a slim frame
                "oversized" -> 0.05   // adds visual volume
                "relaxed" -> 0.0
                else -> 0.0           // "regular" — neutral
            }
            "athletic" -> when (fit) {
                "fitted" -> 0.1       // highlights athletic build
                "regular" -> 0.05     // clean lines suit broad shoulders
                "oversized" -> -0.05  // can look bulky on athletic frames
                else -> 0.0
            }
            "plus-size" -> when (fit) {
                "relaxed" -> 0.1      // comfortable and flattering drape
                "oversized" -> 0.05   // structured oversized pieces create shape
                "fitted" -> -0.05     // can feel restrictive
                else -> 0.0
            }
            else -> 0.0 // "average" — all fits work equally
        }
    }

    // --- Color harmony ---

    internal fun colorHarmonyBonus(outfit: List<ClothingItem>): Double {
        if (outfit.size < 2) return 0.0

        val colors = outfit.map { it.color.lowercase() }
        val neutrals = setOf("black", "white", "gray", "grey", "beige", "navy", "tan", "cream", "khaki", "ivory")
        val neutralCount = colors.count { it in neutrals }
        val accentColors = colors.filter { it !in neutrals }
        val accentCount = accentColors.size

        // Monochromatic: all non-neutral colors are the same
        val uniqueAccents = accentColors.toSet()
        if (uniqueAccents.size == 1 && neutralCount >= 1) return 1.0

        // All neutrals: polished and safe
        if (neutralCount == colors.size) return 0.7

        // Neutral base with one accent: classic polished look
        if (neutralCount >= 1 && accentCount == 1) return 1.0

        // Neutral base with two accents: check if they're related
        if (neutralCount >= 1 && accentCount == 2) {
            val pair = accentColors.map { it.lowercase() }.sorted()
            if (areAnalogous(pair[0], pair[1])) return 0.95
            if (areComplementary(pair[0], pair[1])) return 0.9
            // Unrelated accents with neutral base is still okay
            return 0.7
        }

        // No neutrals: check color relationships between accents
        if (neutralCount == 0 && accentCount == 2) {
            val pair = accentColors.map { it.lowercase() }.sorted()
            if (areAnalogous(pair[0], pair[1])) return 0.85
            if (areComplementary(pair[0], pair[1])) return 0.8
            if (sameColorTemperature(pair[0], pair[1])) return 0.7
            return 0.4
        }

        // Three+ non-neutral colors with no neutral base: risky
        if (accentCount > 2 && neutralCount == 0) return 0.2

        // Fallback: neutral base with 3+ accents
        if (neutralCount >= 1 && accentCount > 2) return 0.5

        return 0.5
    }

    private fun areComplementary(a: String, b: String): Boolean {
        val pairs = setOf(
            setOf("blue", "orange"), setOf("red", "green"), setOf("yellow", "purple"),
            setOf("teal", "red"), setOf("pink", "green"), setOf("coral", "teal")
        )
        return setOf(a, b) in pairs
    }

    private fun areAnalogous(a: String, b: String): Boolean {
        val groups = listOf(
            setOf("red", "orange", "coral", "pink"),
            setOf("orange", "yellow", "gold"),
            setOf("yellow", "green", "lime"),
            setOf("green", "teal", "olive"),
            setOf("blue", "teal", "navy"),
            setOf("blue", "purple", "indigo"),
            setOf("purple", "pink", "magenta"),
            setOf("brown", "orange", "tan", "rust")
        )
        return groups.any { group -> a in group && b in group }
    }

    private fun sameColorTemperature(a: String, b: String): Boolean {
        val warm = setOf("red", "orange", "yellow", "coral", "pink", "gold", "rust", "brown", "tan", "peach")
        val cool = setOf("blue", "green", "purple", "teal", "navy", "indigo", "mint", "olive", "magenta")
        return (a in warm && b in warm) || (a in cool && b in cool)
    }

    internal fun colorHarmonyLabel(outfit: List<ClothingItem>): String {
        if (outfit.size < 2) return ""

        val colors = outfit.map { it.color.lowercase() }
        val neutrals = setOf("black", "white", "gray", "grey", "beige", "navy", "tan", "cream", "khaki", "ivory")
        val accentColors = colors.filter { it !in neutrals }
        val neutralCount = colors.count { it in neutrals }

        val uniqueAccents = accentColors.toSet()
        if (uniqueAccents.size == 1 && neutralCount >= 1) {
            return "Monochromatic palette — ${uniqueAccents.first()} with neutral base creates a cohesive, polished look"
        }
        if (neutralCount == colors.size) {
            return "All-neutral palette — ${colors.joinToString(", ")} gives a clean, sophisticated foundation"
        }
        if (neutralCount >= 1 && accentColors.size == 1) {
            return "${accentColors.first().replaceFirstChar { it.uppercase() }} pops against ${colors.filter { it in neutrals }.joinToString("/")} — a classic, polished combination"
        }
        if (accentColors.size == 2) {
            val pair = accentColors.sorted()
            if (areAnalogous(pair[0], pair[1])) {
                return "${pair[0].replaceFirstChar { it.uppercase() }} and ${pair[1]} are analogous colors — they sit next to each other on the color wheel for a harmonious blend"
            }
            if (areComplementary(pair[0], pair[1])) {
                return "${pair[0].replaceFirstChar { it.uppercase() }} and ${pair[1]} are complementary — opposite on the color wheel for a vibrant, balanced contrast"
            }
            if (sameColorTemperature(pair[0], pair[1])) {
                val temp = if (pair[0] in setOf("red", "orange", "yellow", "coral", "pink", "gold", "rust", "brown", "tan", "peach")) "warm" else "cool"
                return "Both ${pair[0]} and ${pair[1]} are $temp tones — a unified color temperature for a cohesive feel"
            }
        }
        val score = colorHarmonyBonus(outfit)
        return when {
            score >= 0.8 -> "Colors work well together for a coordinated look"
            score >= 0.5 -> "Interesting color mix — adds personality"
            else -> "Bold color combination — consider a neutral anchor piece"
        }
    }

    // --- Category diversity ---

    internal fun categoryDiversityBonus(outfit: List<ClothingItem>): Double {
        val categories = outfit.map { it.category.lowercase() }.toSet()
        val hasTop = ClothingCategory.TOP.lowercase() in categories
        val hasBottom = ClothingCategory.BOTTOM.lowercase() in categories

        return when {
            hasTop && hasBottom && categories.size >= 3 -> 1.0
            hasTop && hasBottom -> 0.8
            hasTop || hasBottom -> 0.4
            else -> 0.2
        }
    }

    // --- Outfit combination builder ---

    private fun buildOutfitCombinations(
        tops: List<ClothingItem>,
        bottoms: List<ClothingItem>,
        outerwear: List<ClothingItem>,
        shoes: List<ClothingItem>,
        accessories: List<ClothingItem>
    ): List<List<ClothingItem>> {
        if (tops.isEmpty() || bottoms.isEmpty()) return emptyList()

        val combos = mutableListOf<List<ClothingItem>>()

        for (top in tops) {
            for (bottom in bottoms) {
                // Base outfit: top + bottom
                val base = listOf(top, bottom)
                combos.add(base)

                // Top + bottom + outerwear
                for (outer in outerwear) {
                    combos.add(base + outer)
                }

                // Top + bottom + shoes
                for (shoe in shoes) {
                    combos.add(base + shoe)

                    // Top + bottom + shoes + outerwear
                    for (outer in outerwear) {
                        combos.add(base + shoe + outer)
                    }
                }

                // Top + bottom + accessory
                for (acc in accessories) {
                    combos.add(base + acc)
                }
            }
        }

        return combos
    }

    // --- Explanation generation ---

    private fun generateExplanation(
        outfit: List<ClothingItem>,
        score: Double,
        preferences: UserPreferences
    ): String {
        val parts = mutableListOf<String>()

        // Lead with color harmony — highest-weighted outfit factor
        val harmonyLabel = colorHarmonyLabel(outfit)
        if (harmonyLabel.isNotBlank()) {
            parts.add(harmonyLabel)
        }

        // Add body type insight only when the outfit has a notable fit advantage
        val fitInsight = when (preferences.bodyType.lowercase()) {
            "slim" -> if (outfit.any { it.category.equals(ClothingCategory.OUTERWEAR, ignoreCase = true) })
                "Layered pieces add depth to a slim frame" else null
            "athletic" -> if (outfit.any { it.category.equals(ClothingCategory.TOP, ignoreCase = true) })
                "Structured tops complement your athletic build" else null
            "plus-size" -> if (outfit.any { it.category.equals(ClothingCategory.OUTERWEAR, ignoreCase = true) })
                "Structured layers create a flattering silhouette"
            else if (outfit.any { it.comfortLevel >= 4 })
                "Comfortable fits flatter your proportions" else null
            else -> null
        }
        if (fitInsight != null) parts.add(fitInsight)

        // Add season or comfort only when noteworthy (avoid generic filler)
        val seasons = outfit.map { it.season }.toSet()
        val allSeasonMatch = seasons.all { it.equals("All", ignoreCase = true) }
        val hasSeasonMismatch = seasons.none { s ->
            s.equals("All", ignoreCase = true) ||
                preferences.preferredSeasons.any { it.equals(s, ignoreCase = true) }
        }
        if (hasSeasonMismatch) {
            parts.add("Consider for ${seasons.joinToString("/").lowercase()} weather")
        }

        val avgComfort = outfit.sumOf { it.comfortLevel }.toDouble() / outfit.size
        if (avgComfort < preferences.comfortPreference - 0.5) {
            parts.add("Trades some comfort for style")
        }

        // Score tier
        val tier = when {
            score >= 2.5 -> "Highly recommended"
            score >= 1.5 -> "Good match"
            else -> "Worth trying"
        }
        parts.add(tier)

        return parts.joinToString(". ") + "."
    }

    private fun styleNote(item: ClothingItem, preferences: UserPreferences): String? {
        return when (preferences.stylePreference.lowercase()) {
            "casual" -> if (item.comfortLevel >= 4) "great casual pick for everyday wear" else null
            "formal" -> if (item.category.equals(ClothingCategory.OUTERWEAR, ignoreCase = true)) "adds a formal finishing touch" else null
            "sporty" -> if (item.comfortLevel >= 4) "comfort-first choice for an active lifestyle" else null
            "streetwear" -> "works well in a streetwear rotation"
            else -> null
        }
    }

    private fun bodyTypeFitNote(item: ClothingItem, preferences: UserPreferences): String? {
        val category = item.category.lowercase()
        val fit = item.fit.lowercase()
        val bodyType = preferences.bodyType.lowercase()

        // Garment fit note takes priority when it's a strong pairing
        val fitNote = when (bodyType) {
            "slim" -> when (fit) {
                "fitted" -> "fitted cut accentuates a slim frame"
                "oversized" -> "oversized silhouette adds volume to a slim build"
                else -> null
            }
            "athletic" -> when (fit) {
                "fitted" -> "fitted cut highlights your athletic build"
                "oversized" -> null // not ideal — skip
                else -> null
            }
            "plus-size" -> when (fit) {
                "relaxed" -> "relaxed fit drapes comfortably"
                "oversized" -> "structured oversized piece creates shape"
                else -> null
            }
            else -> null
        }
        if (fitNote != null) return fitNote

        // Fall back to category-based notes
        return when (bodyType) {
            "slim" -> when (category) {
                ClothingCategory.OUTERWEAR.lowercase() -> "layering adds dimension to a slim frame"
                ClothingCategory.ACCESSORY.lowercase() -> "accessories add visual interest to your silhouette"
                else -> null
            }
            "athletic" -> when (category) {
                ClothingCategory.TOP.lowercase() -> "structured top complements an athletic build"
                ClothingCategory.SHOES.lowercase() -> "sporty footwear pairs well with your build"
                else -> null
            }
            "plus-size" -> when {
                category == ClothingCategory.OUTERWEAR.lowercase() -> "structured outerwear creates a flattering shape"
                category == ClothingCategory.ACCESSORY.lowercase() -> "accessories draw the eye and accent your look"
                item.comfortLevel >= 4 -> "comfortable fit flatters your proportions"
                else -> null
            }
            else -> null
        }
    }

    private fun colorCharacter(color: String): String {
        return when (color.lowercase()) {
            "black" -> "timeless sophistication"
            "white" -> "clean freshness"
            "blue", "navy" -> "calm versatility"
            "red" -> "bold energy"
            "green" -> "natural balance"
            "gray", "grey" -> "understated elegance"
            "beige", "tan", "cream" -> "warm neutrality"
            "yellow" -> "cheerful brightness"
            "orange" -> "vibrant warmth"
            "purple" -> "creative flair"
            "pink" -> "playful softness"
            "brown" -> "earthy grounding"
            else -> "unique character"
        }
    }

    companion object {
        private const val MAX_RECOMMENDATIONS = 5
        const val MAX_HISTORY_SIZE = 50

        // Scoring weights — color harmony is prioritized for a polished look
        internal const val WEIGHT_SEASON = 0.25
        internal const val WEIGHT_COMFORT = 0.20
        internal const val WEIGHT_STYLE = 0.15
        internal const val WEIGHT_FIT = 0.10
        internal const val WEIGHT_HARMONY = 0.20
        internal const val WEIGHT_COVERAGE = 0.10
    }
}
