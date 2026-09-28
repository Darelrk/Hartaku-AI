package com.example

import java.util.Locale

/** Shared Indonesian Rupiah formatting for AI and UI layers. */
object RupiahFormatter {
    fun format(amount: Double): String {
        val formatted = String.format(Locale.US, "%,.0f", amount).replace(",", ".")
        return "Rp $formatted"
    }

    fun format(amount: Long): String = format(amount.toDouble())
}
