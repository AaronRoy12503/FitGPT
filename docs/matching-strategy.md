# FitGPT Outfit Matching Strategy

> Reference document for the current recommendation engine and guidelines for future AI upgrades.

---

## Architecture Overview

FitGPT uses a **two-tier recommendation system**:

1. **Rule-based engine** (`OutfitRecommendationEngine.kt`) — deterministic, runs offline, always available as a fallback.
2. **AI-powered engine** (`GroqRecommendationService.kt`) — calls the Groq API (Llama 3.3 70B) for natural-language-aware recommendations. Requires an API key.

The `WardrobeViewModel` orchestrates both: it runs the rule-based engine synchronously first, then attempts the AI engine asynchronously. If the AI call fails or returns empty, the rule-based results are shown.

---

## Scoring Model

Each outfit receives a composite score built from **per-item scores** and **outfit-level bonuses**.

### Per-Item Score

| Factor | Weight | Scoring Logic |
|--------|--------|---------------|
| **Season** | 0.25 | 1.0 if item season matches a preferred season; 0.8 for "All" season items; 0.2 otherwise |
| **Comfort** | 0.20 | 1.0 for exact match to user preference; 0.7 for +/-1; 0.4 for +/-2; 0.1 for +/-3+ |
| **Style** | 0.15 | 0.8 if item category fits the user's style profile; 0.5 otherwise |
| **Body Type Fit** | 0.10 | 0.7-0.9 depending on body type + category (e.g. outerwear scores 0.9 for slim frames) |

**Total per-item score** = sum of (factor score x weight) for all four factors.

### Outfit-Level Score

```
outfitScore = avgItemScore + (colorHarmonyBonus x 0.20) + (categoryDiversityBonus x 0.10)
```

| Bonus | Weight | Purpose |
|-------|--------|---------|
| **Color Harmony** | 0.20 | Rewards polished color coordination (see below) |
| **Category Diversity** | 0.10 | Rewards complete outfits (top + bottom + shoes > top + bottom alone) |

### Score Tiers (displayed to user)

| Score Range | Label |
|-------------|-------|
| >= 2.5 | Highly recommended |
| >= 1.5 | Good match |
| < 1.5 | Worth trying |

---

## Color Matching Rules

Color harmony is the highest-weighted outfit-level factor (0.20). The engine classifies colors into **neutrals** and **accents**, then evaluates their relationship.

### Neutral Colors

```
black, white, gray, grey, beige, navy, tan, cream, khaki, ivory
```

### Harmony Evaluation (priority order)

The engine evaluates outfits top-to-bottom and returns the first matching score:

| Pattern | Score | Example |
|---------|-------|---------|
| **Monochromatic + neutral base** | 1.0 | Red top + red shoes + black pants |
| **Single accent + neutral base** | 1.0 | Blue shirt + black pants + white shoes |
| **Two analogous accents + neutral base** | 0.95 | Red top + coral scarf + beige pants |
| **Two complementary accents + neutral base** | 0.9 | Blue top + orange scarf + black pants |
| **Two unrelated accents + neutral base** | 0.7 | Red top + purple scarf + white pants |
| **Two analogous accents, no neutrals** | 0.85 | Red top + pink skirt |
| **Two complementary accents, no neutrals** | 0.8 | Blue top + orange skirt |
| **Same color temperature, no neutrals** | 0.7 | Red top + yellow skirt (both warm) |
| **All neutrals** | 0.7 | Black top + white pants + gray shoes |
| **Neutral base + 3+ accents** | 0.5 | Colorful but anchored |
| **Two unrelated accents, no neutrals** | 0.4 | Red top + purple skirt |
| **3+ accents, no neutral base** | 0.2 | Red + green + purple (risky) |

### Color Relationships

**Complementary pairs** (opposite on the color wheel):

```
blue <-> orange
red <-> green
yellow <-> purple
teal <-> red
pink <-> green
coral <-> teal
```

**Analogous groups** (adjacent on the color wheel):

```
red, orange, coral, pink
orange, yellow, gold
yellow, green, lime
green, teal, olive
blue, teal, navy
blue, purple, indigo
purple, pink, magenta
brown, orange, tan, rust
```

**Color temperature**:

| Warm | Cool |
|------|------|
| red, orange, yellow, coral, pink, gold, rust, brown, tan, peach | blue, green, purple, teal, navy, indigo, mint, olive, magenta |

---

## Category Diversity Scoring

Rewards outfits that cover more garment categories:

| Combination | Score |
|-------------|-------|
| Top + Bottom + 1 more category | 1.0 |
| Top + Bottom only | 0.8 |
| Top or Bottom only | 0.4 |
| Neither top nor bottom | 0.2 |

---

## Body Type Fit Scoring

The body type score combines a **category base score** with a **garment fit modifier**.

### Category Base Scores

| Body Type | Favored Categories | Rationale |
|-----------|--------------------|-----------|
| **Slim** | Outerwear (0.9), Accessories (0.85) | Layering adds dimension |
| **Athletic** | Tops (0.9), Shoes (0.85), Bottoms (0.8) | Accommodates broader build |
| **Plus-size** | Outerwear (0.9), Accessories (0.85), Tops (0.8) | Structured layers create shape; +0.1 comfort bonus for items >= 4 |
| **Average** | All categories (0.7) | Neutral baseline |

### Garment Fit Modifier

Each `ClothingItem` has a `fit` field with one of four values: **Fitted**, **Regular**, **Relaxed**, **Oversized**. The fit modifier adjusts the base score:

| Body Type | Fitted | Regular | Relaxed | Oversized |
|-----------|--------|---------|---------|-----------|
| **Slim** | +0.10 (accentuates frame) | 0.00 | 0.00 | +0.05 (adds volume) |
| **Athletic** | +0.10 (highlights build) | +0.05 (clean lines) | 0.00 | -0.05 (can look bulky) |
| **Plus-size** | -0.05 (can feel restrictive) | 0.00 | +0.10 (comfortable drape) | +0.05 (creates shape) |
| **Average** | 0.00 | 0.00 | 0.00 | 0.00 |

Final score = `(baseScore + fitBonus).coerceIn(0.0, 1.0)`

---

## Repeat Prevention

The system tracks recently shown outfits to keep recommendations fresh:

- Each outfit is fingerprinted as a **set of item IDs** (e.g., `{1, 3, 6}`).
- The `WardrobeViewModel` maintains a FIFO queue of up to **10** recent fingerprints.
- On each refresh, the engine filters out combinations matching any fingerprint in the queue.
- **Fallback**: if all possible combinations have been recently shown, the history is ignored to avoid returning empty results.
- The history is **in-memory only** and resets when the app restarts.

---

## Outfit Combination Builder

The engine generates combinations from categorized items:

```
For each top:
  For each bottom:
    -> top + bottom (base outfit)
    -> base + each outerwear option
    -> base + each shoe option
        -> base + shoe + each outerwear option
    -> base + each accessory option
```

Duplicate combinations (same item IDs regardless of order) are deduplicated before scoring.

**Max recommendations returned**: 5 (sorted by score descending).

---

## Explanation Generation

Each recommendation includes:

1. **Outfit-level explanation** covering season match, comfort assessment, color harmony label, style fit, body type insight, and score tier.
2. **Per-item explanations** covering season reasoning, comfort comparison, style note, body type note, and color character.

### Color Harmony Labels (user-facing)

| Pattern | Label Template |
|---------|---------------|
| Monochromatic | "Monochromatic palette -- {color} with neutral base creates a cohesive, polished look" |
| All neutral | "All-neutral palette -- {colors} gives a clean, sophisticated foundation" |
| Accent + neutral | "{Color} pops against {neutrals} -- a classic, polished combination" |
| Analogous | "{Color1} and {color2} are analogous colors -- they sit next to each other on the color wheel for a harmonious blend" |
| Complementary | "{Color1} and {color2} are complementary -- opposite on the color wheel for a vibrant, balanced contrast" |
| Same temperature | "Both {color1} and {color2} are {warm/cool} tones -- a unified color temperature for a cohesive feel" |
| High score fallback | "Colors work well together for a coordinated look" |
| Medium score fallback | "Interesting color mix -- adds personality" |
| Low score fallback | "Bold color combination -- consider a neutral anchor piece" |

### Color Character Map

Each color has an assigned personality used in per-item explanations:

| Color | Character |
|-------|-----------|
| Black | timeless sophistication |
| White | clean freshness |
| Blue / Navy | calm versatility |
| Red | bold energy |
| Green | natural balance |
| Gray | understated elegance |
| Beige / Tan / Cream | warm neutrality |
| Yellow | cheerful brightness |
| Orange | vibrant warmth |
| Purple | creative flair |
| Pink | playful softness |
| Brown | earthy grounding |

---

## AI Engine (Groq / Llama 3.3 70B)

When available, the Groq service sends a structured prompt containing:

- The full wardrobe (ID, category, color, season, comfort)
- User preferences (body type, style, comfort level, seasons)
- Explicit color coordination rules matching the rule-based engine
- Body type fit guidelines
- A strict output format (`OUTFIT / SCORE / EXPLANATION / ITEM_DETAILS`)

The AI response is parsed back into `OutfitRecommendation` objects and replaces the rule-based results in the UI.

---

## Future AI Upgrade Opportunities

### Short-Term Enhancements

1. **Occasion-aware matching** -- Add an `occasion` field (work, date night, weekend) and adjust scoring weights dynamically. The AI prompt already supports this; the rule-based engine needs a new scoring factor.

2. **Persistent repeat history** -- Store the recently-shown queue in `SharedPreferences` or Room so it survives app restarts. Extend `MAX_HISTORY_SIZE` based on wardrobe size.

3. **User feedback loop** -- Track which recommendations the user saves, wears, or dismisses. Use this to adjust scoring weights per-user over time.

4. **Weather API integration** -- Replace static season preferences with real-time weather data to dynamically weight season scoring.

### Medium-Term Enhancements

5. **Image-based color extraction** -- Use the device camera or photo library to detect actual garment colors (via ML Kit or a color extraction model) instead of relying on user-entered color names. This enables hex-level color harmony using HSL distance calculations.

6. **Expanded color model** -- Replace string-based color names with HSL values. Compute harmony scores mathematically:
   - Complementary: hue difference ~180 degrees
   - Analogous: hue difference ~30 degrees
   - Triadic: hue difference ~120 degrees
   - Split-complementary: 150/210 degree offsets

7. **Pattern and texture awareness** -- Add `pattern` (solid, striped, plaid, floral) and `material` fields to `ClothingItem`. Introduce a pattern-clash penalty (e.g., two bold prints scored lower) and a texture-contrast bonus (e.g., denim + silk).

8. **Embedding-based similarity** -- Use a pre-trained fashion embedding model (e.g., FashionCLIP) to encode garment images into vectors. Compute outfit compatibility as cosine similarity between item embeddings, replacing or augmenting the rule-based color/style scoring.

### Long-Term Vision

9. **On-device fine-tuned model** -- Train a lightweight model on outfit rating datasets (e.g., Polyvore, IQON) and run it on-device via TensorFlow Lite or ONNX Runtime. The model replaces the rule-based engine entirely while the Groq service provides explanations.

10. **Generative outfit visualization** -- Use a diffusion model to generate a preview image of the recommended outfit on a virtual mannequin matching the user's body type.

11. **Social and trend signals** -- Incorporate trending color palettes (e.g., Pantone Color of the Year) and popular outfit structures from fashion APIs to boost seasonally relevant combinations.

12. **Multi-user wardrobe sharing** -- Enable households to share wardrobes and get cross-wardrobe recommendations (e.g., borrowing a partner's jacket to complete an outfit).

---

## Key Files Reference

| File | Purpose |
|------|---------|
| `app/src/main/java/com/fitgpt/app/ai/OutfitRecommendationEngine.kt` | Rule-based scoring and combination engine |
| `app/src/main/java/com/fitgpt/app/ai/GroqRecommendationService.kt` | AI-powered recommendations via Groq API |
| `app/src/main/java/com/fitgpt/app/ai/GroqChatService.kt` | Fashion chatbot service |
| `app/src/main/java/com/fitgpt/app/viewmodel/WardrobeViewModel.kt` | Orchestration, history tracking, state management |
| `app/src/main/java/com/fitgpt/app/data/model/ClothingItem.kt` | Core data model (id, category, color, season, comfort, fit) |
| `app/src/main/java/com/fitgpt/app/data/model/OutfitRecommendation.kt` | Recommendation output model |
| `app/src/main/java/com/fitgpt/app/data/model/UserPreferences.kt` | User profile (body type, style, comfort, seasons) |
| `app/src/test/java/com/fitgpt/app/OutfitRecommendationEngineTest.kt` | Unit tests for scoring, harmony, and repeat prevention |
