package com.example.ai

import org.json.JSONArray
import org.json.JSONObject
import com.example.RupiahFormatter
import java.util.Locale

/**
 * Post-validation untuk jawaban LLM.
 *
 * Celah yang ditangani: numeric hallucination ("120rb" padahal DB punya
 * 1.045.800) dan false negative ("tidak ada" padahal context punya rows).
 *
 * Strategi:
 *  1. Extract angka dari jawaban LLM (handle format: 120000, 120.000, 120,000,
 *     1.2jt, 120rb, 1.5K, 1M).
 *  2. Extract angka dari context (raw tool result JSON + IDR-prefixed lines).
 *  3. Bandingkan: setiap angka di jawaban (>= threshold) harus punya match
 *     di context (tolerance ±1 untuk rounding). Kalau tidak, jawaban
 *     diregenerasi dari context (template polos, tanpa LLM).
 *  4. False negative check: kalau context berisi transaksi / total > 0
 *     tapi jawaban bilang "tidak ada" / "kosong" / "no record", regenerate.
 */
object AnswerValidator {

    /** Angka >= threshold ini divalidate. Di bawah threshold dianggap ID/count/urutan. */
    private const val MIN_VALIDATE = 100L

    /** Toleransi pembulatan. */
    private const val TOLERANCE = 1L

    /** Frasa yang menandakan false negative. */
    private val NEGATIVE_PHRASES = listOf(
        "tidak ada", "tidak ditemukan", "kosong", "belum ada", "no record",
        "no transaction", "nothing found", "no data", "belum tercatat",
        "ga ada", "gak ada", "nggak ada"
    )

    /**
     * Validate & possibly regenerate jawaban LLM.
     *
     * @param llmAnswer jawaban akhir dari LLM (final step, conversational)
     * @param contextString context string yang di-pass ke LLM (untuk audit)
     * @param toolResultJson raw JSON content dari tool (ground truth)
     * @return final answer — original kalau valid, atau template dari context
     */
    fun validate(
        llmAnswer: String,
        contextString: String,
        toolResultJson: String?
    ): String {
        val groundTruth = extractNumbersFromToolResult(toolResultJson)
            .union(extractNumbersFromText(contextString))
            .toSet()

        val groundTruthHasData = groundTruth.isNotEmpty() ||
            (toolResultJson?.let { hasActualData(it) } ?: false)

        // False negative guard.
        if (groundTruthHasData && containsNegativePhrase(llmAnswer)) {
            return regenerateFromContext(toolResultJson, contextString)
        }

        // Empty / missing context tapi LLM kasih angka besar → mencurigakan.
        if (groundTruth.isEmpty() && hasLargeNumberWithoutContext(llmAnswer)) {
            return "Maaf, saya tidak menemukan data yang relevan untuk pertanyaan itu."
        }

        // Validate angka besar di jawaban.
        val answerNumbers = extractNumbersFromText(llmAnswer)
            .filter { it >= MIN_VALIDATE }
        if (answerNumbers.isEmpty()) {
            return llmAnswer // tidak ada angka besar, tidak perlu validasi
        }

        val allMatched = answerNumbers.all { ans ->
            groundTruth.any { gt -> kotlin.math.abs(ans - gt) <= TOLERANCE }
        }

        if (!allMatched) {
            return regenerateFromContext(toolResultJson, contextString)
        }

        return llmAnswer
    }

    /**
     * Extract angka dari teks. Menangani:
     *  - "120.000", "120,000"  (ribuan dengan separator)
     *  - "1.045.800"           (Indo style)
     *  - "1.2jt", "120rb"      (suffix Indonesia)
     *  - "1.5K", "1M"          (suffix English)
     *  - "IDR 120000"          (plain)
     *  - "Rp120.000"           (prefix + Indo)
     */
    fun extractNumbersFromText(text: String): Set<Long> {
        if (text.isBlank()) return emptySet()
        val out = LinkedHashSet<Long>()
        var remainingText = text

        // Pattern dengan suffix bahasa: 1.2jt, 120rb, 1.5K, 1M
        val suffixPattern = Regex(
            """(\d{1,3}(?:[.,]\d{1,3})?)\s*(jt|rb|JT|RB|Jt|Rb|k|K|m|M)\b""",
            RegexOption.IGNORE_CASE
        )
        for (m in suffixPattern.findAll(text)) {
            val raw = m.groupValues[1].replace(',', '.')
            val num = raw.toDoubleOrNull() ?: continue
            val multiplier = when (m.groupValues[2].lowercase()) {
                "rb", "k" -> 1_000L
                "jt", "m" -> 1_000_000L
                else -> 1L
            }
            out.add((num * multiplier).toLong())
            remainingText = remainingText.replace(m.value, " ")
        }

        // Pattern angka dengan separator: 120.000, 120,000, 1.045.800, 1,045,800
        // Hindari match desimal biasa (1.2 yang bukan ribuan)
        val sepPattern = Regex("""\b(\d{1,3}(?:[.,]\d{3})+)\b""")
        for (m in sepPattern.findAll(text)) {
            val raw = m.groupValues[1]
            val cleaned = if (raw.contains('.') && raw.lastIndexOf('.') > 2) {
                // Indo: 1.045.800 → 1045800
                raw.replace(".", "").toLongOrNull()
            } else if (raw.contains(',') && raw.lastIndexOf(',') > 2) {
                // US: 1,045,800 → 1045800
                raw.replace(",", "").toLongOrNull()
            } else {
                // 120.000 ambigu — coba ribuan
                if (raw.contains('.')) raw.replace(".", "").toLongOrNull()
                else raw.replace(",", "").toLongOrNull()
            }
            if (cleaned != null) {
                out.add(cleaned)
                remainingText = remainingText.replace(raw, " ")
            }
        }

        // Plain integer >= 3 digit
        val plainPattern = Regex("""\b(\d{3,})\b""")
        for (m in plainPattern.findAll(remainingText)) {
            m.groupValues[1].toLongOrNull()?.let { out.add(it) }
        }

        return out
    }

    /**
     * Extract angka dari raw tool result JSON.
     * Untuk query_transactions: kumpulkan semua amount.
 * Untuk get_expenses/get_income/get_balance: kumpulkan total + breakdown totals.
     */
    fun extractNumbersFromToolResult(json: String?): Set<Long> {
        if (json.isNullOrBlank()) return emptySet()
        val out = LinkedHashSet<Long>()
        try {
            val obj = JSONObject(json)
            // Skip error payloads
            if (obj.has("error")) return emptySet()

            obj.opt("totalExpense")?.let { out.add(toLongSafe(it)) }
            obj.opt("totalIncome")?.let { out.add(toLongSafe(it)) }
            obj.opt("balance")?.let { out.add(toLongSafe(it)) }
            val incomeBreakdown = obj.optJSONArray("incomeBreakdown")
            if (incomeBreakdown != null) {
                for (i in 0 until incomeBreakdown.length()) {
                    val item = incomeBreakdown.getJSONObject(i)
                    item.opt("total")?.let { out.add(toLongSafe(it)) }
                }
            }

            val breakdown = obj.optJSONArray("breakdown")
            if (breakdown != null) {
                for (i in 0 until breakdown.length()) {
                    val item = breakdown.getJSONObject(i)
                    item.opt("total")?.let { out.add(toLongSafe(it)) }
                }
            }
            val transactions = obj.optJSONArray("transactions")
            if (transactions != null) {
                for (i in 0 until transactions.length()) {
                    val item = transactions.getJSONObject(i)
                    item.opt("amount")?.let { out.add(toLongSafe(it)) }
                }
            }
            obj.opt("rowCount")?.let { /* informational, skip */ }
            obj.opt("transactionCount")?.let { /* informational, skip */ }
        } catch (_: Exception) {
            // not JSON or partial — ignore
        }
        return out
    }

    private fun toLongSafe(v: Any?): Long {
        return when (v) {
            is Number -> v.toLong()
            is String -> v.replace(".", "").replace(",", "").toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    private fun hasActualData(json: String): Boolean {
        return try {
            val obj = JSONObject(json)
            val total = obj.opt("totalExpense") as? Number
            if ((total?.toLong() ?: 0L) > 0L) return true
            val tx = obj.optJSONArray("transactions")
            if (tx != null && tx.length() > 0) return true
            val bd = obj.optJSONArray("breakdown")
            if (bd != null && bd.length() > 0) return true
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun containsNegativePhrase(text: String): Boolean {
        val lower = text.lowercase(Locale.getDefault())
        return NEGATIVE_PHRASES.any { lower.contains(it) }
    }

    private fun hasLargeNumberWithoutContext(text: String): Boolean {
        return extractNumbersFromText(text).any { it >= MIN_VALIDATE }
    }

    /**
     * Template-based answer builder. Dipakai kalau validasi gagal — kita
     * tidak bisa trust angka LLM, jadi kita generate jawaban polos dari
     * ground truth (raw tool result).
     */
    fun regenerateFromContext(
        toolResultJson: String?,
        contextString: String
    ): String {
        if (toolResultJson.isNullOrBlank()) {
            return "Maaf, saya tidak dapat menemukan data yang relevan."
        }
        return try {
            val obj = JSONObject(toolResultJson)
            when (obj.optString("tool")) {
                "query_transactions" -> formatQueryTransactions(obj)
                "get_expenses" -> formatGetExpenses(obj)
                "get_income" -> formatGetIncome(obj)
                "get_balance" -> formatGetBalance(obj)
                else -> {
                    // Unknown / error — fall back ke context string.
                    contextString.ifBlank { "Maaf, data tidak tersedia." }
                }
            }
        } catch (_: Exception) {
            contextString.ifBlank { "Maaf, data tidak tersedia." }
        }
    }

    private fun formatQueryTransactions(obj: JSONObject): String {
        val txArr: JSONArray = obj.optJSONArray("transactions") ?: JSONArray()
        val totalExpense = (obj.opt("totalExpense") as? Number)?.toLong() ?: 0L
        val rowCount = obj.optInt("rowCount", txArr.length())
        val truncated = obj.optBoolean("truncated", false)

        if (txArr.length() == 0) {
            return "Tidak ditemukan transaksi yang cocok dengan kriteria tersebut."
        }

        val lines = ArrayList<String>()
        lines.add("Ditemukan $rowCount transaksi" +
            (if (truncated) " (ditampilkan max 50, total lebih banyak)" else "") + ":")
        for (i in 0 until txArr.length()) {
            val tx = txArr.getJSONObject(i)
            val amount = (tx.opt("amount") as? Number)?.toLong() ?: 0L
            val desc = tx.optString("description", "")
            val cat = tx.optString("category", "")
            val type = tx.optString("type", "EXPENSE")
            lines.add("- ${formatRupiah(amount)} ($type) — $desc [${cat}]")
        }
        if (totalExpense > 0L) {
            lines.add("")
            lines.add("Total ${if (rowCount > 1) "pengeluaran" else ""}: ${formatRupiah(totalExpense)}")
        }
        return lines.joinToString("\n")
    }

    private fun formatGetExpenses(obj: JSONObject): String {
        val totalExpense = (obj.opt("totalExpense") as? Number)?.toLong() ?: 0L
        val dateRange = obj.optString("dateRange", "all")
        val categoryFilter = obj.optString("categoryFilter", "").takeIf { it.isNotEmpty() && it != "null" }
        val breakdown = obj.optJSONArray("breakdown") ?: JSONArray()
        val period = formatPeriod(obj, dateRange)
        val sb = StringBuilder()
        sb.appendLine(if (categoryFilter != null) "Pengeluaran kategori '$categoryFilter' ($period):" else "Pengeluaran ($period):")
        sb.appendLine("Total: ${formatRupiah(totalExpense)}")
        if (breakdown.length() > 0) {
            sb.appendLine("Per kategori:")
            for (i in 0 until breakdown.length()) {
                val item = breakdown.getJSONObject(i)
                val name = item.optString("category", "")
                val total = (item.opt("total") as? Number)?.toLong() ?: 0L
                sb.appendLine("- $name: ${formatRupiah(total)}")
            }
        }
        return sb.toString().trimEnd()
    }

    private fun formatGetIncome(obj: JSONObject): String {
        val totalIncome = (obj.opt("totalIncome") as? Number)?.toLong() ?: 0L
        val dateRange = obj.optString("dateRange", "all")
        val categoryFilter = obj.optString("categoryFilter", "").takeIf { it.isNotEmpty() && it != "null" }
        val breakdown = obj.optJSONArray("incomeBreakdown") ?: JSONArray()
        val period = formatPeriod(obj, dateRange)
        val sb = StringBuilder()
        sb.appendLine(if (categoryFilter != null) "Pemasukan kategori '$categoryFilter' ($period):" else "Pemasukan ($period):")
        sb.appendLine("Total: ${formatRupiah(totalIncome)}")
        if (breakdown.length() > 0) {
            sb.appendLine("Per kategori:")
            for (i in 0 until breakdown.length()) {
                val item = breakdown.getJSONObject(i)
                val name = item.optString("category", "")
                val total = (item.opt("total") as? Number)?.toLong() ?: 0L
                sb.appendLine("- $name: ${formatRupiah(total)}")
            }
        }
        return sb.toString().trimEnd()
    }

    private fun formatGetBalance(obj: JSONObject): String {
        val totalExpense = (obj.opt("totalExpense") as? Number)?.toLong() ?: 0L
        val totalIncome = (obj.opt("totalIncome") as? Number)?.toLong() ?: 0L
        val balance = (obj.opt("balance") as? Number)?.toLong() ?: (totalIncome - totalExpense)
        val dateRange = obj.optString("dateRange", "all")
        val period = formatPeriod(obj, dateRange)
        val sb = StringBuilder()
        sb.appendLine("Saldo ($period):")
        sb.appendLine("Saldo: ${formatRupiah(balance)}")
        sb.appendLine("Pemasukan: ${formatRupiah(totalIncome)}")
        sb.appendLine("Pengeluaran: ${formatRupiah(totalExpense)}")
        return sb.toString().trimEnd()
    }

    private fun formatPeriod(obj: JSONObject, dateRange: String): String {
        val days = obj.optInt("days", -1).takeIf { it > 0 }
        val weeks = obj.optInt("weeks", -1).takeIf { it > 0 }
        val months = obj.optInt("months", -1).takeIf { it > 0 }
        return when {
            days != null -> "$days hari terakhir"
            weeks != null -> "$weeks minggu terakhir"
            months != null -> "$months bulan terakhir"
            else -> when (dateRange) {
                "today" -> "hari ini"
                "yesterday" -> "kemarin"
                "week" -> "7 hari terakhir"
                "month" -> "30 hari terakhir"
                "all" -> "semua waktu"
                else -> dateRange
            }
        }
    }

    fun formatRupiah(amount: Long): String = RupiahFormatter.format(amount)
}
