package com.example.ai

/**
 * Message in a chat conversation. Role = "user" or "assistant".
 * Used by [ChatbotRAGManager] untuk history dan response.
 */
data class ChatMessageItem(
    val role: String,
    val content: String
)
