package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Satu message dalam sesi percakapan.
 * Compound unique index (sessionId, turnIndex) prevents duplicate inserts on replay.
 */
@Entity(
    tableName = "conversation_messages",
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["createdAt"]),
        Index(value = ["sessionId", "turnIndex"], unique = true)
    ]
)
data class ConversationMessage(
    @PrimaryKey val id: String,       // UUID v4
    val sessionId: String,             // logical FK to conversation_sessions.id
    val role: String,                  // "user" | "assistant" | "tool"
    val content: String,
    val turnIndex: Int,                // 0-based, per session
    val createdAt: Long
)
