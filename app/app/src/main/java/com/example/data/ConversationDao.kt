package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversation_sessions ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestActiveSession(): ConversationSession?

    @Query("SELECT * FROM conversation_messages WHERE sessionId = :sessionId ORDER BY turnIndex ASC LIMIT :limit")
    suspend fun getMessages(sessionId: String, limit: Int = 50): List<ConversationMessage>

    @Query("SELECT COALESCE(MAX(turnIndex), -1) FROM conversation_messages WHERE sessionId = :sessionId")
    suspend fun getLastTurnIndex(sessionId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ConversationSession)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ConversationMessage)

    @Query("UPDATE conversation_sessions SET updatedAt = :now, messageCount = messageCount + :delta WHERE id = :sessionId")
    suspend fun touchSession(sessionId: String, now: Long, delta: Int = 1)

    @Query("DELETE FROM conversation_messages WHERE sessionId IN (SELECT id FROM conversation_sessions WHERE updatedAt < :cutoff)")
    suspend fun deleteMessagesBySessionAge(cutoff: Long)

    @Query("DELETE FROM conversation_sessions WHERE updatedAt < :cutoff")
    suspend fun deleteSessionsOlderThan(cutoff: Long)
}
