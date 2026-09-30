package com.example.ai

import com.example.data.Category
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TransactionAiParserTest {

    private lateinit var fakeChatClient: FakeChatClient
    private lateinit var parser: TransactionAiParser

    private val sampleCategories = listOf(
        Category(id = "1", name = "Makanan", slug = "makanan", typeClass = "EXPENSE", aliases = "[\"makan\", \"bakso\", \"kopi\"]"),
        Category(id = "2", name = "Gaji", slug = "gaji", typeClass = "INCOME", aliases = "[\"gaji\", \"gajian\"]"),
        Category(id = "3", name = "Transport", slug = "transport", typeClass = "EXPENSE", aliases = "[\"bensin\", \"ojek\"]")
    )

    @Before
    fun setUp() {
        fakeChatClient = FakeChatClient()
        parser = TransactionAiParser(fakeChatClient)
    }

    @Test
    fun testPreprocessNominals() {
        val parser = TransactionAiParser(FakeChatClient())
        assertEquals("beli saham 500000", parser.preprocessNominals("beli saham 500rb"))
        assertEquals("beli crypto 200000", parser.preprocessNominals("beli crypto 200k"))
        assertEquals("topup bibit 100000", parser.preprocessNominals("topup bibit 100ribu"))
        assertEquals("gaji harian 2000000", parser.preprocessNominals("gaji harian 2jt"))
        assertEquals("saham 10000000000", parser.preprocessNominals("saham 10M"))
    }

    @Test
    fun testRegexFallback_multipliers() = runTest {
        // Test parsing with regex fallback when AI fails
        fakeChatClient.responseToReturn = Result.failure(Exception("AI Unavailable"))

        val result1 = parser.parse("makan bakso 15rb", sampleCategories)
        assertEquals("regex", result1.source)
        assertEquals(1, result1.transactions.size)
        assertEquals("expense", result1.transactions[0].type)
        assertEquals("Makanan", result1.transactions[0].category)
        assertEquals(15000.0, result1.transactions[0].amount, 0.1)

        val result2 = parser.parse("gaji harian 2jt", sampleCategories)
        assertEquals("regex", result2.source)
        assertEquals(1, result2.transactions.size)
        assertEquals("income", result2.transactions[0].type)
        assertEquals("Gaji", result2.transactions[0].category)
        assertEquals(2000000.0, result2.transactions[0].amount, 0.1)

        val result3 = parser.parse("ojek 10k", sampleCategories)
        assertEquals("regex", result3.source)
        assertEquals(1, result3.transactions.size)
        assertEquals("expense", result3.transactions[0].type)
        assertEquals("Transport", result3.transactions[0].category)
        assertEquals(10000.0, result3.transactions[0].amount, 0.1)
    }

    @Test
    fun testAiSuccessResponseMapping() = runTest {
        // Program the fake chat client to return valid JSON representing parsed transaction
        val mockJsonResponse = """
            [{"type": "expense", "category": "Makanan", "amount": 25000, "description": "Nasi Goreng"}]
        """.trimIndent()
        fakeChatClient.responseToReturn = Result.success(
            ChatResponse(
                content = mockJsonResponse,
                finishReason = "stop",
                promptTokens = 100,
                completionTokens = 30
            )
        )

        val result = parser.parse("nasi goreng 25000", sampleCategories)
        assertEquals("ai", result.source)
        assertEquals(1, result.transactions.size)
        assertEquals("expense", result.transactions[0].type)
        assertEquals("Makanan", result.transactions[0].category)
        assertEquals(25000.0, result.transactions[0].amount, 0.1)
        assertEquals("Nasi Goreng", result.transactions[0].description)
    }

    @Test
    fun testCachingBehavior() = runTest {
        val mockJsonResponse = """
            [{"type": "expense", "category": "Makanan", "amount": 25000, "description": "Nasi Goreng"}]
        """.trimIndent()
        fakeChatClient.responseToReturn = Result.success(
            ChatResponse(
                content = mockJsonResponse,
                finishReason = "stop",
                promptTokens = 100,
                completionTokens = 30
            )
        )

        // Mock Dao to test caching behavior
        val mockCacheDao = object : com.example.data.AiCacheDao {
            val cache = mutableMapOf<String, com.example.data.AiCache>()
            override suspend fun getCache(hash: String): com.example.data.AiCache? = cache[hash]
            override suspend fun insertCache(cache: com.example.data.AiCache) {
                this.cache[cache.queryHash] = cache
            }
            override suspend fun deleteExpired(now: Long, ttlMillis: Long) {}
            override suspend fun clearAll() {}
        }

        val cachedParser = TransactionAiParser(fakeChatClient, mockCacheDao)

        // First parse hits network and saves to cache
        val result1 = cachedParser.parse("nasi goreng 25000", sampleCategories)
        assertEquals("ai", result1.source)
        assertEquals(1, mockCacheDao.cache.size)

        // Reset fake response to throw or return error to ensure subsequent calls are served from cache
        fakeChatClient.responseToReturn = Result.failure(Exception("AI Offline"))
        val result2 = cachedParser.parse("nasi goreng 25000", sampleCategories)
        assertEquals("ai", result2.source) // Still 'ai', not 'regex' fallback
        assertEquals(1, result2.transactions.size)
        assertEquals("Makanan", result2.transactions[0].category)
        assertEquals(25000.0, result2.transactions[0].amount, 0.1)
    }

    @Test
    fun testAiResponseArrayOfTransactions() = runTest {
        // Program fake chat client to return transaction per clause
        fakeChatClient.responseMapper = { msg ->
            val json = if (msg.contains("es teh")) {
                """[{"type": "expense", "category": "Makanan", "amount": 15000, "description": "Es Teh"}]"""
            } else {
                """[{"type": "expense", "category": "Transport", "amount": 20000, "description": "Grab"}]"""
            }
            Result.success(ChatResponse(json, "stop", 60, 25))
        }

        val result = parser.parse("es teh 15000 dan grab 20000", sampleCategories)
        assertEquals("ai", result.source)
        assertEquals(2, result.transactions.size)
        assertEquals("Makanan", result.transactions[0].category)
        assertEquals(15000.0, result.transactions[0].amount, 0.1)
        assertEquals("Transport", result.transactions[1].category)
        assertEquals(20000.0, result.transactions[1].amount, 0.1)
    }

    @Test
    fun testAiResponseBadFormatFallsBackToRegex() = runTest {
        // Program fake chat client to return bad JSON format
        fakeChatClient.responseToReturn = Result.success(
            ChatResponse(
                content = "This is not valid JSON string",
                finishReason = "stop",
                promptTokens = 80,
                completionTokens = 10
            )
        )

        // It should fallback to regex
        val result = parser.parse("ojek 12000", sampleCategories)
        assertEquals("regex", result.source)
        assertEquals(1, result.transactions.size)
        assertEquals("Transport", result.transactions[0].category)
        assertEquals(12000.0, result.transactions[0].amount, 0.1)
    }

    @Test
    fun testRegexFallbackFiltersOutZeroAmountGarbage() = runTest {
        fakeChatClient.responseToReturn = Result.failure(Exception("AI Offline"))
        
        // Input containing a transaction mixed with complaints (which gets split by comma)
        val input = "makan bakso 15rb, aplikasinya agak error di mic"
        val result = parser.parse(input, sampleCategories)
        
        assertEquals("regex", result.source)
        // It should only have 1 transaction (makan bakso) and ignore the complaint (which has 0 amount)
        assertEquals(1, result.transactions.size)
        assertEquals("Makanan", result.transactions[0].category)
        assertEquals(15000.0, result.transactions[0].amount, 0.1)
    }

    @Test
    fun testAiResponseReturnsEmptyArrayForNonFinancialText() = runTest {
        // AI detects non-financial text and returns []
        val mockJsonResponse = "[]"
        fakeChatClient.responseToReturn = Result.success(
            ChatResponse(
                content = mockJsonResponse,
                finishReason = "stop",
                promptTokens = 50,
                completionTokens = 10
            )
        )

        val result = parser.parse("tes mic satu dua tiga", sampleCategories)
        assertEquals("ai", result.source)
        assertTrue(result.transactions.isEmpty())
    }

    @Test
    fun testRegexFallbackReturnsEmptyWhenNoAmountDetected() = runTest {
        fakeChatClient.responseToReturn = Result.failure(Exception("AI Offline"))

        // Input with no numbers/amount at all
        val input = "tes mic satu dua tiga"
        val result = parser.parse(input, sampleCategories)

        assertEquals("regex", result.source)
        assertTrue(result.transactions.isEmpty())
    }

    @Test
    fun testRegexFallback_makan12Ribu_descriptionClean() = runTest {
        // Bug: "makan 12 ribu" → sebelumnya description jadi "makan ribu" (kata "ribu" tertinggal)
        // Fix: strip multiplier keyword juga dari description
        fakeChatClient.responseToReturn = Result.failure(Exception("AI Offline"))

        val result = parser.parse("makan 12 ribu", sampleCategories)
        assertEquals("regex", result.source)
        assertEquals(1, result.transactions.size)
        assertEquals(12000.0, result.transactions[0].amount, 0.1)
        assertEquals("Makanan", result.transactions[0].category)
        assertEquals("makan", result.transactions[0].description)
    }

    @Test
    fun testRegexFallback_indonesianThousandSeparator() = runTest {
        // Format Indonesia: "1.500.000" = 1500000
        fakeChatClient.responseToReturn = Result.failure(Exception("AI Offline"))

        val result = parser.parse("gaji 1.500.000", sampleCategories)
        assertEquals("regex", result.source)
        assertEquals(1, result.transactions.size)
        assertEquals(1500000.0, result.transactions[0].amount, 0.1)
        assertEquals("income", result.transactions[0].type)
        assertEquals("Gaji", result.transactions[0].category)
    }

    @Test
    fun testRegexFallback_slangAmount() = runTest {
        // Indonesian slang: goceng=10rb, gopek=5rb, cepe=2rb
        fakeChatClient.responseToReturn = Result.failure(Exception("AI Offline"))

        val result = parser.parse("kopi goceng", sampleCategories)
        assertEquals("regex", result.source)
        assertEquals(1, result.transactions.size)
        assertEquals(10000.0, result.transactions[0].amount, 0.1) // Default Lainnya category now
        assertEquals("Makanan", result.transactions[0].category)
    }

    @Test
    fun testRegexFallback_multiActionSplitting() = runTest {
        // Multi-action: "kopi 15rb terus bensin 50rb" → 2 transaksi
        fakeChatClient.responseToReturn = Result.failure(Exception("AI Offline"))

        val result = parser.parse("kopi 15rb terus bensin 50rb", sampleCategories)
        assertEquals("regex", result.source)
        assertEquals(2, result.transactions.size)
        assertEquals(15000.0, result.transactions[0].amount, 0.1)
        assertEquals(50000.0, result.transactions[1].amount, 0.1)
    }

    @Test
    fun testRegexFallback_stripsRpPrefix() = runTest {
        // "Rp 25.000" harus jadi 25000
        fakeChatClient.responseToReturn = Result.failure(Exception("AI Offline"))

        val result = parser.parse("bakso Rp 25.000", sampleCategories)
        assertEquals("regex", result.source)
        assertEquals(1, result.transactions.size)
        assertEquals(25000.0, result.transactions[0].amount, 0.1)
    }

    @Test
    fun testPreProcess_clauseSplitAndFillerStrip() = runTest {
        // Test clause splitting
        val input = "tadi saya makan bakso 15rb terus ojek 10rb"
        val clauses = parser.clauseSplitPreProcess(input)
        assertEquals(2, clauses.size)
        // Filler words "tadi saya" are stripped
        assertEquals("makan bakso 15rb", clauses[0])
        assertEquals("ojek 10rb", clauses[1])

        // Test parent-child splitting using "isinya"
        val inputParentChild = "belanja di indomaret 50rb, isinya susu 30rb"
        val clausesPC = parser.clauseSplitPreProcess(inputParentChild)
        assertEquals(2, clausesPC.size)
        // Filler word "di" is stripped from "belanja di indomaret 50rb" -> "belanja indomaret 50rb"
        assertEquals("belanja indomaret 50rb", clausesPC[0])
        assertEquals("susu 30rb", clausesPC[1])
    }

    @Test
    fun testPostProcess_markdownFenceExtractionAndSanitization() = runTest {
        // PostProcess extract from markdown fences
        val rawJson = """
            ```json
            [
              {"type": "expense", "category": "Makanan", "amount": 25000, "description": "Nasi Padang", "confidence": 0.9}
            ]
            ```
        """.trimIndent()
        val parsed = parser.postProcess(rawJson, sampleCategories)
        assertEquals(1, parsed.size)
        assertEquals("Makanan", parsed[0].category)
        assertEquals(25000.0, parsed[0].amount, 0.1)
        assertEquals("Nasi Padang", parsed[0].description)
        assertEquals(0.9, parsed[0].confidence, 0.01)
    }

    @Test
    fun testPostProcess_confidenceFiltering() = runTest {
        // High confidence (>= 0.3) is kept, low confidence (< 0.3) is ignored
        val rawJson = """
            [
              {"type": "expense", "category": "Makanan", "amount": 15000, "description": "Soto", "confidence": 0.8},
              {"type": "expense", "category": "Transport", "amount": 20000, "description": "Grab", "confidence": 0.2}
            ]
        """.trimIndent()
        val parsed = parser.postProcess(rawJson, sampleCategories)
        assertEquals(1, parsed.size)
        assertEquals("Soto", parsed[0].description)
    }

    @Test
    fun testPostProcess_categoryReturnsRawUnmatchedCategory() = runTest {
        // AI returns "Pendidikan" which is unmatched, and "makanan" which matches "Makanan" case-insensitively
        val rawJson = """
            [
              {"type": "expense", "category": "makanan", "amount": 15000, "description": "Bakso"},
              {"type": "expense", "category": "pendidikan", "amount": 120000, "description": "Buku"}
            ]
        """.trimIndent()
        val parsed = parser.postProcess(rawJson, sampleCategories)
        assertEquals(2, parsed.size)
        assertEquals("Makanan", parsed[0].category)
        // Returns capitalized raw unmatched category instead of "Lainnya"
        assertEquals("Pendidikan", parsed[1].category)
    }

    @Test
    fun testPostProcess_categoryFuzzyMatching() = runTest {
        // Match using name (case-insensitive) only, alias matching is removed
        val rawJson = """
            [
              {"type": "expense", "category": "makanan", "amount": 15000, "description": "Bakso"},
              {"type": "expense", "category": "ojek", "amount": 10000, "description": "Gojek"},
              {"type": "expense", "category": "gaya hidup", "amount": 50000, "description": "Beli Baju"}
            ]
        """.trimIndent()
        val parsed = parser.postProcess(rawJson, sampleCategories)
        assertEquals(3, parsed.size)
        // makanan matches Makanan name
        assertEquals("Makanan", parsed[0].category)
        // ojek no longer matches Transport alias, falls back to capitalized raw category
        assertEquals("Ojek", parsed[1].category)
        // gaya hidup has no match in sampleCategories, fallbacks to capitalized raw category
        assertEquals("Gaya hidup", parsed[2].category)
    }
}
