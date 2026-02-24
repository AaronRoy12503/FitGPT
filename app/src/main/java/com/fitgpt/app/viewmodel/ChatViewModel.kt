package com.fitgpt.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitgpt.app.ai.GroqChatService
import com.fitgpt.app.data.model.ChatMessage
import com.fitgpt.app.data.model.ClothingItem
import com.fitgpt.app.data.model.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * DATA SAFETY: This ViewModel sends data to the Groq chat AI service.
 *
 * What flows to the external AI API (via [GroqChatService]):
 * - User chat message text (role + content only; no IDs or timestamps)
 * - Sanitized wardrobe context: clothing attributes only (category, color, season,
 *   comfort level, fit). Item IDs, image URLs, and user-identifying metadata are
 *   stripped before building the context string.
 * - Style preferences (style preference, comfort preference, preferred seasons).
 *   Body type is excluded as it is a physical identifier.
 *
 * What is NOT sent to the AI:
 * - User IDs, auth tokens, or account information
 * - Message IDs or timestamps
 * - ClothingItem.id or ClothingItem.imageUrl
 * - UserPreferences.bodyType
 */

class ChatViewModel : ViewModel() {

    private val groqChatService = GroqChatService()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var wardrobeItems: List<ClothingItem> = emptyList()
    private var userPreferences: UserPreferences? = null

    fun updateWardrobeContext(items: List<ClothingItem>, preferences: UserPreferences) {
        wardrobeItems = items
        userPreferences = preferences
    }

    fun sendMessage(text: String) {
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = "user",
            content = text,
            timestamp = System.currentTimeMillis()
        )
        _messages.value = _messages.value + userMessage

        if (!groqChatService.isAvailable) {
            _messages.value = _messages.value + ChatMessage(
                id = UUID.randomUUID().toString(),
                role = "assistant",
                content = "Chat is unavailable — no API key configured.",
                timestamp = System.currentTimeMillis(),
                isError = true
            )
            return
        }

        _isLoading.value = true

        viewModelScope.launch {
            try {
                val wardrobeContext = buildWardrobeContext()
                // DATA SAFETY: GroqChatService.chat() only extracts role and content
                // from ChatMessage objects; id and timestamp are never serialized.
                val conversationHistory = _messages.value.filter { !it.isError }
                val response = groqChatService.chat(conversationHistory, wardrobeContext)

                _messages.value = _messages.value + ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = "assistant",
                    content = response,
                    timestamp = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                // DATA SAFETY: Log only the exception class/message, not the full
                // stack trace, to avoid leaking user data in logs.
                Log.e("ChatViewModel", "Chat failed: ${e.javaClass.simpleName}")
                _messages.value = _messages.value + ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = "assistant",
                    content = "Something went wrong. Please try again.",
                    timestamp = System.currentTimeMillis(),
                    isError = true
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearChat() {
        _messages.value = emptyList()
    }

    fun retryLastMessage() {
        val msgs = _messages.value.toMutableList()
        // Remove the last error message
        if (msgs.isNotEmpty() && msgs.last().isError) {
            msgs.removeAt(msgs.lastIndex)
            _messages.value = msgs
        }
        // Find the last user message and resend
        val lastUserMessage = _messages.value.lastOrNull { it.role == "user" }
        if (lastUserMessage != null) {
            // Remove the last user message to avoid duplicates, then resend
            _messages.value = _messages.value.dropLast(1)
            sendMessage(lastUserMessage.content)
        }
    }

    /**
     * Builds a sanitized wardrobe context string for the AI.
     *
     * DATA SAFETY: Only clothing attributes are included (category, color, season,
     * comfort level, fit). Fields that could identify the user are stripped:
     * - ClothingItem.id (database identifier)
     * - ClothingItem.imageUrl (could contain user-specific storage paths)
     * - UserPreferences.bodyType (physical identifier)
     */
    private fun buildWardrobeContext(): String {
        val prefs = userPreferences
        val items = wardrobeItems

        if (items.isEmpty() && prefs == null) {
            return "The user hasn't added any wardrobe items or preferences yet."
        }

        val sb = StringBuilder()
        if (items.isNotEmpty()) {
            sb.appendLine("The user's wardrobe contains the following items:")
            for (item in items) {
                // Only include safe clothing attributes; id and imageUrl are excluded.
                sb.appendLine("- ${item.category}: ${item.color}, ${item.season} season, comfort ${item.comfortLevel}/5, fit ${item.fit}")
            }
        } else {
            sb.appendLine("The user hasn't added any wardrobe items yet.")
        }

        if (prefs != null) {
            sb.appendLine()
            // bodyType is excluded as it is a physical identifier.
            sb.appendLine("Style preferences:")
            sb.appendLine("- Style preference: ${prefs.stylePreference}")
            sb.appendLine("- Comfort preference: ${prefs.comfortPreference}/5")
            sb.appendLine("- Preferred seasons: ${prefs.preferredSeasons.joinToString(", ")}")
        }

        return sb.toString().trim()
    }
}
