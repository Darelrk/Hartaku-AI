package com.example.data

/**
 * Result dari scan receipt yang akan di-pass ke ManualInputScreen.
 */
data class ReceiptScanResult(
    val store: String,
    val total: Double,
    val items: List<ReceiptItem>,
    val date: String?,
    /** Teks display untuk di-pre-fill ke input field */
    val autoFillText: String,
    /** Summary untuk ditampilkan sebagai banner */
    val summaryText: String
)

data class ReceiptItem(
    val name: String,
    val price: Double,
    val quantity: Int = 1
)