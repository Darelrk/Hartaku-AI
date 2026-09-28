package com.example.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AnswerValidatorTest {

    // ---------- extractNumbersFromText ----------

    @Test
    fun `extracts plain integer`() {
        val numbers = AnswerValidator.extractNumbersFromText("Total: 1045800")
        assertTrue(1045800L in numbers)
    }

    @Test
    fun `extracts Indo-style thousand separator`() {
        val numbers = AnswerValidator.extractNumbersFromText("Total Rp 1.045.800")
        assertTrue(1045800L in numbers)
    }

    @Test
    fun `extracts US-style thousand separator`() {
        val numbers = AnswerValidator.extractNumbersFromText("Total 1,045,800")
        assertTrue(1045800L in numbers)
    }

    @Test
    fun `extracts rb suffix (Indonesian thousand)`() {
        val numbers = AnswerValidator.extractNumbersFromText("Pengeluaran 120rb")
        assertTrue(120000L in numbers)
    }

    @Test
    fun `extracts jt suffix (Indonesian million)`() {
        val numbers = AnswerValidator.extractNumbersFromText("Total 1.2jt")
        assertTrue(1200000L in numbers)
    }

    @Test
    fun `extracts K and M English suffixes`() {
        val numbers = AnswerValidator.extractNumbersFromText("Sekitar 1.5K atau 2M")
        assertTrue(1500L in numbers)
        assertTrue(2000000L in numbers)
    }

    @Test
    fun `does not extract small numbers below 3 digits`() {
        val numbers = AnswerValidator.extractNumbersFromText("ada 3 transaksi, kode 12")
        // 3 (single digit) dan 12 (2 digit) tidak masuk karena regex \d{3,}
        assertTrue(numbers.isEmpty())
    }

    // ---------- extractNumbersFromToolResult ----------

    @Test
    fun `extracts amounts from query_transactions result`() {
        val json = """
            {"tool":"query_transactions","rowCount":2,"totalExpense":40000,
             "transactions":[
                {"id":1,"amount":25000,"description":"Kopi","category":"Makanan"},
                {"id":2,"amount":15000,"description":"Teh","category":"Makanan"}
             ]}
        """.trimIndent()
        val numbers = AnswerValidator.extractNumbersFromToolResult(json)
        assertTrue(40000L in numbers)
        assertTrue(25000L in numbers)
        assertTrue(15000L in numbers)
    }

    @Test
    fun `extracts totals from get_summary result`() {
        val json = """
            {"tool":"get_summary","totalExpense":150000,
             "breakdown":[
                {"category":"Makanan","total":100000},
                {"category":"Transport","total":50000}
             ]}
        """.trimIndent()
        val numbers = AnswerValidator.extractNumbersFromToolResult(json)
        assertTrue(150000L in numbers)
        assertTrue(100000L in numbers)
        assertTrue(50000L in numbers)
    }

    @Test
    fun `error json is skipped`() {
        val json = """{"error":"execution_failed","message":"oops"}"""
        val numbers = AnswerValidator.extractNumbersFromToolResult(json)
        assertTrue(numbers.isEmpty())
    }

    @Test
    fun `invalid json returns empty set`() {
        val numbers = AnswerValidator.extractNumbersFromToolResult("not a json at all")
        assertTrue(numbers.isEmpty())
    }

    // ---------- validate() ----------

    @Test
    fun `valid answer with matching numbers passes through`() {
        val toolJson = """{"tool":"get_expenses","totalExpense":1045800,"breakdown":[]}"""
        val answer = "Total pengeluaran Anda adalah Rp 1.045.800."
        val validated = AnswerValidator.validate(answer, "", toolJson)
        assertEquals(answer, validated)
    }

    @Test
    fun `hallucinated angka 120000 vs context 1045800 triggers regenerate`() {
        val toolJson = """{"tool":"get_expenses","totalExpense":1045800,"breakdown":[]}"""
        val answer = "Total pengeluaran Anda 120rb rupiah."
        val validated = AnswerValidator.validate(answer, "", toolJson)
        // 120000 tidak ada di ground truth (1045800), harus regenerate
        assertTrue(
            "Should regenerate, got: $validated",
            validated.contains("1.045.800") || validated.contains("1045800")
        )
        assertTrue("Should not contain hallucinated 120.000", !validated.contains("120.000"))
    }

    @Test
    fun `false negative when context has data but answer says none`() {
        val toolJson = """
            {"tool":"query_transactions","rowCount":2,"totalExpense":40000,
             "transactions":[
                {"id":1,"amount":25000,"description":"Kopi","category":"Makanan"},
                {"id":2,"amount":15000,"description":"Teh","category":"Makanan"}
             ]}
        """.trimIndent()
        val answer = "Maaf, tidak ada transaksi yang cocok."
        val validated = AnswerValidator.validate(answer, "", toolJson)
        // Harus regenerate, jawaban polos dari template berisi "Ditemukan"
        assertTrue(
            "Should regenerate, got: $validated",
            validated.contains("Ditemukan") || validated.contains("25.000")
        )
    }

    @Test
    fun `chat_only with large number and empty context is replaced`() {
        val answer = "Pengeluaran kamu 5.000.000 bulan ini."
        val validated = AnswerValidator.validate(answer, "", null)
        assertTrue(
            "Should warn no data, got: $validated",
            validated.contains("tidak menemukan") || validated.contains("tidak")
        )
    }

    @Test
    fun `chat_only with no numbers passes through unchanged`() {
        val answer = "Halo! Ada yang bisa saya bantu dengan catatan keuangan Anda?"
        val validated = AnswerValidator.validate(answer, "", null)
        assertEquals(answer, validated)
    }

    // ---------- formatRupiah ----------

    @Test
    fun `formatRupiah produces dot-separated thousands`() {
        assertEquals("Rp 1.045.800", AnswerValidator.formatRupiah(1045800L))
        assertEquals("Rp 1.000", AnswerValidator.formatRupiah(1000L))
        assertEquals("Rp 999", AnswerValidator.formatRupiah(999L))
        assertEquals("Rp 0", AnswerValidator.formatRupiah(0L))
    }

    // ---------- regenerateFromContext ----------

    @Test
    fun `regenerate from query_transactions lists rows`() {
        val toolJson = """
            {"tool":"query_transactions","rowCount":2,"totalExpense":40000,"truncated":false,
             "transactions":[
                {"id":1,"amount":25000,"description":"Kopi","category":"Makanan","type":"EXPENSE"},
                {"id":2,"amount":15000,"description":"Teh","category":"Makanan","type":"EXPENSE"}
             ]}
        """.trimIndent()
        val out = AnswerValidator.regenerateFromContext(toolJson, "")
        assertTrue(out.contains("Ditemukan 2 transaksi"))
        assertTrue(out.contains("25.000"))
        assertTrue(out.contains("40.000"))
    }

    @Test
    fun `regenerate from empty query_transactions returns polite empty message`() {
        val toolJson = """{"tool":"query_transactions","rowCount":0,"totalExpense":0,"transactions":[]}"""
        val out = AnswerValidator.regenerateFromContext(toolJson, "")
        assertTrue(out.contains("Tidak ditemukan"))
    }
}
