package com.fitgpt.app.ai

import com.fitgpt.app.data.model.ClothingCategory
import com.fitgpt.app.data.model.ClothingItem
import com.fitgpt.app.data.model.OutfitRecommendation
import com.fitgpt.app.data.model.TemperatureCategory
import com.fitgpt.app.data.model.TimeCategory
import com.fitgpt.app.data.model.UserPreferences

class OutfitRecommendationEngine {

    fun recommend(
        items: List<ClothingItem>,
        preferences: UserPreferences,
        recentlyShown: Set<Set<Int>> = emptySet(),
        plannedItemIds: Set<Int> = emptySet(),
        timeCategory: TimeCategory? = null,
        temperatureCategory: TemperatureCategory? = null,
        savedOutfitIds: Set<Set<Int>> = emptySet()
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
                val score = scoreItem(item, preferences, temperatureCategory)
                val perItem = mapOf(item.id to generateItemExplanation(item, preferences))
                OutfitRecommendation(
                    items = listOf(item),
                    score = score,
                    explanation = generateExplanation(listOf(item), score, preferences, timeCategory, temperatureCategory),
                    itemExplanations = perItem
                )
            }
                .sortedByDescending { it.score }
                .take(MAX_RECOMMENDATIONS)
        }

        // Filter out recently shown outfits (exact match)
        val fresh = allCombinations.filter { combo ->
            combo.map { it.id }.toSet() !in recentlyShown
        }

        // If every combination was recently shown, use all (graceful reset)
        val outfitCombinations = fresh.ifEmpty { allCombinations }

        val scored = outfitCombinations
            .map { outfit ->
                val baseScore = scoreOutfit(outfit, preferences, temperatureCategory)
                // Penalize outfits that heavily overlap with recently shown ones
                val overlapPen = overlapPenalty(outfit, recentlyShown)
                val plannerPen = plannerItemPenalty(outfit, plannedItemIds)
                val timeBonus = timeContextScore(outfit, timeCategory) * WEIGHT_TIME
                val tempComfortBonus = temperatureComfortBonus(outfit, temperatureCategory) * WEIGHT_TEMPERATURE
                val freshnessBonus = freshnessScore(outfit, recentlyShown) * WEIGHT_FRESHNESS
                val savedBonus = savedOutfitBonus(outfit, savedOutfitIds) * WEIGHT_SAVED
                val fitCompatBonus = outfitFitScore(outfit, preferences) * WEIGHT_FIT_COMPAT
                val fitPen = fitMismatchPenalty(outfit, preferences)
                val totalPenalty = (overlapPen + plannerPen).coerceAtMost(MAX_CONTEXT_PENALTY)
                val adjustedScore = (baseScore + timeBonus + tempComfortBonus + freshnessBonus + savedBonus + fitCompatBonus - totalPenalty - fitPen).coerceAtLeast(0.01)
                val perItem = outfit.associate { item ->
                    item.id to generateItemExplanation(item, preferences)
                }
                OutfitRecommendation(
                    items = outfit,
                    score = adjustedScore,
                    explanation = generateExplanation(outfit, adjustedScore, preferences, timeCategory, temperatureCategory),
                    itemExplanations = perItem
                )
            }

        return pickDiverseResults(scored, MAX_RECOMMENDATIONS)
    }

    // --- Freshness ---

    /**
     * Scores how fresh an outfit is relative to recently shown outfits.
     *
     * Returns a value in [0.0, 1.0]:
     * - 1.0 when the outfit shares no items with any recent outfit
     * - 0.0 when the outfit is an exact repeat of a recent outfit
     * - Near-duplicates (overlap >= [NEAR_DUPLICATE_THRESHOLD]) are penalized
     *   more heavily via amplification so that "swap one accessory" outfits
     *   rank much lower than genuinely novel combinations.
     *
     * Multiplied by [WEIGHT_FRESHNESS] in the scoring formula for an additive
     * bonus that complements the subtractive [overlapPenalty].
     */
    internal fun freshnessScore(
        outfit: List<ClothingItem>,
        recentlyShown: Set<Set<Int>>
    ): Double {
        if (recentlyShown.isEmpty() || outfit.isEmpty()) return 1.0

        val outfitIds = outfit.map { it.id }.toSet()

        // Exact repeat → minimum freshness
        if (outfitIds in recentlyShown) return 0.0

        // Find maximum overlap ratio with any recent outfit
        val maxOverlap = recentlyShown.maxOfOrNull { shown ->
            val commonCount = outfitIds.intersect(shown).size
            commonCount.toDouble() / outfitIds.size.coerceAtLeast(1)
        } ?: 0.0

        // Near-duplicate amplification: [0.75, 1.0) → [0.875, 1.0)
        val effectiveOverlap = if (maxOverlap >= NEAR_DUPLICATE_THRESHOLD) {
            0.5 * (1.0 + maxOverlap)
        } else {
            maxOverlap
        }

        return (1.0 - effectiveOverlap).coerceIn(0.0, 1.0)
    }

    /**
     * Penalizes outfits that share many items with recently shown outfits.
     * Exact duplicates are already filtered out; this handles *partial* overlap
     * so that refreshes feel genuinely different, not just a small item swap.
     */
    internal fun overlapPenalty(
        outfit: List<ClothingItem>,
        recentlyShown: Set<Set<Int>>
    ): Double {
        if (recentlyShown.isEmpty()) return 0.0
        val outfitIds = outfit.map { it.id }.toSet()
        val maxOverlap = recentlyShown.maxOfOrNull { shown ->
            val commonCount = outfitIds.intersect(shown).size
            commonCount.toDouble() / outfitIds.size.coerceAtLeast(1)
        } ?: 0.0
        // Scale by diversity weight — a 100% overlap (not exact set match) gets full penalty
        return maxOverlap * WEIGHT_DIVERSITY
    }

    /**
     * Penalizes outfits that contain items already planned for the day.
     * The penalty is proportional to how many outfit items overlap with
     * the planned set — wearing the same shirt you already committed to
     * should be deprioritized but not excluded.
     */
    internal fun plannerItemPenalty(
        outfit: List<ClothingItem>,
        plannedItemIds: Set<Int>
    ): Double {
        if (plannedItemIds.isEmpty()) return 0.0
        val outfitIds = outfit.map { it.id }.toSet()
        val overlapCount = outfitIds.intersect(plannedItemIds).size
        val overlapRatio = overlapCount.toDouble() / outfitIds.size.coerceAtLeast(1)
        return overlapRatio * WEIGHT_PLANNER
    }

    /**
     * Rewards outfits that reuse items from the user's saved (favorite) outfits
     * while suggesting novel combinations. Exact saved combinations get no bonus
     * (the user already has them saved — suggest something new instead).
     *
     * Returns a value in [0.0, 1.0]:
     * - 0.0 when outfit is an exact saved combination or has no overlap
     * - Proportional to how many items appear in any saved outfit
     *
     * Multiplied by [WEIGHT_SAVED] in the scoring formula.
     */
    internal fun savedOutfitBonus(
        outfit: List<ClothingItem>,
        savedOutfitIds: Set<Set<Int>>
    ): Double {
        if (savedOutfitIds.isEmpty() || outfit.isEmpty()) return 0.0

        val outfitIds = outfit.map { it.id }.toSet()

        // Exact saved combination → no bonus (user already has this; suggest novelty)
        if (outfitIds in savedOutfitIds) return 0.0

        // Bonus proportional to how many items appear in any saved outfit
        val savedItemIds = savedOutfitIds.flatten().toSet()
        val favoriteCount = outfitIds.count { it in savedItemIds }

        return favoriteCount.toDouble() / outfitIds.size.coerceAtLeast(1)
    }

    // --- Fit compatibility ---

    /**
     * Returns how compatible a garment's fit is with the given body type.
     * Values range from 0.3 (poor match) to 1.0 (ideal match).
     *
     * "Regular" fit is always safe (0.7–0.8), ensuring default items never
     * break the recommendation flow. Unknown fits and body types default
     * to 0.7 (neutral baseline).
     */
    internal fun fitCompatibility(fit: String, bodyType: String): Double {
        val f = fit.lowercase()
        val b = bodyType.lowercase()
        return when (b) {
            "slim" -> when (f) {
                "fitted" -> 1.0
                "regular" -> 0.7
                "oversized" -> 0.6
                "relaxed" -> 0.5
                else -> 0.7
            }
            "athletic" -> when (f) {
                "fitted" -> 1.0
                "regular" -> 0.8
                "relaxed" -> 0.5
                "oversized" -> 0.3
                else -> 0.7
            }
            "plus-size" -> when (f) {
                "relaxed" -> 1.0
                "oversized" -> 0.8
                "regular" -> 0.7
                "fitted" -> 0.3
                else -> 0.7
            }
            "average" -> when (f) {
                "regular" -> 0.8
                "fitted" -> 0.7
                "relaxed" -> 0.7
                "oversized" -> 0.6
                else -> 0.7
            }
            else -> 0.7 // unknown body type — neutral baseline
        }
    }

    /**
     * Returns the average fit compatibility across all items in the outfit.
     * Higher scores mean the outfit's garment fits are well-suited to the
     * user's body type.
     *
     * Returns 0.0 for empty outfits.
     */
    internal fun outfitFitScore(
        outfit: List<ClothingItem>,
        preferences: UserPreferences
    ): Double {
        if (outfit.isEmpty()) return 0.0
        return outfit.sumOf { fitCompatibility(it.fit, preferences.bodyType) } / outfit.size
    }

    /**
     * Penalizes outfits containing items whose fit is strongly mismatched
     * with the user's body type (compatibility below [FIT_MISMATCH_THRESHOLD]).
     *
     * The penalty is proportional to the fraction of mismatched items,
     * scaled by [WEIGHT_FIT_PENALTY]. Applied separately from the context
     * penalty cap so fit mismatches always have effect.
     */
    internal fun fitMismatchPenalty(
        outfit: List<ClothingItem>,
        preferences: UserPreferences
    ): Double {
        if (outfit.isEmpty()) return 0.0
        val mismatchCount = outfit.count {
            fitCompatibility(it.fit, preferences.bodyType) < FIT_MISMATCH_THRESHOLD
        }
        val mismatchRatio = mismatchCount.toDouble() / outfit.size
        return mismatchRatio * WEIGHT_FIT_PENALTY
    }

    /**
     * Greedy selection that balances quality with item diversity.
     * Picks the highest-scoring outfit first, then for each subsequent pick
     * adds a novelty bonus for outfits that use items not yet selected.
     * This ensures the returned set covers more of the wardrobe.
     */
    private fun pickDiverseResults(
        candidates: List<OutfitRecommendation>,
        count: Int
    ): List<OutfitRecommendation> {
        if (candidates.size <= count) return candidates.sortedByDescending { it.score }

        val selected = mutableListOf<OutfitRecommendation>()
        val remaining = candidates.toMutableList()
        val usedItemIds = mutableSetOf<Int>()

        while (selected.size < count && remaining.isNotEmpty()) {
            val best = remaining.maxByOrNull { rec ->
                val novelItems = rec.items.count { it.id !in usedItemIds }
                val noveltyBonus = if (rec.items.isNotEmpty()) {
                    novelItems.toDouble() / rec.items.size * WEIGHT_DIVERSITY
                } else 0.0
                rec.score + noveltyBonus
            } ?: break

            selected.add(best)
            remaining.remove(best)
            usedItemIds.addAll(best.items.map { it.id })
        }

        return selected
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

    internal fun scoreItem(
        item: ClothingItem,
        preferences: UserPreferences,
        temperatureCategory: TemperatureCategory? = null
    ): Double {
        val seasonScore = seasonMatchScore(item, preferences) * WEIGHT_SEASON
        val comfortScore = comfortMatchScore(item, preferences) * WEIGHT_COMFORT
        val styleScore = styleMatchScore(item, preferences) * WEIGHT_STYLE
        val fitScore = bodyTypeFitScore(item, preferences) * WEIGHT_FIT
        val rawScore = seasonScore + comfortScore + styleScore + fitScore
        val tempMultiplier = temperatureSeasonScore(item, temperatureCategory)
        return rawScore * tempMultiplier
    }

    internal fun scoreOutfit(
        outfit: List<ClothingItem>,
        preferences: UserPreferences,
        temperatureCategory: TemperatureCategory? = null
    ): Double {
        if (outfit.isEmpty()) return 0.0

        val avgItemScore = outfit.sumOf { scoreItem(it, preferences, temperatureCategory) } / outfit.size
        val harmonyBonus = colorHarmonyBonus(outfit)
        val coverageBonus = categoryDiversityBonus(outfit)

        return avgItemScore + (harmonyBonus * WEIGHT_HARMONY) + (coverageBonus * WEIGHT_COVERAGE)
    }

    // --- Temperature ---

    /**
     * Returns a multiplicative factor for how well an item's season matches the
     * current temperature category. Returns 1.0 (no effect) when [temperatureCategory]
     * is null. Suitability is floored at [TEMPERATURE_SUITABILITY_FLOOR] to prevent
     * zero-score items from being completely excluded.
     */
    internal fun temperatureSeasonScore(
        item: ClothingItem,
        temperatureCategory: TemperatureCategory?
    ): Double {
        if (temperatureCategory == null) return 1.0
        return temperatureCategory.seasonSuitability(item.season)
            .coerceAtLeast(TEMPERATURE_SUITABILITY_FLOOR)
    }

    /**
     * Returns a comfort bonus for extreme temperatures (COLD/HOT). The bonus
     * rewards high-comfort items when the weather is harsh. Normalized to [0.0, 1.0],
     * then multiplied by [WEIGHT_TEMPERATURE] in the scoring formula.
     *
     * Returns 0.0 when [temperatureCategory] is null, non-extreme, or outfit is empty.
     */
    internal fun temperatureComfortBonus(
        outfit: List<ClothingItem>,
        temperatureCategory: TemperatureCategory?
    ): Double {
        if (temperatureCategory == null || !temperatureCategory.isExtreme || outfit.isEmpty()) return 0.0
        val avgComfort = outfit.sumOf { it.comfortLevel }.toDouble() / outfit.size
        return ((avgComfort - 1.0) / 4.0).coerceIn(0.0, 1.0)
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

    // --- Time-based scoring ---

    /**
     * Infers a formality level (1–5) for a clothing item based on its category,
     * fit, and color. Used by [timeContextScore] to align outfit formality with
     * time of day.
     */
    internal fun inferFormality(item: ClothingItem): Int {
        // Category base
        val categoryBase = when (item.category.lowercase()) {
            ClothingCategory.OUTERWEAR.lowercase() -> 4
            ClothingCategory.ACCESSORY.lowercase() -> 2
            else -> 3 // Top, Bottom, Shoes
        }

        // Fit modifier
        val fitMod = when (item.fit.lowercase()) {
            "fitted" -> 1
            "oversized", "relaxed" -> -1
            else -> 0 // "regular"
        }

        // Color modifier
        val formalColors = setOf("black", "navy", "gray", "grey")
        val casualColors = setOf("yellow", "orange", "pink", "coral", "mint", "peach")
        val colorMod = when (item.color.lowercase()) {
            in formalColors -> 1
            in casualColors -> -1
            else -> 0
        }

        return (categoryBase + fitMod + colorMod).coerceIn(1, 5)
    }

    /**
     * Scores how well an outfit's average formality matches the given time
     * category's ideal range.
     *
     * Returns a value in [0.0, 1.0]:
     * - 1.0 when average formality falls within the time category's ideal range
     * - Decreases by 0.25 per unit of distance outside the range
     * - 0.0 minimum
     *
     * Returns 0.0 when [timeCategory] is null or the outfit is empty (backward
     * compat — zero contribution to the scoring formula).
     */
    internal fun timeContextScore(
        outfit: List<ClothingItem>,
        timeCategory: TimeCategory?
    ): Double {
        if (timeCategory == null || outfit.isEmpty()) return 0.0

        val avgFormality = outfit.sumOf { inferFormality(it) }.toDouble() / outfit.size

        val distance = when {
            avgFormality < timeCategory.idealFormalityMin -> timeCategory.idealFormalityMin - avgFormality
            avgFormality > timeCategory.idealFormalityMax -> avgFormality - timeCategory.idealFormalityMax
            else -> 0.0
        }

        return (1.0 - 0.25 * distance).coerceAtLeast(0.0)
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
                if (combos.size >= MAX_COMBINATIONS) return combos

                // Top + bottom + outerwear
                for (outer in outerwear) {
                    combos.add(base + outer)
                    if (combos.size >= MAX_COMBINATIONS) return combos
                }

                // Top + bottom + shoes
                for (shoe in shoes) {
                    combos.add(base + shoe)
                    if (combos.size >= MAX_COMBINATIONS) return combos

                    // Top + bottom + shoes + outerwear
                    for (outer in outerwear) {
                        combos.add(base + shoe + outer)
                        if (combos.size >= MAX_COMBINATIONS) return combos
                    }
                }

                // Top + bottom + accessory
                for (acc in accessories) {
                    combos.add(base + acc)
                    if (combos.size >= MAX_COMBINATIONS) return combos
                }
            }
        }

        return combos
    }

    // --- Explanation generation ---

    private fun generateExplanation(
        outfit: List<ClothingItem>,
        score: Double,
        preferences: UserPreferences,
        timeCategory: TimeCategory? = null,
        temperatureCategory: TemperatureCategory? = null
    ): String {
        val parts = mutableListOf<String>()

        // Temperature note (only when temperatureCategory is provided)
        if (temperatureCategory != null && outfit.isNotEmpty()) {
            val tempNote = when (temperatureCategory) {
                TemperatureCategory.COLD -> "Selected for cold weather comfort"
                TemperatureCategory.COOL -> "Light layers for cool conditions"
                TemperatureCategory.MILD -> "Suited for mild temperatures"
                TemperatureCategory.WARM -> "Breathable choices for warm weather"
                TemperatureCategory.HOT -> "Lightweight picks to beat the heat"
            }
            parts.add(tempNote)
        }

        // Time-of-day note (only when timeCategory is provided)
        if (timeCategory != null && outfit.isNotEmpty()) {
            val timeNote = when (timeCategory) {
                TimeCategory.MORNING -> "Relaxed morning-ready look"
                TimeCategory.AFTERNOON -> "Versatile daytime ensemble"
                TimeCategory.EVENING -> "Polished for the evening"
                TimeCategory.NIGHT -> "Comfortable late-night pick"
            }
            parts.add(timeNote)
        }

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

        // Fit compatibility note
        val fitCompat = outfitFitScore(outfit, preferences)
        when {
            fitCompat >= 0.9 -> parts.add("Excellent fit choices for your ${preferences.bodyType.lowercase()} build")
            fitCompat < FIT_MISMATCH_THRESHOLD -> parts.add("Consider trying different fits for your frame")
        }

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
        private const val MAX_COMBINATIONS = 500

        // Scoring weights — color harmony is prioritized for a polished look
        internal const val WEIGHT_SEASON = 0.25
        internal const val WEIGHT_COMFORT = 0.20
        internal const val WEIGHT_STYLE = 0.15
        internal const val WEIGHT_FIT = 0.10
        internal const val WEIGHT_HARMONY = 0.20
        internal const val WEIGHT_COVERAGE = 0.10
        internal const val WEIGHT_DIVERSITY = 0.15
        internal const val WEIGHT_PLANNER = 0.25
        internal const val WEIGHT_TIME = 0.10
        internal const val WEIGHT_TEMPERATURE = 0.10
        internal const val WEIGHT_FRESHNESS = 0.15
        internal const val WEIGHT_SAVED = 0.08
        internal const val WEIGHT_FIT_COMPAT = 0.12
        internal const val WEIGHT_FIT_PENALTY = 0.15
        internal const val FIT_MISMATCH_THRESHOLD = 0.4
        internal const val NEAR_DUPLICATE_THRESHOLD = 0.75
        internal const val TEMPERATURE_SUITABILITY_FLOOR = 0.05
        internal const val MAX_CONTEXT_PENALTY = 0.40
    }
}
