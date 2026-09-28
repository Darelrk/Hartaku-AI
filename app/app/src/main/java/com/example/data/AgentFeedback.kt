package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(
    tableName = "agent_feedback",
    indices = [
        Index(value = ["category"]),
        Index(value = ["rating"])
    ]
)
data class AgentFeedback(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "query_text") val queryText: String,
    @ColumnInfo(name = "response_text") val responseText: String,
    @ColumnInfo(name = "tool_calls_json") val toolCallsJson: String,
    val rating: Int?,
    @ColumnInfo(name = "implicit_signal") val implicitSignal: String?,
    val category: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "processed_at") val processedAt: Long?
)

@Dao
interface AgentFeedbackDao {
    @Query("SELECT * FROM agent_feedback WHERE id = :id")
    suspend fun getById(id: String): AgentFeedback?

    @Query("SELECT * FROM agent_feedback WHERE category = :category ORDER BY created_at DESC")
    suspend fun getByCategory(category: String): List<AgentFeedback>

    @Query("SELECT AVG(CAST(rating AS REAL)) FROM agent_feedback WHERE category = :category AND rating IS NOT NULL")
    suspend fun getAverageRating(category: String): Double?

    @Query("SELECT * FROM agent_feedback WHERE category = :category AND rating IS NOT NULL AND rating < :threshold ORDER BY created_at DESC")
    suspend fun getLowRated(category: String, threshold: Int = 3): List<AgentFeedback>

    @Query("DELETE FROM agent_feedback WHERE created_at < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(feedback: AgentFeedback)

    @Query("UPDATE agent_feedback SET rating = :rating WHERE id = :id")
    suspend fun updateRating(id: String, rating: Int)
}
