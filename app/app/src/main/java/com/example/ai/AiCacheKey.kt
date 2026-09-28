package com.example.ai

import java.security.MessageDigest

/**
 * Kunci cache untuk seluruh jalur AI.
 *
 * `forChat` dan `forInsight` memuat fingerprint data pengguna supaya jawaban
 * basi tidak mungkin dilayani setelah ada transaksi masuk/ubah/hapus.
 * `forParserClause` sengaja TIDAK memuat fingerprint: output parser adalah
 * fungsi murni dari teks dan tidak pernah bergantung pada data pengguna —
 * kalau ikut di-fingerprint, setiap transaksi baru menghapus seluruh cache
 * parser dan nilai cache yang paling tinggi hilang.
 */
object AiCacheKey {

    fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun forChat(fingerprint: String, query: String): String =
        sha256("chat|$fingerprint|$query")

    fun forInsight(fingerprint: String, data: AiInsightGenerator.InsightData): String =
        sha256(
            "insight|$fingerprint|${data.totalExpense}|${data.yesterdayExpense}|" +
                "${data.totalIncome}|${data.transactionCount}|${data.topCategory}|${data.topCategoryAmount}"
        )

    fun forParserClause(clause: String): String =
        sha256("parse|$clause")
}
