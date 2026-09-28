package com.example.data

/**
 * Sidik jari isi tabel transaksi. Dipakai sebagai bagian kunci cache AI
 * supaya jawaban yang diturunkan dari data pengguna tidak pernah basi.
 * Tanpa migrasi skema: tiga agregat SQL yang sudah ada di SQLite.
 */
data class DataFingerprint(
    val txnCount: Int,
    val totalAmount: Double,
    val latestTimestamp: Long
) {
    fun asKey(): String = "$txnCount|$totalAmount|$latestTimestamp"
}
