package com.example.ai

import com.example.data.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Local rule-based chat backend — fallback when NIM API key is unavailable.
 *
 * Reuses [ToolCallExecutor] untuk query langsung ke database, dan
 * [AnswerValidator.regenerateFromContext] untuk format jawaban natural language.
 * Intent detection (priority order):
 *  1. Small talk / greeting → hardcoded reply
 *  2. "pemasukan/income/gaji" → `get_income`
 *  3. "pengeluaran/belanja/biaya" → `get_expenses`
 *  4. "saldo/sisa/neraca" → `get_balance`
 *  5. "tampilkan" / "list" / "ada" / "cari" / "transaksi" → `query_transactions`
 *  6. Default → `query_transactions` dengan dateRange=last_n_days=7
 *
 * Date range resolution (Indonesian keywords):
 *  - "hari ini" / "sekarang" / "today" → today
 *  - "kemarin" / "yesterday" → yesterday
 *  - "minggu ini" / "7 hari" → week
 *  - "bulan ini" / "30 hari" → month
 *  - "N hari terakhir" → last_n_days=N
 *  - "N minggu terakhir" → last_n_weeks=N
 *  - "N bulan terakhir" → last_n_months=N
 *  - default → last_n_days=7
 */
open class LocalRuleBasedChat(
    private val transactionRepository: TransactionRepository
) {


    private val toolExecutor = ToolCallExecutor(transactionRepository)
    open suspend fun processQuery(
        userQuery: String,
        history: List<ChatMessageItem> = emptyList()
    ): String = withContext(Dispatchers.Default) {
        ChatLogger.d("LocalChat", "processQuery START: '$userQuery', history=${history.size}")
        return@withContext try {
            val intent = detectIntent(userQuery)
            ChatLogger.d("LocalChat", "processQuery intent=${intent::class.simpleName}")
            when (intent) {
                is ChatIntent.SmallTalk -> {
                    ChatLogger.d("LocalChat", "processQuery SmallTalk reply='${intent.reply.take(50)}'")
                    intent.reply
                }
                is ChatIntent.Tool -> {
                    val toolResult = toolExecutor.execute(intent.toolCall)
                    if (looksLikeError(toolResult.content)) {
                        "Maaf, saya tidak bisa mengakses data transaksi saat ini."
                    } else {
                        AnswerValidator.regenerateFromContext(
                            toolResultJson = toolResult.content,
                            contextString = "Tool result: ${toolResult.content}"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            "Maaf, terjadi kesalahan saat memproses pertanyaan: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    internal fun detectIntent(query: String): ChatIntent {
        val q = query.lowercase().trim()

         if (SMALL_TALK.any { q.contains(it) }) {
            return ChatIntent.SmallTalk(reply = pickGreeting(q))
        }

        val dateArgs = extractDateRange(q)
        val category = extractCategory(q)
        val keyword = extractKeyword(q)
        val toolName = pickTool(q)

        val args = JSONObject().apply {
            put("dateRange", dateArgs.first)
            dateArgs.second?.let { put(it.first, it.second) }
            if (category != null) put("category", category)
            if (keyword != null) put("keyword", keyword)
        }.toString()

        val toolCall = ToolCall(
            id = "local-${System.currentTimeMillis()}",
            name = toolName,
            argumentsJson = args
        )
        return ChatIntent.Tool(toolCall)
    }

    private fun pickGreeting(q: String): String {
        return when {
            q.contains("apa kabar") || q.contains("kabarnya") ->
                "Halo! Saya HartaKu AI, siap bantu kamu kelola keuangan. Coba tanya \"berapa pengeluaran minggu ini?\" atau \"tampilkan transaksi kopi 5 hari terakhir\"."
            q.contains("halo") || q.contains("hai") || q.contains("hi ") || q.contains("hello") ->
                "Halo! Ada yang bisa saya bantu soal keuanganmu?"
            q.contains("terima kasih") || q.contains("makasih") || q.contains("thanks") ->
                "Sama-sama! Kalau ada pertanyaan lain, langsung tanya saja."
            q.contains("siapa kamu") || q.contains("kamu siapa") ->
                "Saya HartaKu AI, asisten keuangan pribadimu. Bisa bantu cek transaksi, ringkasan, dan analisis kategori."
            q.contains("bantu") || q.contains("help") ->
                "Tentu! Kamu bisa tanya soal:\n- Total pengeluaran / pemasukan (hari ini, minggu ini, bulan ini)\n- Cari transaksi tertentu (mis. \"ada transaksi kopi?\")\n- Breakdown per kategori"
            else ->
                "Halo! Saya HartaKu AI. Mau tanya soal keuangan apa hari ini?"
        }
    }

    private fun extractDateRange(q: String): Pair<String, Pair<String, Int>?> {
        when {
            q.contains("hari ini") || q.contains("sekarang") || q.contains("today") ->
                return "today" to null
            q.contains("kemarin") || q.contains("yesterday") ->
                return "yesterday" to null
            q.contains("minggu ini") || q.contains("7 hari terakhir") || q.contains("seminggu") ->
                return "week" to null
            q.contains("bulan ini") || q.contains("30 hari terakhir") || q.contains("sebulan") ->
                return "month" to null
            q.contains("semua") || q.contains("all") || q.contains("sepanjang waktu") ->
                return "all" to null
        }
        val daysMatch = Regex("""(\d+)\s*hari(?:\s*terakhir|\s*lalu)?""").find(q)
        if (daysMatch != null) {
            val n = daysMatch.groupValues[1].toIntOrNull() ?: 0
            if (n in 1..365) return "last_n_days" to ("days" to n)
        }
        val weeksMatch = Regex("""(\d+)\s*minggu(?:\s*terakhir|\s*lalu)?""").find(q)
        if (weeksMatch != null) {
            val n = weeksMatch.groupValues[1].toIntOrNull() ?: 0
            if (n in 1..52) return "last_n_weeks" to ("weeks" to n)
        }
        val monthsMatch = Regex("""(\d+)\s*bulan(?:\s*terakhir|\s*lalu)?""").find(q)
        if (monthsMatch != null) {
            val n = monthsMatch.groupValues[1].toIntOrNull() ?: 0
            if (n in 1..36) return "last_n_months" to ("months" to n)
        }
        return "last_n_days" to ("days" to 7)
    }

    private fun extractCategory(q: String): String? {
        for (cat in CATEGORIES) {
            if (Regex("""\b${Regex.escape(cat.lowercase())}\b""").containsMatchIn(q)) {
                return cat
            }
        }
        return null
    }

    private fun extractKeyword(q: String): String? {
        val quoted = Regex(""""([^"]+)"""").find(q)?.groupValues?.get(1)
        if (quoted != null) return quoted.trim()
        val patterns = listOf(
            Regex("""transaksi\s+([\w\s]+?)(?:\?|$|hari|minggu|bulan|di|yang)"""),
            Regex("""cari\s+([\w\s]+?)(?:\?|$|hari|minggu|bulan|di|yang)"""),
            Regex("""pembelian\s+([\w\s]+?)(?:\?|$|hari|minggu|bulan|di|yang)""")
        )
        for (p in patterns) {
            val m = p.find(q)
            if (m != null) {
                val kw = m.groupValues[1].trim()
                if (kw.isNotBlank() && !STOP_WORDS.any { it == kw.lowercase() }) {
                    return kw
                }
            }
        }
        return null
    }

    private fun pickTool(q: String): String {
        val incomeKeywords = listOf("pemasukan", "income", "gaji", "pendapatan")
        val balanceKeywords = listOf("saldo", "sisa", "balance", "neraca", "net")
        val expenseKeywords = listOf("pengeluaran", "belanja", "expense", "biaya", "bayar", "uang keluar")
        val queryKeywords = listOf("tampilkan", "list", "ada", "cari", "transaksi", "pembelian")
        return when {
            balanceKeywords.any { q.contains(it) } -> "get_balance"
            incomeKeywords.any { q.contains(it) } -> "get_income"
            expenseKeywords.any { q.contains(it) } -> "get_expenses"
            queryKeywords.any { q.contains(it) } -> "query_transactions"
            // "berapa" / "total" tanpa keyword spesifik → default ke pengeluaran
            listOf("berapa", "total", "ringkasan", "breakdown", "summary", "jumlah", "rekap").any { q.contains(it) } -> "get_expenses"
            else -> "query_transactions"
        }
    }

    private fun looksLikeError(json: String): Boolean {
        return try {
            JSONObject(json).has("error")
        } catch (_: Exception) {
            false
        }
    }

    internal sealed class ChatIntent {
        data class SmallTalk(val reply: String) : ChatIntent()
        data class Tool(val toolCall: ToolCall) : ChatIntent()
    }

    companion object {
        private val CATEGORIES = listOf(
            "Makanan", "Transport", "Belanja", "Hiburan", "Tagihan",
            "Investasi", "Kesehatan", "Pendidikan", "Perawatan Diri",
            "Olahraga", "Donasi", "Asuransi", "Perbaikan", "Peliharaan",
            "Lainnya", "Gaji", "Penjualan", "Hadiah"
        )

        private val SMALL_TALK = listOf(
            "halo", "hai", "hi ", "hello", "apa kabar", "kabarnya",
            "terima kasih", "makasih", "thanks",
            "siapa kamu", "kamu siapa",
            "bantu", "help"
        )

        private val STOP_WORDS = setOf(
            "apa", "berapa", "kapan", "dimana", "siapa", "mengapa", "kenapa",
            "saya", "aku", "kamu", "kami", "kita", "mereka",
            "yang", "di", "ke", "dari", "untuk", "dengan", "pada", "ini", "itu",
            "hari", "minggu", "bulan", "tahun",
            "ada", "belum", "sudah", "sedang", "masih", "akan",
            "transaksi", "pembelian", "pengeluaran", "pemasukan"
        )
    }
}
