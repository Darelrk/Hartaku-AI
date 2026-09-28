package com.example.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.RupiahFormatter

/**
 * AI-powered financial insight generator.
 * Panggil NIM LLM dengan data transaksi → insight natural language (Bahasa Indonesia).
 */
class AiInsightGenerator(private val nimClient: ChatClient) {

    private val TAG = "AiInsightGenerator"

    data class InsightData(
        val totalExpense: Double,
        val yesterdayExpense: Double,
        val totalIncome: Double,
        val transactionCount: Int,
        val topCategory: String?,
        val topCategoryAmount: Double
    )

    data class InsightResult(
        val insight: String,
        val saran: String
    )

    private val systemPrompt = """
Kamu asisten keuangan Indonesia. Generate insight dari data pengeluaran hari ini.
WAJIB: Return ONLY valid JSON, no other text, no markdown.

Format:
{
  "insight": "ringkasan pengeluaran (1-2 kalimat)",
  "saran": "saran keuangan (1 kalimat)"
}

Contoh:
{"insight":"Hari ini pengeluaran Rp 85.000, naik dari kemarin. Kategori terbesar Makanan.","saran":"Coba kurangi jajan kopi minggu ini."}
    """.trimIndent()

    suspend fun generate(data: InsightData): Result<InsightResult> = withContext(Dispatchers.IO) {
        try {
            val userMessage = buildString {
                appendLine("- Total pengeluaran: ${RupiahFormatter.format(data.totalExpense)}")
                appendLine("- Pengeluaran kemarin: ${RupiahFormatter.format(data.yesterdayExpense)}")
                appendLine("- Total pemasukan: ${RupiahFormatter.format(data.totalIncome)}")
                appendLine("- Kategori terbesar: ${data.topCategory ?: "N/A"} (${RupiahFormatter.format(data.topCategoryAmount)})")
            }

            val response = nimClient.chat(
                systemPrompt = systemPrompt,
                userMessage = userMessage,
                maxTokens = 256,
                temperature = 0.3
            )

            if (response.isFailure) {
                Log.w(TAG, "AI insight failed, using fallback: ${response.exceptionOrNull()?.message}")
                return@withContext Result.success(fallbackInsight(data))
            }

            val content = response.getOrThrow().content.trim()
            val json = extractJson(content) ?: return@withContext Result.success(fallbackInsight(data))

            Result.success(InsightResult(
                insight = json.optString("insight", "Tidak ada insight."),
                saran = json.optString("saran", "Terus pantau pengeluaranmu.")
            ))

        } catch (e: Exception) {
            Log.w(TAG, "Insight generation failed: ${e.message}")
            Result.success(fallbackInsight(data))
        }
    }

    private fun extractJson(content: String): org.json.JSONObject? {
        val match = Regex("""(\{.*\})""", RegexOption.DOT_MATCHES_ALL).find(content)
        return match?.let { try { org.json.JSONObject(it.value) } catch (_: Exception) { null } }
    }

    /**
     * Fallback insight (matematika sederhana) — kalau AI gagal atau timeout.
     */
    private fun fallbackInsight(data: InsightData): InsightResult {
        val diff = if (data.yesterdayExpense > 0) {
            ((data.totalExpense - data.yesterdayExpense) / data.yesterdayExpense * 100).toInt()
        } else 0

        val insight = when {
            diff > 0 -> "Hari ini pengeluaran ${RupiahFormatter.format(data.totalExpense)}, naik $diff% dari kemarin."
            diff < 0 -> "Hari ini pengeluaran ${RupiahFormatter.format(data.totalExpense)}, turun ${kotlin.math.abs(diff)}% dari kemarin. Hemat!"
            else -> "Total pengeluaran hari ini ${RupiahFormatter.format(data.totalExpense)}."
        }

        val saran = when {
            data.topCategory == "Makanan" -> "Coba masak sendiri untuk kurangi pengeluaran."
            data.topCategory == "Transport" -> "Pertimbangkan transportasi umum yang lebih hemat."
            data.topCategory == "Hiburan" -> "Batasi langganan streaming yang jarang dipakai."
            data.totalExpense > 200_000 -> "Pengeluaran hari ini cukup besar, evaluasi kebutuhan vs keinginan."
            else -> "Terus pantau pengeluaranmu secara rutin."
        }

        return InsightResult(
            insight = insight,
            saran = saran
        )
    }
}
