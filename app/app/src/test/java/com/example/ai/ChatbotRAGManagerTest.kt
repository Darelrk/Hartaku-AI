package com.example.ai

import com.example.data.FakeTransactionRepository
import com.example.data.Transaction
import com.example.data.TransactionType
import com.example.data.AgentFeedback
import com.example.data.FakeAgentFeedbackDao
import com.example.data.vector.TransactionVectorEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.toList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression + tool flow tests for [ChatbotRAGManager].
 *
 * Alur baru: chatWithTools() (routing) → ToolCallExecutor (jika ada tool_call)
 * → chat() final (no tools) → AnswerValidator.
 *
 * Celah asli yang diuji (6 regression):
 *  1. "tampilkan transaksi kopi 5 hari terakhir" → query_transactions
 *  2. "berapa kategori Makanan minggu ini"      → get_summary
 *  3. "berapa pengeluaran 3 hari terakhir"      → get_summary last_n_days
 *  4. "sebulan terakhir"                         → get_summary month
 *  5. chat_only "halo"                           → no tool_call
 *  6. "tampilkan semua"                          → query_transactions all
 *
 * Tool flow tests (4 new):
 *  - Tool routing: tool_calls[] → execute → final answer
 *  - Post-validation: angka hallucinated → regenerate
 *  - Chat-only path: off-topic → no tool_call → returned as-is
 *  - Error recovery: executor gagal → fallback
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ChatbotRAGManagerTest {

    private lateinit var fakeChatClient: FakeChatClient
    private lateinit var fakeTransactionRepository: FakeTransactionRepository
    private lateinit var ragManager: ChatbotRAGManager

    private val now = System.currentTimeMillis()
    private val dayMs = 24L * 60 * 60 * 1000

    @Before
    fun setUp() {
        fakeChatClient = FakeChatClient()
        fakeTransactionRepository = FakeTransactionRepository()
        ragManager = ChatbotRAGManager(fakeChatClient, fakeTransactionRepository)
    }

    private fun insertTx(
        description: String,
        amount: Double,
        category: String,
        daysAgo: Int,
        type: TransactionType = TransactionType.EXPENSE
    ) = kotlinx.coroutines.runBlocking {
        fakeTransactionRepository.insertTransaction(
            Transaction(
                amount = amount,
                description = description,
                category = category,
                type = type,
                timestamp = now - daysAgo * dayMs
            )
        )
    }

    // ==================== 6 REGRESSION TESTS ====================

    @Test
    fun `regression 1 - tampilkan transaksi kopi 5 hari terakhir uses query_transactions`() = runTest {
        insertTx("Kopi susu", 25000.0, "Makanan", daysAgo = 1)
        insertTx("Bensin", 50000.0, "Transport", daysAgo = 1)

        fakeChatClient.toolCallToReturn = ToolCall(
            id = "call_1",
            name = "query_transactions",
            argumentsJson = """{"keyword":"kopi","dateRange":"last_n_days","days":5}"""
        )
        fakeChatClient.toolAnswerToReturn = "Ditemukan 1 transaksi kopi dalam 5 hari terakhir, total Rp 25.000."

        val answer = ragManager.processQuery("tampilkan transaksi kopi 5 hari terakhir", emptyList())

        assertEquals(2, fakeChatClient.toolCallCount)
        assertTrue("Answer should mention 25.000", answer.contains("25.000"))
    }

    @Test
    fun `regression 2 - berapa kategori Makanan minggu ini uses get_expenses with category filter`() = runTest {
        insertTx("Nasi padang", 30000.0, "Makanan", daysAgo = 1)
        insertTx("Bensin", 50000.0, "Transport", daysAgo = 1)

        fakeChatClient.toolCallToReturn = ToolCall(
            id = "call_2",
            name = "get_expenses",
            argumentsJson = """{"category":"Makanan","dateRange":"week"}"""
        )
        fakeChatClient.toolAnswerToReturn = "Total kategori Makanan minggu ini Rp 30.000 dari 1 transaksi."

        val answer = ragManager.processQuery("berapa kategori Makanan minggu ini", emptyList())

        assertEquals(2, fakeChatClient.toolCallCount)
        assertTrue(answer.contains("30.000"))
    }

    @Test
    fun `regression 3 - berapa pengeluaran 3 hari terakhir uses get_expenses with last_n_days`() = runTest {
        insertTx("Kopi", 20000.0, "Makanan", daysAgo = 1)
        insertTx("Lama", 10000.0, "Makanan", daysAgo = 10) // di luar 3 hari

        fakeChatClient.toolCallToReturn = ToolCall(
            id = "call_3",
            name = "get_expenses",
            argumentsJson = """{"dateRange":"last_n_days","days":3}"""
        )
        fakeChatClient.toolAnswerToReturn = "Pengeluaran 3 hari terakhir Rp 20.000 dari 1 transaksi."

        val answer = ragManager.processQuery("berapa pengeluaran 3 hari terakhir", emptyList())

        assertEquals(2, fakeChatClient.toolCallCount)
        assertTrue(answer.contains("20.000"))
    }

    @Test
    fun `regression 4 - sebulan terakhir uses get_expenses with dateRange month`() = runTest {
        insertTx("Sewa", 1500000.0, "Sewa", daysAgo = 5)
        insertTx("Lama banget", 500000.0, "Lainnya", daysAgo = 60)

        fakeChatClient.toolCallToReturn = ToolCall(
            id = "call_4",
            name = "get_expenses",
            argumentsJson = """{"dateRange":"month"}"""
        )
        fakeChatClient.toolAnswerToReturn = "Total 30 hari terakhir Rp 1.500.000 dari 1 transaksi."

        val answer = ragManager.processQuery("sebulan terakhir", emptyList())

        assertEquals(2, fakeChatClient.toolCallCount)
        assertTrue(answer.contains("1.500.000"))
    }

    @Test
    fun `regression 5 - chat_only halo returns plain greeting without tool call`() = runTest {
        // toolCallToReturn null → chat_only path
        fakeChatClient.toolCallToReturn = null
        fakeChatClient.toolAnswerToReturn = "Halo! Ada yang bisa saya bantu dengan catatan keuanganmu?"

        val answer = ragManager.processQuery("halo", emptyList())

        assertEquals(1, fakeChatClient.toolCallCount)
        assertTrue(answer.contains("Halo"))
    }

    @Test
    fun `regression 6 - tampilkan semua uses query_transactions with dateRange all`() = runTest {
        repeat(5) { i ->
            insertTx("Transaksi $i", 10000.0, "Makanan", daysAgo = i)
        }

        fakeChatClient.toolCallToReturn = ToolCall(
            id = "call_6",
            name = "query_transactions",
            argumentsJson = """{"dateRange":"all"}"""
        )
        fakeChatClient.toolAnswerToReturn = "Ditemukan 5 transaksi total Rp 50.000."

        val answer = ragManager.processQuery("tampilkan semua", emptyList())

        assertEquals(2, fakeChatClient.toolCallCount)
        assertTrue(answer.contains("5 transaksi") || answer.contains("50.000"))
    }

    // ==================== 4 NEW TOOL FLOW TESTS ====================

    @Test
    fun `tool flow - LLM tool_calls triggers executor and final answer uses real data`() = runTest {
        // Real DB data
        insertTx("Kopi susu", 25000.0, "Makanan", daysAgo = 1)
        insertTx("Kopi hitam", 15000.0, "Makanan", daysAgo = 2)

        // Routing: LLM emit tool_call
        fakeChatClient.toolCallToReturn = ToolCall(
            id = "tc_1",
            name = "query_transactions",
            argumentsJson = """{"keyword":"kopi","dateRange":"last_n_days","days":7}"""
        )
        // Final answer (akan divalidate; pakai angka real)
        fakeChatClient.toolAnswerToReturn = "Saya menemukan 2 transaksi kopi dengan total Rp 40.000."

        val answer = ragManager.processQuery("kopi 7 hari terakhir", emptyList())

        // Verify tool flow: 1 routing call + 1 final answer call
        assertEquals(2, fakeChatClient.toolCallCount)
        assertEquals(0, fakeChatClient.callCount) // final answer

        // Answer harus pass validasi (40.000 ada di context)
        assertTrue(answer.contains("40.000"))
        // Bukan jawaban fallback / regenerate
        assertFalse(answer.contains("Maaf, HartaKu AI"))
    }

    @Test
    fun `post-validation - hallucinated 120rb replaced with real 1_045_800`() = runTest {
        insertTx("Sewa apartemen", 1045800.0, "Sewa", daysAgo = 1)

        fakeChatClient.toolCallToReturn = ToolCall(
            id = "tc_2",
            name = "get_expenses",
            argumentsJson = """{"dateRange":"last_n_days","days":7}"""
        )
        // LLM haluskan: jawab 120rb padahal data 1.045.800
        fakeChatClient.toolAnswerToReturn = "Total pengeluaran 7 hari terakhir 120rb rupiah."

        val answer = ragManager.processQuery("berapa pengeluaran 7 hari terakhir", emptyList())

        // Validator harus mengganti jawaban → format template dari context
        assertFalse("120rb harus ditolak", answer.contains("120rb"))
        assertFalse("120.000 harus ditolak", answer.contains("120.000"))
        // Jawaban baru harus dari template (ada Rp dan angka 1.045.800 atau 1045800)
        assertTrue(
            "Answer harus mengandung Rp1.045.800 (regenerated), got: $answer",
            answer.contains("1.045.800") || answer.contains("1045800")
        )
    }

    @Test
    fun `chat-only path - off-topic returns plain content as-is`() = runTest {
        // LLM tidak emit tool_call
        fakeChatClient.toolCallToReturn = null
        fakeChatClient.toolAnswerToReturn = "Saya adalah asisten keuangan, mungkin bisa bantu kamu kelola anggaran?"

        val answer = ragManager.processQuery("ceritakan lelucon dong", emptyList())

        // 1 call ke chatWithTools, tidak ada final call
        assertEquals(1, fakeChatClient.toolCallCount)
        assertEquals(0, fakeChatClient.callCount) // tidak ada final LLM call
        assertTrue(answer.contains("asisten keuangan"))
    }

    @Test
    fun `error recovery - executor returns error json yields fallback message`() = runTest {
        // Force executor error: malformed args akan yield JSON {"error":"..."}
        // lalu looksLikeError di manager → return fallback.
        fakeChatClient.toolCallToReturn = ToolCall(
            id = "tc_3",
            name = "query_transactions",
            argumentsJson = """{"keyword":"kopi"}""" // dateRange tidak valid di sini, tapi executor fallback ke all
        )
        // Simulasikan result kosong dari executor (tidak ada data di repo)
        // → toolResult.content bukan error, tapi rowCount=0
        // Validator cek false negative; jika jawaban bilang "ada" padahal 0 → regenerate
        fakeChatClient.toolAnswerToReturn = "Ada 1 transaksi kopi total Rp 25.000."

        // Repo kosong → tool result rowCount=0, totalExpense=0
        val answer = ragManager.processQuery("kopi 5 hari terakhir", emptyList())

        // Jawaban "Ada 1 transaksi" tidak valid karena context bilang 0
        // → regenerate dari context → "Tidak ditemukan"
        assertTrue(
            "Should regenerate to empty, got: $answer",
            answer.contains("Tidak ditemukan") || answer.contains("tidak")
        )
    }

    @Test
    fun `profile injection - low accuracy appends caution prompt with mistakes`() = runTest {
        val fakeDao = FakeAgentFeedbackDao()
        fakeDao.fakeAverageRating = 2.0 // accuracy = 0.4
        fakeDao.fakeLowRated = listOf(
            AgentFeedback(id="1", sessionId="", queryText="total kemarin berapa ya", responseText="", toolCallsJson="[]", rating=1, implicitSignal=null, category="CHAT", createdAt=0, processedAt=null),
            AgentFeedback(id="2", sessionId="", queryText="total minggu ini dong", responseText="", toolCallsJson="[]", rating=2, implicitSignal=null, category="CHAT", createdAt=0, processedAt=null)
        )
        val profileProvider = AgentProfileProvider(fakeDao)
        ragManager = ChatbotRAGManager(fakeChatClient, fakeTransactionRepository, agentProfileProvider = profileProvider)

        fakeChatClient.toolCallToReturn = null
        fakeChatClient.toolAnswerToReturn = "Halo, ada yang bisa dibantu?"

        ragManager.processQuery("halo", emptyList())

        val prompt = fakeChatClient.lastSystemPrompt!!
        assertTrue("Should contain 'Akurasi historis rendah'", prompt.contains("Akurasi historis rendah"))
        assertTrue("Should contain 40%", prompt.contains("40%"))
        assertTrue("Should contain mistake hint about 'total'", prompt.contains("Pengguna sering memberi rating rendah pada pertanyaan"))
    }

    @Test
    fun `profile injection - null provider yields no adaptation block`() = runTest {
        ragManager = ChatbotRAGManager(fakeChatClient, fakeTransactionRepository)

        fakeChatClient.toolCallToReturn = null
        fakeChatClient.toolAnswerToReturn = "Halo, ada yang bisa dibantu?"

        ragManager.processQuery("halo", emptyList())

        val prompt = fakeChatClient.lastSystemPrompt!!
        assertFalse("Should NOT contain adaptation block", prompt.contains("Adaptasi dari Riwayat Feedback"))
    }

    @Test
    fun `auto-rag - formatTransactionContext produces correct markdown with formatted amounts`() = runTest {
        ragManager = ChatbotRAGManager(fakeChatClient, fakeTransactionRepository)
        val mockResults = listOf(
            TransactionVectorEntity(
                id = 1, transactionId = 100,
                description = "Bensin", category = "Transport",
                amount = 75000.0, type = "EXPENSE", timestamp = 1000000
            ),
            TransactionVectorEntity(
                id = 2, transactionId = 101,
                description = "Gaji", category = "Pendapatan",
                amount = 5000000.0, type = "INCOME", timestamp = 2000000
            )
        )

        val result = ragManager.formatTransactionContext(mockResults)

        assertTrue("Should contain header", result.contains("Transaksi Terkini"))
        assertTrue("Should contain description", result.contains("Bensin"))
        assertTrue("Should format amount with Indonesian delimiter", result.contains("Rp 75.000"))
        assertTrue("Should format large amount correctly", result.contains("Rp 5.000.000"))
        assertTrue("Should contain type", result.contains("INCOME"))
    }

    @Test
    fun `processQueryStream - returns single-emit flow wrapping processQuery`() = runTest {
        fakeChatClient.toolCallToReturn = null
        fakeChatClient.toolAnswerToReturn = "Halo, ada yang bisa dibantu?"

        val flow = ragManager.processQueryStream("halo", emptyList())
        val emitted = flow.toList()

        assertEquals("Should emit exactly once (fallback to non-streaming)", 1, emitted.size)
        assertEquals("Halo, ada yang bisa dibantu?", emitted[0])
    }
}

