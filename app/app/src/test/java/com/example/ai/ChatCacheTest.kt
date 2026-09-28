package com.example.ai

import com.example.data.AiCache
import com.example.data.AiCacheDao
import com.example.data.FakeTransactionRepository
import com.example.data.Transaction
import com.example.data.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** DAO cache in-memory supaya hit/miss bisa diamati tanpa Room. */
private class FakeAiCacheDao : AiCacheDao {
    val rows = linkedMapOf<String, AiCache>()

    override suspend fun getCache(hash: String): AiCache? = rows[hash]
    override suspend fun insertCache(cache: AiCache) {
        rows[cache.queryHash] = cache
    }
    override suspend fun deleteExpired(now: Long, ttlMillis: Long) {
        rows.entries.removeAll { now - it.value.createdAt > ttlMillis }
    }
    override suspend fun clearAll() = rows.clear()
}

/**
 * Cache chat: hit pada query yang sama, dan bust otomatis begitu fingerprint
 * data berubah. Menjalankan pipeline produksi penuh dengan `FakeChatClient`
 * supaya "cache hit" berarti benar-benar tidak ada panggilan LLM.
 */
@RunWith(RobolectricTestRunner::class)
class ChatCacheTest {

    private lateinit var repo: FakeTransactionRepository
    private lateinit var client: FakeChatClient
    private lateinit var cache: FakeAiCacheDao

    private val dayMs = 24L * 60 * 60 * 1000

    @Before
    fun setUp() = runBlocking {
        repo = FakeTransactionRepository()
        client = FakeChatClient()
        cache = FakeAiCacheDao()
        repo.insertTransaction(
            Transaction(
                amount = 85_000.0,
                description = "Makanan",
                category = "Makanan",
                type = TransactionType.EXPENSE,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    private fun manager() = ChatbotRAGManager(
        chatClient = client,
        transactionRepository = repo,
        aiCacheDao = cache,
        dataFingerprint = { repo.dataFingerprint() }
    )

    private fun programFake(finalAnswer: String) {
        client.resetTurns()
        client.turnToolCalls = mapOf(
            0 to listOf(
                ToolCall("call_1", "get_expenses", """{"dateRange":"today"}""")
            )
        )
        client.turnContent = mapOf(1 to finalAnswer)
    }

    @Test
    fun sameQueryTwice_secondRunIsServedFromCache() = runBlocking {
        programFake("Total pengeluaran hari ini Rp 85.000.")
        val manager = manager()

        val first = manager.processQuery("berapa pengeluaran hari ini?")
        val llmCallsAfterFirst = client.toolCallCount
        assertTrue("pukulan pertama harus memanggil LLM", llmCallsAfterFirst > 0)
        assertEquals(1, cache.rows.size)

        // Pukulan kedua dengan instance baru: cache di luar manager, jadi manager
        // baru wajib menyentuhnya tanpa pernah memanggil LLM.
        val second = manager().processQuery("berapa pengeluaran hari ini?")

        assertEquals(first, second)
        assertEquals(
            "pukulan kedua tidak boleh memanggil LLM",
            llmCallsAfterFirst,
            client.toolCallCount
        )
        assertNotNull(AiTraceLog.recent().firstOrNull { it.validation == "CACHE" })
    }

    @Test
    fun addingTransactionInvalidatesCache() = runBlocking {
        programFake("Total pengeluaran hari ini Rp 85.000.")
        manager().processQuery("berapa pengeluaran hari ini?")
        val llmCallsAfterFirst = client.toolCallCount

        // Transaksi baru mengubah fingerprint (COUNT + SUM + MAX) sehingga kunci
        // cache tidak boleh lagi cocok.
        repo.insertTransaction(
            Transaction(
                amount = 15_000.0,
                description = "Kopi",
                category = "Makanan",
                type = TransactionType.EXPENSE,
                timestamp = System.currentTimeMillis() - dayMs
            )
        )

        programFake("Total pengeluaran hari ini Rp 100.000.")
        manager().processQuery("berapa pengeluaran hari ini?")

        assertTrue(
            "fingerprint berubah harus bust cache dan memanggil LLM lagi",
            client.toolCallCount > llmCallsAfterFirst
        )
        assertEquals(
            "entri cache lama dan baru harus terpisah oleh fingerprint",
            2, cache.rows.size
        )
        // Teks jawaban berasal dari FakeChatClient dan boleh ditulis ulang oleh
        // AnswerValidator, jadi yang dibuktikan di sini mekanismenya: cache bust,
        // LLM dipanggil lagi, dan kunci lama tidak lagi dipakai.
    }

    /**
     * `deleteExpired` bersifat table-wide. Kalau prune memakai TTL chat
     * (10 menit), entri TransactionAiParser yang umurnya 30 menit ikut hilang
     * padahal TTL-nya 30 hari. Test ini menjaga hal itu.
     */
    @Test
    fun chatPruneKeepsParserEntriesOlderThanChatTtl() = runBlocking {
        val parserKey = AiCacheKey.forParserClause("45000 nasi padang")
        cache.insertCache(
            AiCache(
                queryHash = parserKey,
                rawInput = "45000 nasi padang",
                responseJson = "[]",
                createdAt = System.currentTimeMillis() - 30 * 60 * 1000
            )
        )

        programFake("Total pengeluaran hari ini Rp 85.000.")
        manager().processQuery("berapa pengeluaran hari ini?")

        assertNotNull(
            "prune dari jalur chat tidak boleh menghapus entri parser yang lebih tua",
            cache.getCache(parserKey)
        )
    }
}
