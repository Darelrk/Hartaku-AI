package com.example.ai

import com.example.data.AgentFeedback
import com.example.data.AgentFeedbackDao
import java.util.UUID

/**
 * Collects user feedback — explicit ratings (👍/👎) and implicit signals.
 */
class FeedbackCollector(
    private val feedbackDao: AgentFeedbackDao
) {
    suspend fun record(
        queryText: String,
        responseText: String,
        category: String = "CHAT",
        rating: Int? = null
    ): String {
        val id = UUID.randomUUID().toString()
        feedbackDao.insert(AgentFeedback(
            id = id, sessionId = "",
            queryText = queryText, responseText = responseText,
            toolCallsJson = "[]", rating = rating,
            implicitSignal = null, category = category,
            createdAt = System.currentTimeMillis(), processedAt = null
        ))
        return id
    }

    suspend fun rate(feedbackId: String, rating: Int) {
        feedbackDao.updateRating(feedbackId, rating.coerceIn(1, 5))
    }
}
