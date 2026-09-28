package com.example.ai

/**
 * Mode input user yang menentukan jenis entitas yang akan di-emit LLM saat parsing.
 *
 * - [EXPENSE]: LLM boleh emit transactions (expense) DAN bills (jika ada recurrence cue).
 *              Kombinasi, tidak eksklusif — LLM tetap auto-detect bill cues.
 * - [INCOME]:  LLM emit transactions (income) saja, no bills.
 * - [BILL]:    LLM emit bills saja, no transactions.
 *
 * Properti [displayName] dan [descriptionLabel] dipakai oleh UI untuk
 * placeholder text dan label toggle.
 */
enum class InputMode(val displayName: String, val descriptionLabel: String) {
    EXPENSE(
        displayName = "Pengeluaran",
        descriptionLabel = "cth: makan bakso 15rb"
    ),
    INCOME(
        displayName = "Pemasukan",
        descriptionLabel = "cth: gaji 5 juta"
    ),
    BILL(
        displayName = "Tagihan",
        descriptionLabel = "cth: listrik 200rb setiap tanggal 5"
    )
}
