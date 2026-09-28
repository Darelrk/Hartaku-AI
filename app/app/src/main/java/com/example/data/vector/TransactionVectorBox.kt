package com.example.data.vector

import android.content.Context
import io.objectbox.Box
import io.objectbox.BoxStore

/**
 * ObjectBox store + DAO wrapper for [TransactionVectorEntity]. Process-wide
 * singleton; call [init] once from [com.example.data.AppContainer].
 */
object TransactionVectorBox {
    private lateinit var store: BoxStore
    private lateinit var box: Box<TransactionVectorEntity>

    fun init(context: Context) {
        if (::store.isInitialized) return
        store = MyObjectBox.builder()
            .androidContext(context.applicationContext)
            .build()
        box = store.boxFor(TransactionVectorEntity::class.java)
    }

    fun upsert(entity: TransactionVectorEntity) {
        box.put(entity)
    }

    fun upsertAll(entities: List<TransactionVectorEntity>) {
        box.put(entities)
    }

    fun deleteByTransactionId(transactionId: Int) {
        box.query(TransactionVectorEntity_.transactionId.equal(transactionId.toLong()))
            .build()
            .use { it.remove() }
    }

    fun searchByVector(
        queryVector: FloatArray,
        limit: Int = 20,
        typeFilter: String? = null
    ): List<TransactionVectorEntity> {
        // nearestNeighbors returns OrderedQuery — re-query with type filter for parity
        // with the spec. nearestNeighbors doesn't support extra filters inline; we
        // do HNSW on embedding first, then filter in Kotlin.
        val all = box.query()
            .nearestNeighbors(
                TransactionVectorEntity_.embedding,
                queryVector,
                if (typeFilter != null) limit * 4 else limit
            )
            .build()
            .use { it.find() }
        return if (typeFilter != null) {
            all.filter { it.type == typeFilter }.take(limit)
        } else all
    }

    fun count(): Long = box.count()
}
