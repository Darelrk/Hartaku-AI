package com.example.ai

import com.example.data.FakeTransactionRepository
import com.example.data.Transaction
import com.example.data.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MultiTurnOrchestratorTest {

    /* Catatan desain: dua test lama ("multi-turn chains two tool calls" dan
     * "max turns exceeded returns fallback") dihapus 2026-07-31 karena orchestrator
     * sekarang sengaja membatasi ke SATU round tool-call per query (guard di
     * MultiTurnOrchestrator.processQuery) untuk mencegah Nemotron 4B loop di
     * production. Bila di masa depan proper anti-repeat guard (same tool+args)
     * diimplementasikan, kedua test bisa dipulihkan dari git history. */

    private lateinit var fakeClient: FakeChatClient
    private lateinit var repo: FakeTransactionRepository
    private lateinit var executor: ToolCallExecutor
    private lateinit var orchestrator: MultiTurnOrchestrator
    private val now = System.currentTimeMillis()

    @Before
    fun setUp() {
        fakeClient = FakeChatClient()
        repo = FakeTransactionRepository()
        executor = ToolCallExecutor(repo)
        orchestrator = MultiTurnOrchestrator(
            chatClient = fakeClient,
            toolExecutor = executor,
            maxTurns = 5
        )
        repo.transactions.value = listOf(
            Transaction(
                id = 1, amount = 25000.0, type = TransactionType.EXPENSE,
                description = "Kopi Kenangan", category = "Makanan",
                categoryId = "food", timestamp = now - 100000, deletedAt = null
            ),
            Transaction(
                id = 2, amount = 50000.0, type = TransactionType.EXPENSE,
                description = "Nasi Goreng", category = "Makanan",
                categoryId = "food", timestamp = now - 200000, deletedAt = null
            )
        )
    }

    // ── Single-turn: LLM responds with content (no tool_call) ──

    @Test
    fun `single turn with no tool call returns LLM content`() = runBlocking {
        fakeClient.turnContent = mapOf(0 to "Total pengeluaran Anda Rp 75.000.")
        fakeClient.turnToolCalls = emptyMap()

        val result = orchestrator.processQuery(
            userQuery = "berapa total pengeluaran saya?",
            systemPrompt = "Test prompt",
            history = emptyList()
        )

        assertEquals("Total pengeluaran Anda Rp 75.000.", result)
    }

    // ── Single tool call → final answer ──

    @Test
    fun `single tool call followed by final answer`() = runBlocking {
        fakeClient.turnToolCalls = mapOf(
            0 to listOf(ToolCall("call_1", "query_transactions",
                """{"keyword":"kopi","dateRange":"all"}"""))
        )
        fakeClient.turnContent = mapOf(
            0 to "",  // turn 0: tool_call response, no content
            1 to "Ada 1 transaksi kopi: Rp 25.000."
        )

        val result = orchestrator.processQuery(
            userQuery = "cari transaksi kopi",
            systemPrompt = "Test prompt",
            history = emptyList()
        )

        assertTrue(result.contains("25.000"))
        assertTrue(result.contains("kopi"))
    }

    // ── Tool error resilience ──

    @Test
    fun `tool execution error does not crash orchestrator`() = runBlocking {
        fakeClient.turnToolCalls = mapOf(
            0 to listOf(ToolCall("call_err", "nonexistent_tool", """{}"""))
        )
        fakeClient.turnContent = mapOf(
            0 to "",
            1 to "Maaf, saya tidak bisa memproses permintaan itu."
        )

        val result = orchestrator.processQuery(
            userQuery = "panggil tool yang tidak ada",
            systemPrompt = "Test prompt",
            history = emptyList()
        )

        assertTrue(result.isNotBlank())
    }

    // ── History passthrough ──

    @Test
    fun `history is passed to LLM calls`() = runBlocking {
        fakeClient.turnContent = mapOf(0 to "Halo!")
        fakeClient.turnToolCalls = emptyMap()

        val history = listOf(
            ChatMessageItem("user", "Halo"),
            ChatMessageItem("assistant", "Hai, ada yang bisa dibantu?")
        )

        orchestrator.processQuery(
            userQuery = "berapa pengeluaran saya?",
            systemPrompt = "Test prompt",
            history = history
        )

        val lastHistory = fakeClient.lastHistory
        assertNotNull(lastHistory)
        assertTrue(lastHistory!!.any { it.content == "Hai, ada yang bisa dibantu?" })
    }

    // ── Tool result appended to history ──

    @Test
    fun `tool result is appended to history after execution`() = runBlocking {
        fakeClient.turnToolCalls = mapOf(
            0 to listOf(ToolCall("call_t", "query_transactions",
                """{"keyword":"kopi","dateRange":"all"}"""))
        )
        fakeClient.turnContent = mapOf(
            0 to "",
            1 to "Ditemukan 1 transaksi kopi."
        )

        orchestrator.processQuery(
            userQuery = "cari kopi",
            systemPrompt = "Test prompt",
            history = emptyList()
        )

        // After first call, second call's history should include tool result
        val secondCallHistory = fakeClient.lastHistory
        assertNotNull(secondCallHistory)
        // The history for the second call should contain the tool result
        assertTrue(secondCallHistory!!.any { it.role == "tool" })
    }
}
