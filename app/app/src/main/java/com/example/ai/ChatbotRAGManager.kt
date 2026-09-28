package com.example.ai

import com.example.data.AiCache
import com.example.data.AiCacheDao
import com.example.data.ConversationMessage
import com.example.data.ConversationRepository
import com.example.data.ConversationSession
import com.example.data.TransactionRepository
import com.example.data.vector.TransactionVectorBox
import com.example.data.vector.TransactionVectorEntity
import kotlin.math.abs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

/**
 * Chatbot RAG manager — delegates to MultiTurnOrchestrator for tool-use flow.
 * AnswerValidator post-processes the LLM's final answer for numeric accuracy.
 * Persists conversation history to Room DB via ConversationRepository.
 */
class ChatbotRAGManager(
    private val chatClient: ChatClient,
    private val transactionRepository: TransactionRepository,
    private val embeddingClient: NimEmbeddingClient? = null,
    private val conversationRepository: ConversationRepository? = null,
    private val agentProfileProvider: AgentProfileProvider? = null,
    private val aiCacheDao: AiCacheDao? = null,
    private val dataFingerprint: (suspend () -> String)? = null
) {

    private companion object {
        const val CHAT_TTL_MILLIS = 10L * 60 * 1000

        /**
         * Jendela prune untuk `deleteExpired`. WAJIB TTL terlama yang dipakai
         * tabel `ai_cache`, bukan TTL chat: DELETE-nya table-wide, jadi memakai
         * 10 menit akan menghapus entri TransactionAiParser (TTL 30 hari) —
         * justru cache paling berharga.
         */
        const val PRUNE_TTL_MILLIS = 30L * 24 * 60 * 60 * 1000
    }

    /** Durasi embed+HNSW terakhir, dipakai untuk stage `embed` di AiTrace. */
    private var lastEmbedMs: Long? = null
    private val toolExecutor = ToolCallExecutor(transactionRepository, embeddingClient)
    private val orchestrator = MultiTurnOrchestrator(chatClient, toolExecutor)

    /** Ground truth dari tool terakhir — diekspos untuk validasi post-stream. */
    val lastToolResultJson: String?
        get() = orchestrator.lastToolResultJson

    /**
     * Validasi jawaban akhir terhadap hasil tool terakhir (guard anti-halusinasi).
     * Dipanggil setelah stream selesai dari sisi UI; aman dipanggil berulang.
     */
    fun validateFinalAnswer(finalAnswer: String): String =
        AnswerValidator.validate(
            llmAnswer = finalAnswer,
            contextString = "",
            toolResultJson = orchestrator.lastToolResultJson
        )

    /** Query counter — rebuild profile every 5 queries. */
    private var queryCount = 0
    private var cachedProfile: AgentProfile? = null

    suspend fun processQuery(
        userQuery: String,
        history: List<ChatMessageItem> = emptyList()
    ): String {
        val startedAt = System.currentTimeMillis()

        // Cache read. Fingerprint data ikut di dalam kunci, jadi jawaban basi
        // tidak mungkin dilayani setelah ada transaksi masuk/ubah/hapus.
        val cacheKey = chatCacheKey(userQuery)
        val cachedAnswer = readChatCache(cacheKey)
        if (cachedAnswer != null) {
            AiTraceLog.record(
                AiTrace(
                    query = userQuery,
                    stages = emptyList(),
                    promptTokens = null,
                    completionTokens = null,
                    validation = "CACHE",
                    totalMs = System.currentTimeMillis() - startedAt
                )
            )
            return cachedAnswer
        }

        // Rebuild agent profile every 5 queries for prompt adaptation
        queryCount++
        if (queryCount % 5 == 0 || cachedProfile == null) {
            cachedProfile = try {
                agentProfileProvider?.buildProfile("CHAT")
            } catch (_: Exception) {
                cachedProfile // preserve last-good on failure
            }
        }
        val retrievedContext = retrieveRelevantTransactions(userQuery)
        val systemPrompt = buildRoutingSystemPrompt(retrievedContext)
        // Load from DB if caller passes empty history (first message or test)
        val effectiveHistory = if (history.isEmpty()) loadHistoryFromDb() else history
        val answer = orchestrator.processQuery(userQuery, systemPrompt, effectiveHistory)
        val validated = AnswerValidator.validate(
            llmAnswer = answer,
            contextString = "",
            toolResultJson = orchestrator.lastToolResultJson
        )

        val stages = buildList {
            lastEmbedMs?.let { add(AiTraceStage("embed", it)) }
            addAll(orchestrator.lastToolStages)
            addAll(orchestrator.lastLlmStages)
        }
        AiTraceLog.record(
            AiTrace(
                query = userQuery,
                stages = stages,
                promptTokens = orchestrator.lastPromptTokens,
                completionTokens = orchestrator.lastCompletionTokens,
                validation = when {
                    answer.startsWith("Maaf, saya sedang bermasalah") ||
                        answer.startsWith("Saya sudah mencoba beberapa kali") -> "FALLBACK"
                    validated == answer -> "OK"
                    else -> "REGENERATED"
                },
                totalMs = System.currentTimeMillis() - startedAt
            )
        )

        writeChatCache(cacheKey, userQuery, validated)

        persistToDb(userQuery, validated)
        return validated
    }

    /**
     * Streaming variant — delegates all queries to orchestrator.processQueryStream().
     * Orchestrator handles tool-call routing + streaming internally.
     */
    suspend fun processQueryStream(
        userQuery: String,
        history: List<ChatMessageItem> = emptyList()
    ): Flow<String> = flow {
        val startedAt = System.currentTimeMillis()

        // Jalur streaming adalah jalur produksi (HomeViewModel memanggil ini),
        // jadi cache WAJIB ada di sini — bukan hanya di processQuery.
        val cacheKey = chatCacheKey(userQuery)
        val cachedAnswer = readChatCache(cacheKey)
        if (cachedAnswer != null) {
            AiTraceLog.record(
                AiTrace(
                    query = userQuery,
                    stages = emptyList(),
                    promptTokens = null,
                    completionTokens = null,
                    validation = "CACHE",
                    totalMs = System.currentTimeMillis() - startedAt
                )
            )
            emit(cachedAnswer)
            return@flow
        }

        // Rebuild agent profile every 5 queries for prompt adaptation
        queryCount++
        if (queryCount % 5 == 0 || cachedProfile == null) {
            cachedProfile = try {
                agentProfileProvider?.buildProfile("CHAT")
            } catch (_: Exception) {
                cachedProfile
            }
        }
        val retrievedContext = retrieveRelevantTransactions(userQuery)
        val systemPrompt = buildRoutingSystemPrompt(retrievedContext)
        val effectiveHistory = if (history.isEmpty()) loadHistoryFromDb() else history

        val streamed = StringBuilder()
        orchestrator.processQueryStream(userQuery, systemPrompt, effectiveHistory).collect { delta ->
            streamed.append(delta)
            emit(delta)
        }

        // Validasi numerik untuk jalur ini dijalankan di sisi UI setelah stream
        // selesai, jadi trace hanya mencatat bahwa stream selesai.
        AiTraceLog.record(
            AiTrace(
                query = userQuery,
                stages = buildList {
                    lastEmbedMs?.let { add(AiTraceStage("embed", it)) }
                    addAll(orchestrator.lastToolStages)
                    addAll(orchestrator.lastLlmStages)
                },
                promptTokens = orchestrator.lastPromptTokens,
                completionTokens = orchestrator.lastCompletionTokens,
                validation = "STREAM",
                totalMs = System.currentTimeMillis() - startedAt
            )
        )

        if (streamed.isNotBlank()) {
            writeChatCache(cacheKey, userQuery, streamed.toString())
        }
    }

    /** Kunci cache chat, atau null bila cache tidak dikonfigurasi. */
    private suspend fun chatCacheKey(userQuery: String): String? {
        if (aiCacheDao == null || dataFingerprint == null) return null
        return AiCacheKey.forChat(
            runCatching { dataFingerprint() }.getOrDefault(""),
            userQuery
        )
    }

    /**
     * Baca cache chat. Setiap miss sekalian membuang entri kedaluwarsa supaya
     * tabel tidak menumpuk ketika fingerprint data berubah.
     */
    private suspend fun readChatCache(key: String?): String? {
        if (key == null) return null
        val cached = runCatching {
            aiCacheDao?.getCache(key)
                ?.takeIf { System.currentTimeMillis() - it.createdAt <= CHAT_TTL_MILLIS }
        }.getOrNull() ?: run {
            runCatching { aiCacheDao?.deleteExpired(System.currentTimeMillis(), PRUNE_TTL_MILLIS) }
            return null
        }
        return cached.responseJson
    }

    private suspend fun writeChatCache(key: String?, userQuery: String, answer: String) {
        if (key == null) return
        runCatching {
            aiCacheDao?.insertCache(AiCache(queryHash = key, rawInput = userQuery, responseJson = answer))
        }
    }

    private suspend fun loadHistoryFromDb(): List<ChatMessageItem> {
        val repo = conversationRepository ?: return emptyList()
        val session = repo.getLatestActiveSession()
        if (session == null) {
            repo.addSession(ConversationSession(
                id = UUID.randomUUID().toString(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            ))
            return emptyList()
        }
        return repo.getMessages(session.id).map { ChatMessageItem(it.role, it.content) }
    }

    private suspend fun persistToDb(userQuery: String, response: String) {
        val repo = conversationRepository ?: return
        val session = repo.getLatestActiveSession() ?: return
        val now = System.currentTimeMillis()
        repo.addMessage(ConversationMessage(
            id = UUID.randomUUID().toString(),
            sessionId = session.id,
            role = "user",
            content = userQuery,
            turnIndex = repo.getNextTurnIndex(session.id),
            createdAt = now
        ))
        repo.touchSession(session.id, now)
        repo.addMessage(ConversationMessage(
            id = UUID.randomUUID().toString(),
            sessionId = session.id,
            role = "assistant",
            content = response,
            turnIndex = repo.getNextTurnIndex(session.id),
            createdAt = System.currentTimeMillis()
        ))
        repo.touchSession(session.id, System.currentTimeMillis())
    }

    /**
     * Embed user query → ObjectBox HNSW search → top-5 semantically similar transactions.
     * Returns empty list when embedding client is null, query is blank, or search fails.
     * No greeting skip-list — embedding is cheap (~300ms) and noise bounded by limit=5.
     */
    private suspend fun retrieveRelevantTransactions(query: String): List<TransactionVectorEntity> {
        if (embeddingClient == null || query.isBlank()) {
            lastEmbedMs = null
            return emptyList()
        }
        val startedAt = System.currentTimeMillis()
        return try {
            val queryEmb = embeddingClient.embed(query, isQuery = true).getOrNull() ?: return emptyList()
            TransactionVectorBox.searchByVector(queryEmb, limit = 5)
        } catch (_: Exception) {
            emptyList()
        } finally {
            lastEmbedMs = System.currentTimeMillis() - startedAt
        }
    }

    /**
     * Format retrieved transactions into a markdown context block for the system prompt.
     * Marked `internal` for testability — pure function, no dependencies.
     */
    internal fun formatTransactionContext(results: List<TransactionVectorEntity>): String {
        if (results.isEmpty()) return ""
        return buildString {
            appendLine()
            appendLine("--- Transaksi Terkini ---")
            appendLine("Berikut transaksi yang relevan dengan pertanyaan pengguna (semantic search):")
            results.forEach { tx ->
                appendLine("- ${tx.description} | ${tx.category} | ${AnswerValidator.formatRupiah(abs(tx.amount).toLong())} | ${tx.type}")
            }
        }
    }

    private suspend fun buildRoutingSystemPrompt(
        ragContext: List<TransactionVectorEntity> = emptyList()
    ): String = buildString {
        append("""
Kamu adalah asisten keuangan HartaKu AI untuk MELIHAT data transaksi.
WAJIB jawab dalam Bahasa Indonesia.


ATURAN PENTING:
- Panggil tool SATU KALI SAJA untuk mengambil data.
- Setelah data diterima, JAWAB LANGSUNG berdasarkan data tersebut.
- JANGAN panggil tool lagi jika sudah memiliki data yang cukup.

Tool:
- query_transactions: cari/tampilkan transaksi spesifik (mis. "cari gojek", "tampilkan kopi")
- get_expenses: total pengeluaran + breakdown per kategori (panggil jika user tanya berapa/total/pengeluaran/belanja)
- get_income: total pemasukan + breakdown per kategori (panggil jika user tanya berapa/total/pemasukan/income/gaji)
- get_balance: saldo bersih = pemasukan - pengeluaran (panggil jika user tanya saldo/sisa/balance/neraca)
KAMU HANYA BISA MELIHAT DATA. TIDAK BISA MENCATAT TRANSAKSI.
Jika user minta menambahkan transaksi, jawab: "Maaf, saya hanya bisa melihat data. Silakan gunakan menu tambah transaksi."

Date range: today, yesterday, last_n_days+days=N, last_n_weeks+weeks=N, all
Tanggal spesifik ("tanggal 15 Juli"): isi startDate & endDate sama (yyyy-MM-dd).
""".trimIndent())

        if (ragContext.isNotEmpty()) {
            append(formatTransactionContext(ragContext))
        }
        cachedProfile?.let { profile ->
            appendLine()
            appendLine("--- Adaptasi dari Riwayat Feedback ---")
            when {
                profile.accuracy == 0.0 && profile.commonMistakes.isEmpty() ->
                    appendLine("Belum ada data feedback.")
                profile.accuracy >= 0.8 ->
                    appendLine("Akurasi historis tinggi (${(profile.accuracy * 100).toInt()}%). Pertahankan jawaban ringkas dan langsung ke angka.")
                profile.accuracy >= 0.5 ->
                    appendLine("Akurasi historis sedang (${(profile.accuracy * 100).toInt()}%). Tampilkan angka lengkap dan sebut asumsi.")
                else ->
                    appendLine("Akurasi historis rendah (${(profile.accuracy * 100).toInt()}%). Sangat teliti: double-check angka dan tunjukkan proses.")
            }
            profile.commonMistakes.forEach { appendLine("- $it") }
        }
    }
}

object ChatLogger {
    fun d(tag: String, msg: String) {
        try {
            android.util.Log.d(tag, msg)
        } catch (_: java.lang.RuntimeException) {
            println("[$tag] $msg")
        }
    }
    fun e(tag: String, msg: String, tr: Throwable? = null) {
        try {
            android.util.Log.e(tag, msg, tr)
        } catch (_: java.lang.RuntimeException) {
            println("[$tag] ERROR: $msg")
            tr?.printStackTrace()
        }
    }
}
