package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Satu sesi percakapan dengan AI agent.
 * Auto-created saat user kirim chat pertama tanpa session aktif.
 * Sesi >30 hari di-cleanup oleh DailyCheckWorker.
 */
@Entity(tableName = "conversation_sessions")
data class ConversationSession(
    @PrimaryKey val id: String,       // UUID v4
    val createdAt: Long,               // System.currentTimeMillis()
    val updatedAt: Long,               // last activity timestamp
    val messageCount: Int = 0
)
