package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_cache")
data class AiCache(
    @PrimaryKey val queryHash: String, // SHA-256 of the cleaned input clause
    val rawInput: String,
    val responseJson: String,
    val createdAt: Long = System.currentTimeMillis()
)
