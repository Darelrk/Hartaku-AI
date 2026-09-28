package com.example.eval

/**
 * Satu kasus eval: query pengguna → tool yang dipilih LLM → ground truth.
 *
 * Yang diuji offline adalah ORKESTRASI kita (ToolCallExecutor, resolusi
 * rentang tanggal, format, AnswerValidator) — bukan kualitas model. Fake
 * berperan sebagai keputusan routing LLM; sisanya kode produksi sungguhan.
 * runner online memakai korpus yang sama untuk memisahkan dua lapis itu.
 */
data class EvalCase(
    val id: String,
    val query: String,
    val toolName: String,
    val toolArgsJson: String,
    /** Harus muncul di JSON hasil tool — bukti argumen LLM dieksekusi benar. */
    val toolResultMustContain: String,
    /** Angka dalam format RupiahFormatter yang harus muncul di jawaban akhir. */
    val expectedAnswerNumbers: List<String>,
    val expectNoData: Boolean = false
)

/**
 * 30 kasus. Seed transaksi yang dipakai semua kasus ada di
 * `EvalHarnessTest.seedTransactions`: 85.000 "Makanan" hari-0,
 * 45.000 "Gojek ke kantor" hari-0, 20.000 "Kopi" hari-1,
 * 1.500.000 "Gaji" INCOME hari-0, 12.000 "Belanja" hari-2.
 */
object EvalCorpus {

    private val today = """{"dateRange":"today"}"""
    private val yesterday = """{"dateRange":"yesterday"}"""
    private val all = """{"dateRange":"all"}"""
    private val days7 = """{"dateRange":"last_n_days","days":7}"""
    private val days30 = """{"dateRange":"last_n_days","days":30}"""
    private val weeks1 = """{"dateRange":"last_n_weeks","weeks":1}"""
    private val weeks2 = """{"dateRange":"last_n_weeks","weeks":2}"""
    private val months1 = """{"dateRange":"last_n_months","months":1}"""
    private val months3 = """{"dateRange":"last_n_months","months":3}"""
    private val future = """{"startDate":"2099-01-01","endDate":"2099-01-31"}"""

    val cases: List<EvalCase> = listOf(
        // --- get_expenses -------------------------------------------------
        EvalCase("expenses_today", "berapa pengeluaran hari ini?", "get_expenses", today, "130000", listOf("130.000")),
        EvalCase("expenses_yesterday", "berapa pengeluaran kemarin?", "get_expenses", yesterday, "20000", listOf("20.000")),
        EvalCase("expenses_7days", "berapa pengeluaran 7 hari terakhir?", "get_expenses", days7, "\"dateRange\":\"last_n_days\"", emptyList()),
        EvalCase("expenses_2weeks", "total belanja 2 minggu terakhir", "get_expenses", weeks2, "\"dateRange\":\"last_n_weeks\"", emptyList()),
        EvalCase("expenses_all", "total pengeluaran dari awal", "get_expenses", all, "162000", listOf("162.000")),
        EvalCase("expenses_no_data", "berapa pengeluaran bulan depan?", "get_expenses", future, "\"totalExpense\":0", listOf("0"), expectNoData = true),
        EvalCase("expenses_caps", "Berapa Pengeluaran Hari Ini?", "get_expenses", today, "130000", listOf("130.000")),
        EvalCase("expenses_double_space", "berapa  pengeluaran   hari ini", "get_expenses", today, "130000", listOf("130.000")),
        EvalCase("expenses_greeting", "halo, berapa pengeluaran hari ini?", "get_expenses", today, "130000", listOf("130.000")),
        EvalCase("expenses_1day", "pengeluaran 1 hari terakhir", "get_expenses", """{"dateRange":"last_n_days","days":1}""", "\"dateRange\":\"last_n_days\"", emptyList()),
        EvalCase("expenses_30days", "total pengeluaran 30 hari terakhir", "get_expenses", days30, "\"dateRange\":\"last_n_days\"", emptyList()),
        EvalCase("expenses_1week", "total belanja minggu ini", "get_expenses", weeks1, "\"dateRange\":\"last_n_weeks\"", emptyList()),

        // --- get_income ---------------------------------------------------
        EvalCase("income_today", "berapa pemasukan hari ini?", "get_income", today, "1500000", listOf("1.500.000")),
        EvalCase("income_all", "total pemasukan semua", "get_income", all, "1500000", listOf("1.500.000")),
        EvalCase("income_3months", "pemasukan 3 bulan terakhir", "get_income", months3, "\"dateRange\":\"last_n_months\"", emptyList()),
        EvalCase("income_1month", "pemasukan bulan ini", "get_income", months1, "\"dateRange\":\"last_n_months\"", emptyList()),
        EvalCase("income_all_caps", "Total Pemasukan Semua", "get_income", all, "1500000", listOf("1.500.000")),
        EvalCase("income_zero_yesterday", "berapa pemasukan kemarin?", "get_income", yesterday, "\"totalIncome\":0", listOf("0"), expectNoData = true),

        // --- get_balance --------------------------------------------------
        EvalCase("balance_today", "berapa saldo saya?", "get_balance", today, "1370000", listOf("1.370.000")),
        EvalCase("balance_all", "saldo keseluruhan", "get_balance", all, "1338000", listOf("1.338.000")),
        EvalCase("balance_negative", "berapa saldo kemarin?", "get_balance", yesterday, "-20000", listOf("20.000")),
        EvalCase("balance_7days", "saldo 7 hari terakhir", "get_balance", days7, "\"dateRange\":\"last_n_days\"", emptyList()),
        EvalCase("balance_1week", "saldo minggu ini", "get_balance", weeks1, "\"dateRange\":\"last_n_weeks\"", emptyList()),
        EvalCase("balance_typo", "berapa saldo akhir?", "get_balance", today, "1370000", listOf("1.370.000")),

        // --- query_transactions -------------------------------------------
        EvalCase("tx_keyword_gojek", "cari gojek", "query_transactions", """{"keyword":"Gojek","dateRange":"all"}""", "45000", listOf("45.000")),
        EvalCase("tx_keyword_kopi_range", "tampilkan kopi 30 hari terakhir", "query_transactions", """{"keyword":"Kopi","dateRange":"last_n_days","days":30}""", "20000", listOf("20.000")),
        EvalCase("tx_keyword_makanan", "cari makanan", "query_transactions", """{"keyword":"Makanan","dateRange":"all"}""", "85000", listOf("85.000")),
        EvalCase("tx_today_all", "tampilkan transaksi hari ini", "query_transactions", today, "130000", listOf("130.000")),
        EvalCase("tx_30days_no_keyword", "tampilkan transaksi 30 hari terakhir", "query_transactions", days30, "\"tool\":\"query_transactions\"", emptyList()),
        EvalCase("tx_no_result", "cari transaksi yacht", "query_transactions", """{"keyword":"yacht","dateRange":"all"}""", "\"rowCount\":0", emptyList(), expectNoData = true)
    )
}
