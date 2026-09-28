package com.example.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/**
 * In-memory fake of [AiCacheDao] for unit tests. Mirrors the BillRepository
 * fake pattern: holds a [MutableStateFlow] of [AiCache] rows keyed by
 * queryHash, and supports get/insert/deleteExpired/clearAll with the same
 * semantics as the real Room DAO.
 */
class FakeAiCacheDao : AiCacheDao {

    private val entries = MutableStateFlow<Map<String, AiCache>>(emptyMap())

    fun snapshot(): List<AiCache> = entries.value.values.toList()

    fun count(): Int = entries.value.size

    fun hashes(): Set<String> = entries.value.keys

    override suspend fun getCache(hash: String): AiCache? {
        return entries.value[hash]
    }

    override suspend fun insertCache(cache: AiCache) {
        entries.value = entries.value.toMutableMap().apply {
            put(cache.queryHash, cache)
        }
    }

    override suspend fun deleteExpired(now: Long, ttlMillis: Long) {
        entries.value = entries.value.filterValues { (now - it.createdAt) <= ttlMillis }
    }

    override suspend fun clearAll() {
        entries.value = emptyMap()
    }

    /** Convenience for assertions. */
    suspend fun firstCache(hash: String): AiCache? = entries.value.values.firstOrNull { it.queryHash == hash }
}
