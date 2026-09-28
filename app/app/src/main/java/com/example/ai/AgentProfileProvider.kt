package com.example.ai

import com.example.data.AgentFeedbackDao

data class AgentProfile(
    val accuracy: Double,
    val commonMistakes: List<String>
)

/**
 * Aggregates feedback data into AgentProfile — accuracy score + mistake patterns.
 */
class AgentProfileProvider(
    private val feedbackDao: AgentFeedbackDao
) {
    suspend fun buildProfile(category: String = "CHAT"): AgentProfile {
        val avgRating = feedbackDao.getAverageRating(category)
        val accuracy = if (avgRating != null) (avgRating / 5.0).coerceIn(0.0, 1.0) else 0.0

        val lowRated = feedbackDao.getLowRated(category, 3)
        val mistakes = extractMistakePatterns(lowRated)

        return AgentProfile(accuracy, mistakes)
    }

    private fun extractMistakePatterns(lowRated: List<com.example.data.AgentFeedback>): List<String> {
        if (lowRated.isEmpty()) return emptyList()
        val keywordCounts = mutableMapOf<String, Int>()
        for (fb in lowRated) {
            val q = fb.queryText.lowercase()
            for (kw in listOf("total", "jumlah", "rata-rata", "perbandingan", "prediksi")) {
                if (q.contains(kw)) keywordCounts[kw] = (keywordCounts[kw] ?: 0) + 1
            }
        }
        val threshold = (lowRated.size * 0.4).toInt()
        return keywordCounts.filter { it.value >= threshold }
            .keys.map { kw ->
                "Pengguna sering memberi rating rendah pada pertanyaan \"$kw\". " +
                "Pastikan jawaban akurat berdasarkan data real."
            }
    }
}
