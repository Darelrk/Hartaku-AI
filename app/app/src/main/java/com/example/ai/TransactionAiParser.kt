package com.example.ai

import android.util.Log
import com.example.data.Category
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * AI-powered transaction parser with regex fallback.
 * Categories di-inject dari DB — dynamic, bukan hardcoded.
 *
 * Flow:
 * 1. Coba parse via NIM LLM ([NimConfig.LLM_MODEL]) dengan daftar kategori dari DB
 * 2. Kalau AI gagal/timeout → fallback ke alias matching di DB
 * 3. Kalau AI sukses → pakai hasil AI
 */
class TransactionAiParser(
    private val nimClient: ChatClient,
    private val aiCacheDao: com.example.data.AiCacheDao? = null
) {

    private val TAG = "TransactionAiParser"
    data class ParsedTransaction(
        val type: String,           // "expense" | "income"
        val category: String,
        val amount: Double,
        val description: String,
        val confidence: Double = 0.8
    )

    data class ParsedBill(
        val name: String,
        val amount: Double,
        val dueDate: Int,                // 1-31
        val category: String,
        val notifyBeforeDays: Int = 1,
        val recurrenceMode: String = "ONCE",  // "ONCE" | "FOREVER" | "CUSTOM_RANGE"
        val rangeEndMonthMillis: Long? = null
    )

    data class ParseResult(
        val transactions: List<ParsedTransaction>,
        val bills: List<ParsedBill> = emptyList(),
        val source: String          // "ai" | "regex" | "error"
    )

    fun preprocessNominals(text: String): String {
        var result = text
        val numberPattern = """(\d+(?:[.,]\d+)?)"""

        // Billion: M / miliar / milyar
        val billionRegex = Regex(numberPattern + """\s*(?:miliar|milyar|M)\b""", RegexOption.IGNORE_CASE)
        result = billionRegex.replace(result) { matchResult ->
            val numStr = matchResult.groupValues[1].replace(",", ".")
            val num = numStr.toDoubleOrNull()
            if (num != null) {
                (num * 1_000_000_000).toLong().toString()
            } else {
                matchResult.value
            }
        }

        // Million: jt / juta / jut
        val millionRegex = Regex(numberPattern + """\s*(?:jt|juta|jut)\b""", RegexOption.IGNORE_CASE)
        result = millionRegex.replace(result) { matchResult ->
            val numStr = matchResult.groupValues[1].replace(",", ".")
            val num = numStr.toDoubleOrNull()
            if (num != null) {
                (num * 1_000_000).toLong().toString()
            } else {
                matchResult.value
            }
        }

        // Thousand: rb / rib / ribu / rebu / k
        val thousandRegex = Regex(numberPattern + """\s*(?:rb|rib|ribu|rebu|k)\b""", RegexOption.IGNORE_CASE)
        result = thousandRegex.replace(result) { matchResult ->
            val numStr = matchResult.groupValues[1].replace(",", ".")
            val num = numStr.toDoubleOrNull()
            if (num != null) {
                (num * 1_000).toLong().toString()
            } else {
                matchResult.value
            }
        }

        return result
    }

    /**
     * Parse teks transaksi dengan daftar kategori dari DB.
     * @param text Input user
     * @param categories Daftar kategori aktif dari DB
     */
    suspend fun parse(
        text: String,
        categories: List<Category> = emptyList(),
        mode: InputMode = InputMode.EXPENSE
    ): ParseResult {
        val preprocessedText = preprocessNominals(text)
        // Step 1: Pre-process
        val clauses = clauseSplitPreProcess(preprocessedText)
        if (clauses.isEmpty()) {
            return ParseResult(emptyList(), emptyList(), "ai")
        }

        val allTransactions = mutableListOf<ParsedTransaction>()
        var aiFailed = false

        // TTL is 30 days
        val ttlMillis = 30L * 24 * 60 * 60 * 1000

        // We run LLM per clause
        for (clause in clauses) {
            val hash = AiCacheKey.forParserClause(clause)
            
            // Check cache first
            var cachedJson: String? = null
            try {
                if (aiCacheDao != null) {
                    val cached = aiCacheDao.getCache(hash)
                    if (cached != null && (System.currentTimeMillis() - cached.createdAt) <= ttlMillis) {
                        cachedJson = cached.responseJson
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to query cache for '$clause': ${e.message}")
            }

            if (cachedJson != null) {
                try {
                    val parsed = postProcess(cachedJson, categories)
                    allTransactions.addAll(parsed)
                    continue
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse cached JSON: ${e.message}")
                }
            }

            val prompt = buildPrompt(categories, mode)
            try {
                val response = nimClient.chat(
                    systemPrompt = prompt,
                    userMessage = clause,
                    maxTokens = 256
                )
                if (response.isSuccess) {
                    val content = response.getOrThrow().content.trim()
                    // Write to cache if not empty
                    if (content != "[]" && content != "{}") {
                        val parsed = postProcess(content, categories)
                        allTransactions.addAll(parsed)
                        try {
                            if (aiCacheDao != null) {
                                aiCacheDao.insertCache(
                                    com.example.data.AiCache(
                                        queryHash = hash,
                                        rawInput = clause,
                                        responseJson = content
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to insert into cache for '$clause': ${e.message}")
                        }
                    }
                } else {
                    aiFailed = true
                    break
                }
            } catch (e: Exception) {
                Log.w(TAG, "AI parse failed for clause '$clause': ${e.message}")
                aiFailed = true
                break
            }
        }

        // If AI succeeded on all clauses
        if (!aiFailed) {
            return ParseResult(allTransactions, emptyList(), "ai")
        }

        // Step 2: Fallback ke regex + alias matching
        val regexResult = parseWithRegex(text, categories)
        return ParseResult(regexResult, emptyList(), "regex")
    }

    fun clauseSplitPreProcess(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        val delimiterRegex = Regex(
            """(?:\s*(?:dan|terus|lalu|juga|plus|&|kemudian|serta|isinya|berisi|ada)\s+|,\s*)""",
            RegexOption.IGNORE_CASE
        )

        return trimmed.split(delimiterRegex)
            .map { cleanFiller(it) }
            .filter { it.isNotBlank() }
    }

    fun cleanFiller(text: String): String {
        var cleaned = text
        val fillers = listOf("hari ini", "tadi", "saya", "di")
        for (filler in fillers) {
            cleaned = cleaned.replace(Regex("""\b$filler\b""", RegexOption.IGNORE_CASE), "")
        }
        return cleaned.replace(Regex("""\s+"""), " ").trim { it <= ' ' || it == ',' || it == '.' || it == '-' }
    }

    /**
     * Build system prompt dengan daftar kategori dari DB.
     * Instruksi yang sangat cerdas untuk memisahkan obrolan santai, feedback, atau keluhan
     * dari transaksi keuangan yang sebenarnya.
     */
    private fun buildPrompt(categories: List<Category>, mode: InputMode = InputMode.EXPENSE): String {
        val categoryListStr = categories.joinToString("\n") { cat ->
            val parsedAliases = try {
                JSONArray(cat.aliases).let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }.joinToString(", ")
                }
            } catch (_: Exception) {
                ""
            }
            val aliasSuffix = if (parsedAliases.isNotEmpty()) " (alias: $parsedAliases)" else ""
            "- ${cat.name} (tipe: ${cat.typeClass.lowercase()})$aliasSuffix"
        }

        return """
Kamu adalah asisten pencatatan keuangan pribadi bahasa Indonesia. Tugasmu adalah mengekstrak transaksi keuangan (pemasukan atau pengeluaran) dari teks suara informal ke format JSON array.

### DAFTAR KATEGORI YANG VALID:
$categoryListStr

### SCHEMA KONTRAK OUTPUT JSON:
Kembalikan HANYA JSON array dengan format berikut:
[
  {
    "type": "expense" | "income",
    "category": "<Kategori dari daftar di atas>",
    "amount": <angka nominal murni, double atau integer>,
    "description": "<deskripsi bersih>",
    "confidence": <confidence score antara 0.0 sampai 1.0>
  }
]

### ATURAN UTAMA:
1. Keluhan / Basa-basi: Jika teks hanya berupa keluhan sistem, obrolan kosong, basa-basi, atau tes mic tanpa ada transaksi keuangan nyata (misal: "tombol mic mengganggu", "kok lambat ya", "tes mic 1 2 3", "aplikasi error"), kembalikan array kosong: []
2. Narrative Filter: Bersihkan deskripsi dari kata-kata naratif/filler seperti "hari ini", "tadi", "saya", "di", "beli", "bayar", "investasi", "topup" agar menjadi deskripsi yang bersih.
3. Slang Nominal: Teks nominal uang telah dikonversi ke nominal angka penuh (misal: "500000"). Pastikan mengambil angka nominal tersebut apa adanya.
4. Multi-clause: Ekstrak setiap transaksi sebagai item terpisah dalam array JSON.
5. Parent-Child: Jika teks mengandung struktur induk-anak dengan kata "isinya", "berisi", atau "ada", anggap sebagai transaksi terpisah.
6. Missing Field / Bukan Transaksi: Jika nominal/amount adalah 0 atau tidak terdeteksi, atau bukan transaksi, abaikan atau skip.
7. Category Match: Pilih kategori yang paling cocok dari DAFTAR KATEGORI YANG VALID di atas berdasarkan deskripsi dan aliasnya.
8. Investment Rule: Jika ada pembelian/topup/investasi instrumen finansial (saham, crypto, reksa dana, bibit), gunakan tipe "expense" (pengeluaran), BUKAN "income" (pemasukan), kecuali jika transaksinya adalah menjual saham/crypto.
9. Confidence Score: Tentukan tingkat keyakinan ekstraksi dari 0.0 (bukan transaksi/ambigu sekali) sampai 1.0 (sangat yakin). Gunakan default 0.8 jika ragu.
10. Format Constraint: Kembalikan HANYA valid JSON array. JANGAN sertakan markdown fences seperti ```json ... ``` atau penjelasan apa pun.
11. Spesifik Klasifikasi Laundry & Tanaman: Transaksi terkait laundry/cuci pakaian, setrika, atau pembelian tanaman hias, bunga, bibit tanaman harus dimasukkan ke kategori "Belanja", BUKAN ke "Transport" atau "Investasi".
12. Tipe Transaksi Ikuti Mode Input yang sedang aktif. Jika mode INCOME, seluruh hasil WAJIB bertipe "income". Jika mode EXPENSE, WAJIB bertipe "expense" kecuali teks menyebut gaji, bonus, gajian, atau pendapatan. Jika mode BILL, keluarkan hanya tagihan berulang.

### CONTOH FEW-SHOT:
1. Input: "makan bakso 15rb"
   Output: [{"type": "expense", "category": "Makanan", "amount": 15000, "description": "Bakso", "confidence": 1.0}]

2. Input: "kopi 15rb terus ojek 10rb"
   Output: [
     {"type": "expense", "category": "Makanan", "amount": 15000, "description": "Kopi", "confidence": 1.0},
     {"type": "expense", "category": "Transport", "amount": 10000, "description": "Ojek", "confidence": 1.0}
   ]

3. Input: "belanja di indomaret 50rb, isinya susu 30rb"
   Output: [
     {"type": "expense", "category": "Lainnya", "amount": 20000, "description": "Belanja Indomaret", "confidence": 0.9},
     {"type": "expense", "category": "Makanan", "amount": 30000, "description": "Susu", "confidence": 1.0}
   ]

4. Input: "gaji bulan ini 5 juta"
   Output: [{"type": "income", "category": "Gaji", "amount": 5000000, "description": "Gaji bulan ini", "confidence": 1.0}]

5. Input: "terima bonus 2jt"
   Output: [{"type": "income", "category": "Gaji", "amount": 2000000, "description": "Bonus", "confidence": 1.0}]

Input User:
        """.trimIndent()
    }

    fun sanitizeJson(raw: String): String {
        var text = raw.trim()
        if (text.startsWith("```")) {
            val lines = text.split("\n")
            val filteredLines = lines.filterIndexed { index, line ->
                !(index == 0 && line.trim().startsWith("```")) &&
                !(index == lines.lastIndex && line.trim() == "```")
            }
            text = filteredLines.joinToString("\n").trim()
        }
        val jsonPattern = Regex("""(\{.*\}|\[.*\])""", RegexOption.DOT_MATCHES_ALL)
        val match = jsonPattern.find(text)
        if (match != null) {
            text = match.value.trim()
        }
        text = text.replace("\\\\\"", "\\\"")
        return text
    }

    fun postProcess(rawJson: String, categories: List<Category>): List<ParsedTransaction> {
        val sanitized = sanitizeJson(rawJson)
        if (sanitized.isEmpty() || sanitized == "[]" || sanitized == "{}") {
            return emptyList()
        }
        
        val results = mutableListOf<ParsedTransaction>()
        if (sanitized.startsWith("[")) {
            val arr = JSONArray(sanitized)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val parsed = parseSingleObject(obj, categories)
                if (parsed != null) {
                    results.add(parsed)
                }
            }
        } else if (sanitized.startsWith("{")) {
            val obj = JSONObject(sanitized)
            val parsed = parseSingleObject(obj, categories)
            if (parsed != null) {
                results.add(parsed)
            }
        } else {
            throw Exception("Sanitized JSON doesn't start with [ or {")
        }
        return results
    }

    private fun parseSingleObject(obj: JSONObject, categories: List<Category>): ParsedTransaction? {
        val type = obj.optString("type", "expense").lowercase().trim()
        if (type != "expense" && type != "income") {
            return null
        }
        val amount = obj.optDouble("amount", 0.0)
        if (amount <= 0.0) {
            return null
        }
        val rawCategory = obj.optString("category", "").trim()
        val description = obj.optString("description", "").trim()
        if (description.isEmpty()) {
            return null
        }
        val confidence = if (obj.has("confidence")) obj.optDouble("confidence", 0.8) else 0.8
        if (confidence < 0.3) {
            return null
        }
        val matchedCategory = if (rawCategory.isNotBlank()) {
            categories.firstOrNull { it.name.equals(rawCategory, ignoreCase = true) }?.name ?: rawCategory.lowercase().replaceFirstChar { it.uppercase() }
        } else {
            "Lainnya"
        }
        return ParsedTransaction(
            type = type,
            category = matchedCategory,
            amount = amount,
            description = description,
            confidence = confidence
        )
    }

    /**
     * Regex fallback dengan alias matching dari DB.
     * Pattern best practice dari SpendTrack/Indonesian parser:
     * - Multi-action splitting (koma, dan, terus, lalu, juga, plus)
     * - Amount bisa di mana saja (prefix/suffix nominal)
     * - Multiplier rb/ribu/k = 1000, jt/juta = 1.000.000, M/miliar = 1e9
     * - Format Indonesia: "25.000" = 25000 (titik = ribuan, koma = desimal)
     * - Slang: "goceng/ceban"=500rb, "gopek"=500, "seperempat"=250, "setengah"=0.5x
     */
    private fun parseWithRegex(text: String, categories: List<Category>): List<ParsedTransaction> {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return emptyList()

        // Multi-action splitting: pisah transaksi berdasarkan delimiter natural
        val parts = trimmed.split(SPLIT_DELIMITER_REGEX)
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return parts.mapNotNull { part ->
            // Slang multiplier check (goceng=500rb, gopek=500, dll)
            val slangAmount = detectSlangAmount(part)
            val amount = slangAmount ?: extractAmount(part)
            if (amount <= 0.0) return@mapNotNull null // Skip kalimat keluhan

            val desc = extractDescription(part)
            if (desc.isBlank()) return@mapNotNull null

            val matched = resolveCategory(desc, categories)
            ParsedTransaction(
                type = if (matched?.typeClass == "INCOME") "income" else "expense",
                category = matched?.name ?: "Lainnya",
                amount = amount,
                description = desc
            )
        }
    }

    /**
     * Cocokkan deskripsi bebas dengan kategori DB lewat nama dan alias.
     *
     * Token terpanjang menang supaya alias yang lebih spesifik ("gajian")
     * mengalahkan alias yang lebih umum ("gaji"). Mengembalikan null bila
     * tidak ada token yang cocok.
     */
    private fun resolveCategory(desc: String, categories: List<Category>): Category? {
        val lower = desc.lowercase()
        var best: Category? = null
        var bestLen = 0
        for (cat in categories) {
            val aliases = try {
                JSONArray(cat.aliases).let { arr ->
                    (0 until arr.length()).map { arr.getString(it).lowercase() }
                }
            } catch (_: Exception) {
                emptyList()
            }
            for (token in listOf(cat.name.lowercase()) + aliases) {
                if (token.isNotBlank() && lower.contains(token) && token.length > bestLen) {
                    best = cat
                    bestLen = token.length
                }
            }
        }
        return best
    }

    private fun detectSlangAmount(text: String): Double? {
        val lower = text.lowercase()
        // Goceng/goceng=500, gopek=500, ceban=500, gocap=500, gope=500
        return when {
            lower.contains(Regex("""\b(goceng|ceban|gocap|gope|sepuluh\s*rebu|10\s*rb)\b""")) -> 10_000.0
            lower.contains(Regex("""\b(gopek|limang\s*rebu|5\s*rb)\b""")) -> 5_000.0
            lower.contains(Regex("""\b(cepe|duaribu|2\s*rb)\b""")) -> 2_000.0
            lower.contains(Regex("""\b(seribu|1\s*rb|cetok)\b""")) -> 1_000.0
            else -> null
        }
    }



    private fun extractAmount(text: String): Double {
        val lower = text.lowercase()

        // Multiplier patterns (CLDR Indonesia standard):
        // rb/rib/ribu/k/kosan = 1.000, jt/juta/jut = 1.000.000, M/miliar/milyar = 1e9
        val multiplier = when {
            lower.contains(Regex("""\d\s*(m|miliar|milyar)\b""")) -> 1_000_000_000.0
            lower.contains(Regex("""\d\s*(jt|juta|jut)\b""")) -> 1_000_000.0
            lower.contains(Regex("""\d\s*(rb|rib|ribu|k|rebu)\b""")) -> 1_000.0
            else -> 1.0
        }

        // Format Indonesia: "1.500.000" = 1500000 (titik = ribuan)
        // Pattern: greedy match SEMUA digit atau format ribuan, dimulai dari digit pertama
        val pattern = Regex("""(\d{1,3}(?:\.\d{3})+(?:,\d+)?|\d+(?:,\d+)?)""")
        val match = pattern.find(text) ?: return 0.0
        val raw = match.value
        val cleaned = if (raw.contains(",")) {
            // Format desimal: 12,5 atau 1.500,75
            raw.replace(".", "").replace(",", ".")
        } else {
            // Format ribuan Indonesia: 1.500.000 → 1500000
            raw.replace(".", "")
        }
        val rawNumber = cleaned.toDoubleOrNull() ?: 0.0

        return rawNumber * multiplier
    }

    private fun extractDescription(text: String): String {
        return text
            .replace(Regex("""\d{1,3}(?:\.\d{3})+(?:,\d+)?|\d+(?:,\d+)?"""), "")
            .replace(Regex("""\b(jt|juta|jut|rb|rib|ribu|rebu|k|miliar|milyar|M)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\b(rp|rupiah)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trimStart { it == ',' || it == '.' || it == '-' }
    }

    private fun parseIndonesianMonth(monthName: String): Int? {
        val normalized = monthName.trim().lowercase()
        return when (normalized) {
            "januari", "jan" -> Calendar.JANUARY
            "februari", "feb" -> Calendar.FEBRUARY
            "maret", "mar" -> Calendar.MARCH
            "april", "apr" -> Calendar.APRIL
            "mei" -> Calendar.MAY
            "juni", "jun" -> Calendar.JUNE
            "juli", "jul" -> Calendar.JULY
            "agustus", "agu", "ags" -> Calendar.AUGUST
            "september", "sep" -> Calendar.SEPTEMBER
            "oktober", "okt" -> Calendar.OCTOBER
            "november", "nov" -> Calendar.NOVEMBER
            "desember", "des" -> Calendar.DECEMBER
            else -> null
        }
    }

    private fun parseSingleBillObject(billObj: JSONObject): ParsedBill {
        val recurrence = billObj.optString("recurrence", "ONCE").uppercase()
        val validRecurrence = when (recurrence) {
            "ONCE", "FOREVER", "CUSTOM_RANGE" -> recurrence
            else -> "ONCE"
        }
        val rangeEndMonthStr = billObj.optString("rangeEndMonth", "").trim()
        val rangeEndYearStr = billObj.optString("rangeEndYear", "").trim()

        val rangeEndMonthMillis = if (rangeEndMonthStr.isNotBlank()) {
            parseIndonesianMonth(rangeEndMonthStr)?.let { month ->
                val year = rangeEndYearStr.toIntOrNull() ?: (Calendar.getInstance().get(Calendar.YEAR) + 1)
                Calendar.getInstance().apply {
                    set(year, month, 1, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
        } else {
            null
        }

        return ParsedBill(
            name = billObj.optString("name", "Tagihan"),
            amount = billObj.optDouble("amount", 0.0),
            dueDate = billObj.optInt("dueDate", 1).coerceIn(1, 31),
            category = billObj.optString("category", "Lainnya"),
            notifyBeforeDays = billObj.optInt("notifyBeforeDays", 1).coerceIn(0, 30),
            recurrenceMode = validRecurrence,
            rangeEndMonthMillis = rangeEndMonthMillis
        )
    }

    private fun postProcessInternal(result: ParseResult): ParseResult {
        return result.copy(
            transactions = result.transactions.map { tx ->
                tx.copy(type = tx.type.trim().lowercase().let {
                    if (it == "income" || it == "pemasukan" || it == "gaji") "income" else "expense"
                })
            }
        )
    }

    companion object {
        private val SPLIT_DELIMITER_REGEX = Regex(""",\s*|\s+dan\s+|\s+terus\s+|\s+lalu\s+|\s+juga\s+|\s+plus\s+|\s+&\s+""")
    }
}