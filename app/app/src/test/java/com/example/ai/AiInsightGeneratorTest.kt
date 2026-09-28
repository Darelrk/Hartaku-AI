package com.example.ai

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
class AiInsightGeneratorTest {

    private lateinit var fakeChatClient: FakeChatClient
    private lateinit var generator: AiInsightGenerator

    @Before
    fun setUp() {
        fakeChatClient = FakeChatClient()
        generator = AiInsightGenerator(fakeChatClient)
    }

    @Test
    fun testGenerate_Success_AiResponse() = runTest {
        val mockJsonResponse = """
            {
              "insight": "Pengeluaran Anda naik sedikit hari ini.",
              "comparison": "naik",
              "persen_perubahan": 8,
              "top_kategori": "Makanan",
              "saran": "Coba masak sendiri untuk berhemat."
            }
        """.trimIndent()

        fakeChatClient.responseToReturn = Result.success(
            ChatResponse(
                content = mockJsonResponse,
                finishReason = "stop",
                promptTokens = 150,
                completionTokens = 45
            )
        )

        val input = AiInsightGenerator.InsightData(
            totalExpense = 108000.0,
            yesterdayExpense = 100000.0,
            totalIncome = 200000.0,
            transactionCount = 3,
            topCategory = "Makanan",
            topCategoryAmount = 80000.0
        )

        val result = generator.generate(input)
        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals("Pengeluaran Anda naik sedikit hari ini.", data.insight)
        assertEquals("Coba masak sendiri untuk berhemat.", data.saran)
    }

    @Test
    fun testGenerate_Failure_FallbackCalculations() = runTest {
        // Force error to trigger fallback logic
        fakeChatClient.responseToReturn = Result.failure(Exception("NIM LLM Offline"))

        val input = AiInsightGenerator.InsightData(
            totalExpense = 150000.0,
            yesterdayExpense = 100000.0,
            totalIncome = 0.0,
            transactionCount = 2,
            topCategory = "Makanan",
            topCategoryAmount = 150000.0
        )

        val result = generator.generate(input)
        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        // Check food category specific fallback advice
        assertEquals("Coba masak sendiri untuk kurangi pengeluaran.", data.saran)
        assertTrue(data.insight.contains("naik 50%"))
    }

    @Test
    fun testGenerate_Fallback_DownwardsChange() = runTest {
        fakeChatClient.responseToReturn = Result.failure(Exception("API Failed"))

        val input = AiInsightGenerator.InsightData(
            totalExpense = 50000.0,
            yesterdayExpense = 100000.0,
            totalIncome = 50000.0,
            transactionCount = 1,
            topCategory = "Transport",
            topCategoryAmount = 50000.0
        )

        val result = generator.generate(input)
        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals("Pertimbangkan transportasi umum yang lebih hemat.", data.saran)
        assertTrue(data.insight.contains("turun 50%"))
    }

    @Test
    fun testGenerate_Fallback_NoExpense() = runTest {
        fakeChatClient.responseToReturn = Result.failure(Exception("Timeout"))

        val input = AiInsightGenerator.InsightData(
            totalExpense = 0.0,
            yesterdayExpense = 0.0,
            totalIncome = 0.0,
            transactionCount = 0,
            topCategory = null,
            topCategoryAmount = 0.0
        )

        val result = generator.generate(input)
        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals("Total pengeluaran hari ini Rp 0.", data.insight)
        assertEquals("Terus pantau pengeluaranmu secara rutin.", data.saran)
    }
}
