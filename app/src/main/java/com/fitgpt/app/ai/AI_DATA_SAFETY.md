# AI Data Safety

This document describes exactly what data each AI service in FitGPT sends, where it
goes, what is excluded, and the rules developers must follow when adding new AI
features.

---

## 1. AI Service Inventory

| Service | File | Processing | Endpoint |
|---|---|---|---|
| `GroqRecommendationService` | `GroqRecommendationService.kt` | **External** (Groq API) | `https://api.groq.com/openai/v1/chat/completions` |
| `GroqChatService` | `GroqChatService.kt` | **External** (Groq API) | `https://api.groq.com/openai/v1/chat/completions` |
| `OutfitRecommendationEngine` | `OutfitRecommendationEngine.kt` | **Local only** | None -- runs entirely on-device |

---

## 2. Data Sent by Each Service

### GroqRecommendationService (External)

Sends to the Groq LLM API to generate outfit recommendations.

**Data included in prompts:**

| Field | Source | Example |
|---|---|---|
| Anonymous item index | Generated at call time (0, 1, 2...) | `0`, `1`, `2` |
| `ClothingItem.category` | User wardrobe | `"Top"`, `"Bottom"` |
| `ClothingItem.color` | User wardrobe | `"Navy"`, `"Black"` |
| `ClothingItem.season` | User wardrobe | `"Summer"`, `"All"` |
| `ClothingItem.comfortLevel` | User wardrobe | `3` |
| `ClothingItem.fit` | User wardrobe | `"Fitted"`, `"Regular"` |
| `UserPreferences.bodyType` | User settings | `"Athletic"` |
| `UserPreferences.stylePreference` | User settings | `"Casual"` |
| `UserPreferences.comfortPreference` | User settings | `3` |
| `UserPreferences.preferredSeasons` | User settings | `["Spring", "Summer"]` |

**Sanitization:** Real `ClothingItem.id` values (database primary keys) are replaced
with sequential anonymous indices (0, 1, 2 ...) before the prompt is built. The
mapping is held in memory and used to translate indices back to real IDs when parsing
the response. This means no database IDs leave the device.

**Not sent:** `ClothingItem.id`, `ClothingItem.imageUrl`, any user identity fields.

### GroqChatService (External)

Sends to the same Groq LLM API to power the Style Assistant chat feature.

**Data included in requests:**

| Field | Source | Notes |
|---|---|---|
| Conversation messages | User-typed chat text | Each `ChatMessage.content` and `.role` |
| Wardrobe summary | Built by `ChatViewModel.buildWardrobeContext()` | Category, color, season, comfort level per item |
| User preferences | `UserPreferences` fields | bodyType, stylePreference, comfortPreference, preferredSeasons |

**Wardrobe context format (built in ChatViewModel):**
```
- Top: Navy, Summer season, comfort 3/5
- Bottom: Black, All season, comfort 4/5
```

**Not sent:** `ChatMessage.id`, `ChatMessage.timestamp`, `ChatMessage.isError`,
`ClothingItem.id`, `ClothingItem.imageUrl`, `ClothingItem.fit` (omitted from chat
context), any user identity fields.

**Note:** User free-text chat messages are forwarded verbatim to the Groq API. The
app does not control what users type, so the system prompt and wardrobe context are
the only app-controlled data in this flow. See Section 6 for guidance.

### OutfitRecommendationEngine (Local)

Runs entirely on-device. No network calls. Receives the same `ClothingItem` and
`UserPreferences` objects as the Groq services but processes them using local scoring
algorithms (season matching, comfort scoring, color harmony, body type fit).

**No data leaves the device through this service.**

---

## 3. Data Flow Diagrams

### Outfit Recommendation Flow (GroqRecommendationService)

```
User Wardrobe           User Preferences
     |                       |
     v                       v
WardrobeViewModel.refreshRecommendations()
     |
     |  items: List<ClothingItem>
     |  preferences: UserPreferences
     v
GroqRecommendationService.recommend()
     |
     |  1. Map real item IDs -> anonymous indices (0, 1, 2...)
     |  2. Build prompt with: anonymous index, category, color,
     |     season, comfortLevel, fit, bodyType, stylePreference,
     |     comfortPreference, preferredSeasons
     v
Groq API  (https://api.groq.com/openai/v1/chat/completions)
     |
     |  Response: outfit combinations using anonymous indices
     v
GroqRecommendationService.parseResponse()
     |
     |  Map anonymous indices back to real ClothingItem objects
     v
List<OutfitRecommendation>  ->  UI
```

### Chat Flow (GroqChatService)

```
User types message
     |
     v
ChatViewModel.sendMessage(text)
     |
     |  1. Build wardrobeContext string (category, color, season,
     |     comfort per item + bodyType, style, comfort, seasons)
     |  2. Collect conversation history (role + content only,
     |     excluding error messages)
     v
GroqChatService.chat(messages, wardrobeContext)
     |
     |  Sends: system prompt + wardrobe context + conversation
     |  history as JSON message array
     v
Groq API  (https://api.groq.com/openai/v1/chat/completions)
     |
     |  Response: assistant reply text
     v
ChatViewModel  ->  UI
```

### Local Recommendation Flow (OutfitRecommendationEngine)

```
User Wardrobe           User Preferences
     |                       |
     v                       v
OutfitRecommendationEngine.recommend()
     |
     |  All processing on-device:
     |  - Season match scoring
     |  - Comfort match scoring
     |  - Style match scoring
     |  - Body type fit scoring
     |  - Color harmony analysis
     |  - Category diversity bonus
     v
List<OutfitRecommendation>  ->  UI

(No network traffic)
```

---

## 4. PII Exclusion Summary

The following data is **never sent** to any external AI endpoint:

| Excluded Field | Reason |
|---|---|
| User email | Not needed for fashion recommendations |
| User password / auth credentials | Security-critical; never relevant to AI |
| Firebase/OAuth user ID | Identifies the user; not needed for styling |
| Auth tokens (JWT, session tokens) | Security-critical |
| Device ID / Android ID / IMEI | Identifies the device; not relevant |
| IP address | Not added to requests by the app (handled at transport layer) |
| `ClothingItem.id` (in recommendation) | Database primary key replaced with anonymous index |
| `ClothingItem.imageUrl` | May contain storage paths or signed URLs; not sent |
| `ChatMessage.id` | Internal UUID; not included in API payloads |
| `ChatMessage.timestamp` | Temporal data; not included in API payloads |
| `UserPreferences.accessibilityModeEnabled` | Disability/health indicator; not sent |
| GPS / location data | Not collected or sent |
| Contacts, phone number, real name | Not collected by the app |

---

## 5. Sensitive Fields -- Never Send to AI Endpoints

Any field in this list must **never** appear in a prompt, system message, or any data
transmitted to an external AI service:

```
email
password
userId / uid / firebaseUid
authToken / accessToken / refreshToken / sessionToken / jwt
deviceId / androidId / advertisingId / IMEI
phoneNumber
realName / firstName / lastName / displayName
dateOfBirth / age
address / zipCode / postalCode
creditCard / paymentInfo
healthData / medicalInfo
accessibilityModeEnabled
imageUrl (may contain signed storage paths)
ClothingItem.id (use anonymous indices instead)
```

If a new model or data class is introduced that contains any of these fields, it must
be stripped or mapped before reaching any external service.

---

## 6. Developer Guidelines for New AI Features

### Before adding any AI feature:

1. **Inventory the data.** List every field that will reach the external API. Compare
   it against the sensitive fields list in Section 5.

2. **Strip or map identifiers.** If the feature needs to reference items, use
   anonymous indices (as `GroqRecommendationService` does) rather than database IDs.

3. **Do not send imageUrl.** Image URLs may contain signed cloud storage paths,
   bucket names, or user-specific path segments.

4. **Build the minimal prompt.** Only include fields the LLM actually needs. The
   recommendation service prompt is a good template -- it sends only category, color,
   season, comfort, fit, and general preferences.

5. **Be cautious with free-text input.** The chat feature forwards user messages
   verbatim. If your feature involves user-generated text, consider whether it could
   contain PII the user inadvertently shares. Where feasible, add a disclaimer or
   keep the scope narrow.

6. **Keep UserPreferences fields to styling data.** `bodyType`, `stylePreference`,
   `comfortPreference`, and `preferredSeasons` are acceptable because they describe
   styling needs without identifying the user. `accessibilityModeEnabled` is NOT
   acceptable -- it reveals disability/health status.

7. **Use the local engine as default.** `OutfitRecommendationEngine` handles
   recommendations without any network traffic. Prefer it when Groq is unavailable
   and as the synchronous fallback (as `WardrobeViewModel.refreshRecommendations()`
   already does).

8. **API key handling.** The Groq API key is sourced from `BuildConfig.GROQ_API_KEY`
   (injected at build time). Never log the key, include it in prompts, or transmit
   it anywhere other than the `Authorization` header.

### Code review checklist for AI PRs:

- [ ] No fields from the Section 5 sensitive list appear in prompts or API payloads
- [ ] Database/internal IDs are mapped to anonymous indices before leaving the device
- [ ] `imageUrl` is excluded from all outgoing data
- [ ] `accessibilityModeEnabled` is excluded
- [ ] API key is only used in the `Authorization` header, never logged or embedded
- [ ] Local fallback exists if the external API is unavailable
- [ ] Wardrobe context uses only: category, color, season, comfortLevel, fit
- [ ] User preferences use only: bodyType, stylePreference, comfortPreference, preferredSeasons
