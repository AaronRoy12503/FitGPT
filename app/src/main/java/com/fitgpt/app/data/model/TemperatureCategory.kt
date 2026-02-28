package com.fitgpt.app.data.model

/**
 * Temperature categories with season suitability mappings for outfit scoring.
 * Used as a multiplicative gate on item scores — items mismatched for the
 * current temperature are soft-filtered to [SUITABILITY_FLOOR] of their normal score.
 *
 * @param label human-readable label for explanation text
 * @param isExtreme true for COLD/HOT, drives comfort bonus activation
 */
enum class TemperatureCategory(val label: String, val isExtreme: Boolean) {
    COLD("cold", true),
    COOL("cool", false),
    MILD("mild", false),
    WARM("warm", false),
    HOT("hot", true);

    /**
     * Returns a suitability score in [0.0, 1.0] for how well [season] fits
     * this temperature category.
     *
     * Case-insensitive. Unknown seasons default to 0.5.
     */
    fun seasonSuitability(season: String): Double {
        return when (this) {
            COLD -> when (season.lowercase()) {
                "winter" -> 1.0
                "fall" -> 0.5
                "all" -> 0.7
                "spring" -> 0.2
                "summer" -> 0.0
                else -> 0.5
            }
            COOL -> when (season.lowercase()) {
                "winter" -> 0.5
                "fall" -> 1.0
                "all" -> 0.8
                "spring" -> 0.9
                "summer" -> 0.2
                else -> 0.5
            }
            MILD -> when (season.lowercase()) {
                "winter" -> 0.2
                "fall" -> 0.9
                "all" -> 0.9
                "spring" -> 1.0
                "summer" -> 0.6
                else -> 0.5
            }
            WARM -> when (season.lowercase()) {
                "winter" -> 0.0
                "fall" -> 0.3
                "all" -> 0.8
                "spring" -> 0.6
                "summer" -> 1.0
                else -> 0.5
            }
            HOT -> when (season.lowercase()) {
                "winter" -> 0.0
                "fall" -> 0.0
                "all" -> 0.6
                "spring" -> 0.3
                "summer" -> 1.0
                else -> 0.5
            }
        }
    }

    companion object {
        fun fromCelsius(temp: Int): TemperatureCategory = when {
            temp < 5 -> COLD
            temp < 15 -> COOL
            temp < 22 -> MILD
            temp < 30 -> WARM
            else -> HOT
        }
    }
}
