package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AiCacheDao {
    @Query("SELECT * FROM ai_cache WHERE queryHash = :hash LIMIT 1")
    suspend fun getCache(hash: String): AiCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(cache: AiCache)

    @Query("DELETE FROM ai_cache WHERE (:now - createdAt) > :ttlMillis")
    suspend fun deleteExpired(now: Long, ttlMillis: Long)

    @Query("DELETE FROM ai_cache")
    suspend fun clearAll()
}
