package com.example.ai

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
    private val agentProfileProvider: AgentProfileProvider? = null
) {
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
        orchestrator.processQueryStream(userQuery, systemPrompt, effectiveHistory).collect { delta ->
            emit(delta)
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
        if (embeddingClient == null || query.isBlank()) return emptyList()
        return try {
            val queryEmb = embeddingClient.embed(query, isQuery = true).getOrNull() ?: return emptyList()
            TransactionVectorBox.searchByVector(queryEmb, limit = 5)
        } catch (_: Exception) { emptyList() }
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
