package com.example.data.vector

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

/**
 * ObjectBox entity — derived index of Room [com.example.data.Transaction] for
 * on-device semantic search via HNSW. Room remains system of record; this is a
 * search index populated by [TransactionVectorSync].
 *
 * 2048-dim FloatArray matches NIM's `nvidia/nemotron-3-embed-1b` output. To switch
 * models later, change `@HnswIndex(dimensions = ...)` and `NimEmbeddingClient.model`
 * together — they're the only two coupled values.
 */
@Entity
data class TransactionVectorEntity(
    @Id var id: Long = 0,
    @Index var transactionId: Int = 0,
    var description: String = "",
    var category: String = "",
    var amount: Double = 0.0,
    var type: String = "",
    var timestamp: Long = 0,
    @HnswIndex(dimensions = 2048) var embedding: FloatArray = floatArrayOf()
)
