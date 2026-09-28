package com.example.ai

import com.example.data.FakeTransactionRepository
import com.example.data.Transaction
import com.example.data.TransactionType
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ToolCallExecutorTest {

    private lateinit var repo: FakeTransactionRepository
    private lateinit var executor: ToolCallExecutor

    private val now = System.currentTimeMillis()
    private val dayMs = 24L * 60 * 60 * 1000

    @Before
    fun setUp() {
        repo = FakeTransactionRepository()
        executor = ToolCallExecutor(repo)
    }

    private fun insertTx(
        description: String,
        amount: Double,
        category: String,
        daysAgo: Int,
        type: TransactionType = TransactionType.EXPENSE
    ) = runBlocking {
        repo.insertTransaction(
            Transaction(
                amount = amount,
                description = description,
                category = category,
                type = type,
                timestamp = now - daysAgo * dayMs
            )
        )
    }

    @Test
    fun `query_transactions with keyword and dateRange returns matching rows`() = runBlocking {
        insertTx("Kopi susu", 25000.0, "Makanan", daysAgo = 1)
        insertTx("Kopi hitam", 15000.0, "Makanan", daysAgo = 2)
        insertTx("Bensin", 50000.0, "Transport", daysAgo = 1)

        val args = """{"keyword":"kopi","dateRange":"last_n_days","days":5}"""
        val result = executor.execute(ToolCall("call_1", "query_transactions", args))

        val json = JSONObject(result.content)
        assertEquals(2, json.getInt("rowCount"))
        assertEquals(40000.0, json.getDouble("totalExpense"), 0.01)
        assertEquals("call_1", result.callId)
    }

    @Test
    fun `get_expenses with category filter narrows breakdown`() = runBlocking {
        insertTx("Nasi padang", 30000.0, "Makanan", daysAgo = 1)
        insertTx("Bensin", 50000.0, "Transport", daysAgo = 1)
        insertTx("Kopi", 20000.0, "Makanan", daysAgo = 2)

        val args = """{"category":"Makanan","dateRange":"last_n_days","days":7}"""
        val result = executor.execute(ToolCall("call_2", "get_expenses", args))

        val json = JSONObject(result.content)
        // get_expenses return breakdown-only; cek field aktual yang ada
        assertEquals(50000.0, json.getDouble("totalExpense"), 0.01)
        val breakdown = json.getJSONArray("breakdown")
        assertEquals(1, breakdown.length())
        assertEquals("Makanan", breakdown.getJSONObject(0).getString("category"))
        assertEquals(50000.0, breakdown.getJSONObject(0).getDouble("total"), 0.01)
    }

    @Test
    fun `query_transactions caps result at 50 rows`() = runBlocking {
        repeat(60) { i ->
            insertTx("Tx $i", 1000.0, "Makanan", daysAgo = 0)
        }

        val args = """{"dateRange":"today"}"""
        val result = executor.execute(ToolCall("call_3", "query_transactions", args))
        val json = JSONObject(result.content)
        assertEquals(50, json.getInt("rowCount"))
        assertTrue("truncated flag should be true", json.getBoolean("truncated"))
    }

    @Test
    fun `unknown tool name returns error json`() = runBlocking {
        val result = executor.execute(ToolCall("call_4", "delete_everything", "{}"))
        val json = JSONObject(result.content)
        assertEquals("unknown_tool", json.getString("error"))
    }

    @Test
    fun `malformed arguments json returns empty result without crashing`() = runBlocking {
        insertTx("Test", 10000.0, "Makanan", daysAgo = 1)
        // Not valid JSON for arguments
        val result = executor.execute(ToolCall("call_5", "query_transactions", "not-json"))
        assertNotNull(result.content)
        // Should not throw, content should be valid JSON
        val json = JSONObject(result.content)
        assertTrue(json.has("rowCount") || json.has("error"))
    }

    @Test
    fun `dateRange all returns transactions across all time`() = runBlocking {
        insertTx("Lama", 10000.0, "Makanan", daysAgo = 365)
        insertTx("Baru", 20000.0, "Makanan", daysAgo = 1)

        val args = """{"dateRange":"all"}"""
        val result = executor.execute(ToolCall("call_6", "query_transactions", args))
        val json = JSONObject(result.content)
        assertEquals(2, json.getInt("rowCount"))
    }

    @Test
    fun `income transactions excluded from totalExpense in query_transactions`() = runBlocking {
        insertTx("Gaji", 5000000.0, "Gaji", daysAgo = 1, type = TransactionType.INCOME)
        insertTx("Makan", 30000.0, "Makanan", daysAgo = 1, type = TransactionType.EXPENSE)

        val args = """{"dateRange":"last_n_days","days":7}"""
        val result = executor.execute(ToolCall("call_7", "query_transactions", args))
        val json = JSONObject(result.content)
        // totalExpense hanya hitung EXPENSE
        assertEquals(30000.0, json.getDouble("totalExpense"), 0.01)
    }
}
