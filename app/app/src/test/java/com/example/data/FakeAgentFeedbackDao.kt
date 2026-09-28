package com.example.data

/**
 * Minimal fake DAO for agent profile tests — controlled feedback ratings.
 */
class FakeAgentFeedbackDao : AgentFeedbackDao {
    var fakeAverageRating: Double? = null
    var fakeLowRated: List<AgentFeedback> = emptyList()

    override suspend fun getAverageRating(category: String): Double? = fakeAverageRating
    override suspend fun getLowRated(category: String, threshold: Int): List<AgentFeedback> = fakeLowRated
    override suspend fun getById(id: String): AgentFeedback? = null
    override suspend fun getByCategory(category: String): List<AgentFeedback> = emptyList()
    override suspend fun deleteOlderThan(cutoffMillis: Long) = Unit
    override suspend fun insert(feedback: AgentFeedback) = Unit
    override suspend fun updateRating(id: String, rating: Int) = Unit
}
