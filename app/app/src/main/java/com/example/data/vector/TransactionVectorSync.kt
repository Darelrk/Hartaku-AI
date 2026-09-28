package com.example.data.vector

import android.util.Log
import com.example.ai.NimEmbeddingClient
import com.example.data.Transaction
import com.example.data.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Mirror Room transactions into ObjectBox for semantic search. Room remains
 * system of record; this is a derived index populated on insert/update/delete.
 *
 * Strategy: hook into TransactionRepository via callbacks (insert/update → re-embed
 * and upsert; soft-delete → remove from ObjectBox). The hooks are wired in
 * [com.example.data.AppContainer].
 *
 * `embeddingClient == null` makes the sync a no-op (local-only mode). The chatbot
 * `query_transactions` falls back to keyword search when the ObjectBox index is empty.
 */
class TransactionVectorSync(
    private val transactionRepo: TransactionRepository,
    private val embeddingClient: NimEmbeddingClient?,
    private val vectorBox: TransactionVectorBox
) {
    private val TAG = "TransactionVectorSync"
    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * On first launch, embed all non-deleted transactions that aren't already in
     * ObjectBox. Idempotent — safe to call multiple times.
     */
    suspend fun backfillAll() {
        val client = embeddingClient ?: return
        val all = transactionRepo.getAllTransactions().first()
        if (all.isEmpty()) return
        val indexed = vectorBox.count()
        if (indexed >= all.size) return
        val toEmbed = all.filter { it.deletedAt == null }
        if (toEmbed.isEmpty()) return
        Log.i(TAG, "backfill: ${toEmbed.size} transactions to embed")
        val texts = toEmbed.map { buildEmbeddingText(it) }
        val result = client.embedBatch(texts)
        result.onSuccess { embeddings ->
            if (embeddings.size < toEmbed.size) {
                Log.w(TAG, "backfill incomplete: ${embeddings.size}/${toEmbed.size}, skipping")
                return@onSuccess
            }
            val entities = toEmbed.mapIndexed { i, tx ->
                TransactionVectorEntity(
                    transactionId = tx.id,
                    description = tx.description,
                    category = tx.category,
                    amount = tx.amount,
                    type = tx.type.name,
                    timestamp = tx.timestamp,
                    embedding = embeddings[i]
                )
            }
            vectorBox.upsertAll(entities)
        }.onFailure { Log.w(TAG, "backfill failed: ${it.message}") }
    }

    /**
     * Observe Room for changes; mirror inserts/updates/deletes into ObjectBox.
     * Idempotent — uses box.put which overwrites by primary key.
     */
    fun observeAndSync(): Job = scope.launch {
        val client = embeddingClient
        if (client == null) return@launch
        try {
            transactionRepo.getAllTransactions().collect { txs ->
                mutex.withLock { syncSnapshot(client, txs) }
            }
        } catch (e: Exception) {
            // collect sudah berhenti; error sekali mematikan sinkronisasi selamanya.
            Log.w(TAG, "observeAndSync error: ${e.message}")
        }
    }

    /** Hook called from TransactionRepository on insert/update. */
    fun onTransactionUpserted(tx: Transaction) {
        if (tx.deletedAt != null) return
        val client = embeddingClient ?: return
        scope.launch {
            mutex.withLock { embedAndStore(client, tx) }
        }
    }

    /** Hook called from TransactionRepository on soft-delete. */
    fun onTransactionSoftDeleted(id: Int) {
        scope.launch { vectorBox.deleteByTransactionId(id) }
    }

    private suspend fun syncSnapshot(client: NimEmbeddingClient, txs: List<Transaction>) {
        val active = txs.filter { it.deletedAt == null }
        if (active.isEmpty()) return
        val texts = active.map { buildEmbeddingText(it) }
        val result = client.embedBatch(texts)
        result.onSuccess { embeddings ->
            if (embeddings.size < active.size) {
                Log.w(TAG, "sync incomplete: ${embeddings.size}/${active.size}, skipping")
                return@onSuccess
            }
            val entities = active.mapIndexed { i, tx ->
                TransactionVectorEntity(
                    transactionId = tx.id,
                    description = tx.description,
                    category = tx.category,
                    amount = tx.amount,
                    type = tx.type.name,
                    timestamp = tx.timestamp,
                    embedding = embeddings[i]
                )
            }
            vectorBox.upsertAll(entities)
            Log.d(TAG, "embedded ${entities.size}/${active.size} transactions")
        }
    }

    private suspend fun embedAndStore(client: NimEmbeddingClient, tx: Transaction) {
        val result = client.embed(buildEmbeddingText(tx))
        result.onSuccess { embedding ->
            vectorBox.upsert(
                TransactionVectorEntity(
                    transactionId = tx.id,
                    description = tx.description,
                    category = tx.category,
                    amount = tx.amount,
                    type = tx.type.name,
                    timestamp = tx.timestamp,
                    embedding = embedding
                )
            )
        }.onFailure { Log.w(TAG, "embed failed for tx ${tx.id}: ${it.message}") }
    }

    private fun buildEmbeddingText(tx: Transaction): String =
        "${tx.description} ${tx.category} ${tx.type.name.lowercase()} ${tx.amount}"
}
