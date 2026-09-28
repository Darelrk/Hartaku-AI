package com.example.data

/**
 * Thin wrapper around [ConversationDao] for use by AI services.
 * Following the repository pattern used by other services in AppContainer.
 */
class ConversationRepository(
    private val dao: ConversationDao
) {
    suspend fun getLatestActiveSession(): ConversationSession? = dao.getLatestActiveSession()
    suspend fun getMessages(sessionId: String, limit: Int = 50): List<ConversationMessage> = dao.getMessages(sessionId, limit)
    suspend fun addSession(session: ConversationSession) = dao.insertSession(session)
    suspend fun addMessage(message: ConversationMessage) = dao.insertMessage(message)
    suspend fun touchSession(sessionId: String, now: Long) = dao.touchSession(sessionId, now)
    suspend fun getNextTurnIndex(sessionId: String): Int = dao.getLastTurnIndex(sessionId) + 1
    suspend fun cleanupOldSessions(cutoff: Long) {
        dao.deleteMessagesBySessionAge(cutoff)
        dao.deleteSessionsOlderThan(cutoff)
    }
}
