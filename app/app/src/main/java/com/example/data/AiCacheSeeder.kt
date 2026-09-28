package com.example.data

/**
 * AI insight cache seeder for the first launch.
 *
 * Pre-populates 5 common insight queries so the daily insight slide
 * renders meaningful content immediately, even before the user has
 * talked to the chatbot. The cache is consulted first by
 * [com.example.ai.AiInsightGenerator] (or equivalent) before any
 * NIM API call, so a fresh install gets instant, offline-friendly
 * insight text.
 *
 * Idempotency: [AiCacheDao.insertCache] uses `OnConflictStrategy.REPLACE`,
 * so seeding twice is safe (entries are overwritten with the same content).
 * The seeder still calls [AiCacheDao.getCache] first to skip the no-op
 * insert path.
 */
object AiCacheSeeder {

    suspend fun seedIfEmpty(dao: AiCacheDao) {
        for (entry in ENTRIES) {
            val existing = dao.getCache(entry.queryHash)
            if (existing == null) {
                dao.insertCache(entry)
            }
        }
    }

    private val ENTRIES: List<AiCache> = listOf(
        AiCache(
            queryHash = "insight:today_summary",
            rawInput = "summary_today",
            responseJson = """{"insight":"Hari ini kamu sudah spending Rp 130.500, mostly makan siang. Masih dalam budget harian.","saran":"Coba masak sendiri besok biar hemat lagi."}"""
        ),
        AiCache(
            queryHash = "insight:week_trend",
            rawInput = "weekly_trend",
            responseJson = """{"insight":"Minggu ini pengeluaran turun 12% dari minggu lalu. Hemat di kategori Transport!","saran":"Pertahankan, jangan naikin Gojek weekend ini."}"""
        ),
        AiCache(
            queryHash = "insight:budget_warning",
            rawInput = "budget_warning_makanan",
            responseJson = """{"insight":"Budget Makan & Minum sudah 87% terpakai dengan 10 hari tersisa.","saran":"Kurangi jajan kopi 1x sehari, bisa hemat ~Rp 200k."}"""
        ),
        AiCache(
            queryHash = "insight:income_pattern",
            rawInput = "income_diversification",
            responseJson = """{"insight":"Income bulan ini: Gaji 5jt + Freelance 1.2jt + Hadiah 200k. Diversifikasi bagus!","saran":"Pertimbangkan investasi reksadana dari surplus."}"""
        ),
        AiCache(
            queryHash = "insight:bill_reminder",
            rawInput = "bill_unpaid",
            responseJson = """{"insight":"Netflix (Rp 186.000) dan Kredit Motor (Rp 850.000) belum dibayar.","saran":"Bayar sebelum tanggal 15 biar gak kena denda."}"""
        )
    )
}
