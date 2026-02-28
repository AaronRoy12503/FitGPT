package com.fitgpt.app.data.model

/**
 * Time-of-day categories with ideal formality ranges for outfit scoring.
 * Formality scale: 1 (very casual) to 5 (very formal).
 *
 * @param idealFormalityMin lower bound of the ideal formality range
 * @param idealFormalityMax upper bound of the ideal formality range
 */
enum class TimeCategory(val idealFormalityMin: Int, val idealFormalityMax: Int) {
    MORNING(1, 2),      // 6–11: casual-leaning
    AFTERNOON(2, 4),    // 12–16: business-casual range
    EVENING(3, 5),      // 17–20: formal-leaning
    NIGHT(1, 2);        // 21–5: comfort/casual

    companion object {
        fun fromHour(hour: Int): TimeCategory = when (hour) {
            in 6..11 -> MORNING
            in 12..16 -> AFTERNOON
            in 17..20 -> EVENING
            else -> NIGHT // 21–23, 0–5
        }
    }
}
