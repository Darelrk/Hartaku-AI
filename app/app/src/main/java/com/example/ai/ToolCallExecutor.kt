package com.example.ai

import com.example.data.Transaction
import com.example.data.TransactionRepository
import com.example.data.vector.TransactionVectorBox
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

/**
 * Eksekusi [ToolCall] (dari LLM) terhadap [TransactionRepository].
 * Output berupa JSON string yang akan dikirim balik ke LLM sebagai
 * `role=tool` content (OpenAI function-calling convention).
 *
 * Dua tool yang didukung:
 *  - `query_transactions` → list transaksi (cap 50 rows).
 *    Falls back to ObjectBox HNSW semantic search when keyword doesn't match
 *  - `get_expenses`  → total pengeluaran + breakdown per kategori
 *  - `get_income`     → total pemasukan + breakdown per kategori
 *  - `get_balance`    → saldo bersih (income - expense)
 * Date range resolution:
 *  - "last_n_days"   + days=N
 *  - "last_n_weeks"  + weeks=N
 *  - "last_n_months" + months=N
 *  - "week" / "month" / "today" / "yesterday" / "all"
 */
class ToolCallExecutor(
    private val transactionRepository: TransactionRepository,
    private val embeddingClient: NimEmbeddingClient? = null
) {

    /** Cap jumlah transaksi yang dikembalikan ke LLM (context window protection). */
    private val maxRows = 50
    suspend fun execute(toolCall: ToolCall): ToolResult {
        return try {
            val args = parseArgs(toolCall.argumentsJson)
            val content = when (toolCall.name) {
                "query_transactions" -> executeQueryTransactions(args)
                "get_expenses" -> executeGetExpenses(args)
                "get_income" -> executeGetIncome(args)
                "get_balance" -> executeGetBalance(args)
                else -> JSONObject().apply {
                    put("error", "unknown_tool")
                    put("tool", toolCall.name)
                }.toString()
            }
            val rowCount = try {
                val obj = JSONObject(content)
                if (obj.has("rowCount")) obj.getInt("rowCount")
                else if (obj.has("transactionCount")) obj.getInt("transactionCount")
                else 0
            } catch (_: Exception) {
                0
            }
            ChatLogger.d("HartaKu/Chat", "ToolExecutor tool=${toolCall.name} argsLen=${toolCall.argumentsJson.length} rowCount=$rowCount")
            ToolResult(callId = toolCall.id, content = content)
        } catch (e: Exception) {
            ChatLogger.d("HartaKu/Chat", "ToolExecutor tool=${toolCall.name} argsLen=${toolCall.argumentsJson.length} rowCount=0")
            ToolResult(
                callId = toolCall.id,
                content = JSONObject().apply {
                    put("error", "execution_failed")
                    put("message", e.message ?: e.javaClass.simpleName)
                }.toString()
            )
        }
    }

    private fun parseArgs(argumentsJson: String): Map<String, Any?> {
        if (argumentsJson.isBlank()) return emptyMap()
        return try {
            val obj = JSONObject(argumentsJson)
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = obj.opt(k)
                    put(k, if (v == JSONObject.NULL) null else v)
                }
            }
        } catch (_: Exception) {
            // Fallback: try wrapping with braces (LLM kadang output raw "key": "value")
            try {
                val wrapped = if (argumentsJson.trim().startsWith("{")) argumentsJson
                else "{$argumentsJson}"
                val obj = JSONObject(wrapped)
                buildMap {
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val v = obj.opt(k)
                        put(k, if (v == JSONObject.NULL) null else v)
                    }
                }
            } catch (_: Exception) {
                emptyMap()
            }
        }
    }

    private suspend fun executeQueryTransactions(args: Map<String, Any?>): String {
        val keyword = args["keyword"] as? String
        val category = args["category"] as? String
        val dateArgs = extractDateArgs(args)
        val now = System.currentTimeMillis()
        val (startMillis, endMillis) = resolveDateRangeMillis(dateArgs, now)

        val list: List<Transaction> = when {
            !keyword.isNullOrBlank() -> transactionRepository.getTransactionsByKeyword(keyword).first()
            !category.isNullOrBlank() -> transactionRepository.getTransactionsByCategoryName(category).first()
            else -> transactionRepository.getTransactionsInRange(startMillis, endMillis).first()
        }

        // Filter tambahan: kalau date range eksplisit, apply juga supaya
        // keyword/category search tidak bocor ke luar range.
        var filtered = list.filter { tx ->
            tx.timestamp in startMillis..endMillis
        }

        // Semantic fallback: kalau keyword search kosong, coba HNSW search
        // pada ObjectBox. Hanya jalan kalau embedding client tersedia dan
        // ada data di index (count > 0). Failure diam: fallback ke hasil
        // keyword kosong, biar tool result contract tidak throw.
        if (filtered.isEmpty() && !keyword.isNullOrBlank() && embeddingClient != null) {
            val semanticIds = try {
                val queryEmb = embeddingClient.embed(keyword, isQuery = true).getOrNull()
                if (queryEmb != null) {
                    TransactionVectorBox.searchByVector(queryEmb, limit = 10)
                        .map { it.transactionId }
                        .toSet()
                } else emptySet()
            } catch (e: Exception) {
                emptySet()
            }
            if (semanticIds.isNotEmpty()) {
                val semanticHits = transactionRepository.getAllTransactions().first()
                    .filter { it.id in semanticIds && it.timestamp in startMillis..endMillis }
                filtered = semanticHits
            }
        }

        val capped = if (filtered.size > maxRows) filtered.take(maxRows) else filtered
        val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
        val txArr = JSONArray()
        for (tx in capped) {
            txArr.put(JSONObject().apply {
                put("id", tx.id)
                put("timestamp", dateFmt.format(Instant.ofEpochMilli(tx.timestamp)))
                put("amount", tx.amount)
                put("type", tx.type.name)
                put("description", tx.description)
                put("category", tx.category)
            })
        }
        val totalAmount = capped.filter { it.type.name == "EXPENSE" }.sumOf { it.amount }
        return JSONObject().apply {
            put("tool", "query_transactions")
            put("rowCount", capped.size)
            put("truncated", filtered.size > maxRows)
            put("totalExpense", totalAmount)
            put("transactions", txArr)
        }.toString()
    }

    private suspend fun executeGetExpenses(args: Map<String, Any?>): String {
        val category = args["category"] as? String
        val dateArgs = extractDateArgs(args)
        val now = System.currentTimeMillis()
        val (startMillis, endMillis) = resolveDateRangeMillis(dateArgs, now)

        val breakdown = transactionRepository.getCategoryBreakdownById(startMillis, endMillis).first()
        val filteredBreakdown = if (!category.isNullOrBlank()) {
            breakdown.filter { it.name.equals(category, ignoreCase = true) }
        } else {
            breakdown
        }
        val totalExpense = filteredBreakdown.sumOf { it.total }

        val breakdownArr = JSONArray()
        for (cat in filteredBreakdown) {
            breakdownArr.put(JSONObject().apply {
                put("category", cat.name)
                put("total", cat.total)
            })
        }
        return JSONObject().apply {
            put("tool", "get_expenses")
            put("dateRange", dateArgs.dateRange)
            put("categoryFilter", category ?: JSONObject.NULL)
            put("totalExpense", totalExpense)
            put("breakdown", breakdownArr)
        }.toString()
    }

    private suspend fun executeGetIncome(args: Map<String, Any?>): String {
        val category = args["category"] as? String
        val dateArgs = extractDateArgs(args)
        val now = System.currentTimeMillis()
        val (startMillis, endMillis) = resolveDateRangeMillis(dateArgs, now)

        val incomeBreakdown = transactionRepository.getIncomeBreakdownById(startMillis, endMillis).first()
        val filteredBreakdown = if (!category.isNullOrBlank()) {
            incomeBreakdown.filter { it.name.equals(category, ignoreCase = true) }
        } else {
            incomeBreakdown
        }
        val totalIncome = filteredBreakdown.sumOf { it.total }

        val breakdownArr = JSONArray()
        for (cat in filteredBreakdown) {
            breakdownArr.put(JSONObject().apply {
                put("category", cat.name)
                put("total", cat.total)
            })
        }
        return JSONObject().apply {
            put("tool", "get_income")
            put("dateRange", dateArgs.dateRange)
            put("categoryFilter", category ?: JSONObject.NULL)
            put("totalIncome", totalIncome)
            put("incomeBreakdown", breakdownArr)
        }.toString()
    }

    private suspend fun executeGetBalance(args: Map<String, Any?>): String {
        val dateArgs = extractDateArgs(args)
        val now = System.currentTimeMillis()
        val (startMillis, endMillis) = resolveDateRangeMillis(dateArgs, now)

        val totalExpense = transactionRepository.getExpenseInRange(startMillis, endMillis).first()
        val totalIncome = transactionRepository.getIncomeInRange(startMillis, endMillis).first()
        val balance = totalIncome - totalExpense

        return JSONObject().apply {
            put("tool", "get_balance")
            put("dateRange", dateArgs.dateRange)
            put("totalExpense", totalExpense)
            put("totalIncome", totalIncome)
            put("balance", balance)
        }.toString()
    }


    private fun extractDateArgs(args: Map<String, Any?>): DateArgs {
        val range = (args["dateRange"] as? String) ?: "all"
        val days = (args["days"] as? Number)?.toInt()?.takeIf { it > 0 }
        val weeks = (args["weeks"] as? Number)?.toInt()?.takeIf { it > 0 }
        val months = (args["months"] as? Number)?.toInt()?.takeIf { it > 0 }
        val startDate = (args["startDate"] as? String)?.takeIf { it.isNotBlank() }
        val endDate = (args["endDate"] as? String)?.takeIf { it.isNotBlank() }
        return DateArgs(range, days, weeks, months, startDate, endDate)
    }

    private data class DateArgs(
        val dateRange: String,
        val days: Int?,
        val weeks: Int?,
        val months: Int?,
        val startDate: String? = null,
        val endDate: String? = null
    )

    /**
     * Resolve date args ke (start, end) timestamp ms.
     * Prioritas: startDate/endDate absolut > numerik (days/weeks/months) > bucket relatif.
     * "all" → (0, Long.MAX_VALUE).
     */
    private fun resolveDateRangeMillis(
        dateArgs: DateArgs,
        now: Long
    ): Pair<Long, Long> {
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        val end = calendar.timeInMillis

        // Tanggal absolut (ISO yyyy-MM-dd) — menang atas semua yang relatif.
        val startDate = dateArgs.startDate
        val endDate = dateArgs.endDate
        if (startDate != null || endDate != null) {
            val zone = ZoneId.systemDefault()
            val formatter = DateTimeFormatter.ISO_LOCAL_DATE
            val startMillis = startDate?.let { s ->
                val d = java.time.LocalDate.parse(s, formatter)
                d.atStartOfDay(zone).toInstant().toEpochMilli()
            } ?: 0L
            val endMillis = endDate?.let { e ->
                val d = java.time.LocalDate.parse(e, formatter)
                d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
            } ?: startDate?.let { s ->
                // Jika cuma startDate, scope = hari itu saja (end of that day).
                val d = java.time.LocalDate.parse(s, formatter)
                d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
            } ?: now
            return Pair(startMillis, endMillis)
        }

        dateArgs.days?.let { n ->
            calendar.add(Calendar.DAY_OF_YEAR, -n)
            return Pair(calendar.timeInMillis, end)
        }
        dateArgs.weeks?.let { n ->
            calendar.add(Calendar.WEEK_OF_YEAR, -n)
            return Pair(calendar.timeInMillis, end)
        }
        dateArgs.months?.let { n ->
            calendar.add(Calendar.MONTH, -n)
            return Pair(calendar.timeInMillis, end)
        }

        when (dateArgs.dateRange) {
            "today" -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                // ponytail: end = end of day (23:59:59.999) agar semua transaksi hari ini terhitung
                val start = calendar.timeInMillis
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                return Pair(start, calendar.timeInMillis)
            }
            "yesterday" -> {
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                return Pair(start, calendar.timeInMillis)
            }
            "week" -> {
                calendar.add(Calendar.DAY_OF_YEAR, -7)
            }
            "month" -> {
                calendar.add(Calendar.MONTH, -1)
            }
            else -> {
                return Pair(0L, Long.MAX_VALUE)
            }
        }
        return Pair(calendar.timeInMillis, end)
    }
}
