package com.example.ai

/**
 * OpenAI-compatible JSON Schema definitions untuk 4 tools yang dipakai
 * ChatbotRAGManager: `query_transactions`, `get_expenses`, `get_income`, `get_balance`.
 *
 * Schema ditulis manual sebagai Kotlin literal (Map/List) agar tidak
 * butuh library JSON tambahan dan tetap valid untuk NIM cloud endpoint
 * (OpenAI-compatible). Field name pakai snake_case sesuai konvensi OpenAI.
 *
 * Enum date range disusun dari yang paling spesifik ke paling luas:
 *   today, yesterday, last_n_days, last_n_weeks, last_n_months,
 *   week, month, all
 */
object ChatToolDefinition {

    private const val DATE_RANGE_ENUM =
        "\"today\",\"yesterday\",\"last_n_days\",\"last_n_weeks\"," +
            "\"last_n_months\",\"week\",\"month\",\"all\""

    val dateRangeSchema: Map<String, Any?> = mapOf(
        "type" to "string",
        "enum" to listOf(
            "today", "yesterday", "last_n_days", "last_n_weeks",
            "last_n_months", "week", "month", "all"
        ),
        "description" to "Rentang waktu relatif. Gunakan 'last_n_*' + days/weeks/months untuk N hari/minggu/bulan terakhir."
    )

    /** Tanggal spesifik (ISO yyyy-MM-dd). Bila dipakai bersama endDate, menang atas dateRange relatif. */
    val startDateSchema: Map<String, Any?> = mapOf(
        "type" to "string",
        "description" to "Tanggal mulai absolut (yyyy-MM-dd, mis. '2026-07-01'). Untuk SATU hari spesifik, isi startDate DAN endDate dengan tanggal yang sama. Menang atas dateRange bila diisi."
    )

    /** Tanggal akhir spesifik (ISO yyyy-MM-dd), inklusif. */
    val endDateSchema: Map<String, Any?> = mapOf(
        "type" to "string",
        "description" to "Tanggal akhir absolut (yyyy-MM-dd, inklusif). Wajib sama dengan startDate bila ingin SATU hari spesifik — mis. startDate='2026-07-15', endDate='2026-07-15'. Boleh kosong: default end-of-day startDate."
    )

    val queryTransactionsSchema: Map<String, Any?> = mapOf(
        "type" to "function",
        "function" to mapOf(
            "name" to "query_transactions",
            "description" to "List transaksi spesifik yang cocok dengan keyword, category, dan date range. " +
                "Gunakan saat user ingin melihat/mencari transaksi tertentu (mis. 'tampilkan kopi 5 hari terakhir', 'ada transaksi gojek?').",
            "parameters" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "keyword" to mapOf(
                        "type" to "string",
                        "description" to "Kata kunci pencarian di deskripsi (mis. 'kopi', 'gojek')."
                    ),
                    "category" to mapOf(
                        "type" to "string",
                        "description" to "Nama kategori (mis. 'Makanan', 'Transport')."
                    ),
                    "dateRange" to dateRangeSchema,
                    "days" to mapOf(
                        "type" to "integer",
                        "description" to "Jumlah hari untuk dateRange=last_n_days."
                    ),
                    "weeks" to mapOf(
                        "type" to "integer",
                        "description" to "Jumlah minggu untuk dateRange=last_n_weeks."
                    ),
                    "months" to mapOf(
                                            "type" to "integer",
                                            "description" to "Jumlah bulan untuk dateRange=last_n_months."
                                        ),
                                        "startDate" to startDateSchema,
                                        "endDate" to endDateSchema
                )
            )
        )
    )

    val getExpensesSchema: Map<String, Any?> = mapOf(
        "type" to "function",
        "function" to mapOf(
            "name" to "get_expenses",
            "description" to "Total pengeluaran + breakdown per kategori. Panggil untuk pertanyaan berapa/total/pengeluaran/belanja.",
            "parameters" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "category" to mapOf(
                        "type" to "string",
                        "description" to "Filter kategori (opsional). Mis. 'Makanan'."
                    ),
                    "dateRange" to dateRangeSchema,
                    "days" to mapOf(
                        "type" to "integer",
                        "description" to "Jumlah hari untuk dateRange=last_n_days."
                    ),
                    "weeks" to mapOf(
                        "type" to "integer",
                        "description" to "Jumlah minggu untuk dateRange=last_n_weeks."
                    ),
                    "months" to mapOf(
                                            "type" to "integer",
                                            "description" to "Jumlah bulan untuk dateRange=last_n_months."
                                        ),
                                        "startDate" to startDateSchema,
                                        "endDate" to endDateSchema
                )
            )
        )
    )

    val getIncomeSchema: Map<String, Any?> = mapOf(
        "type" to "function",
        "function" to mapOf(
            "name" to "get_income",
            "description" to "Total pemasukan + breakdown per kategori. Panggil untuk pertanyaan berapa/total/pemasukan/income/gaji.",
            "parameters" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "category" to mapOf(
                        "type" to "string",
                        "description" to "Filter kategori (opsional). Mis. 'Gaji'."
                    ),
                    "dateRange" to dateRangeSchema,
                    "days" to mapOf(
                        "type" to "integer",
                        "description" to "Jumlah hari untuk dateRange=last_n_days."
                    ),
                    "weeks" to mapOf(
                        "type" to "integer",
                        "description" to "Jumlah minggu untuk dateRange=last_n_weeks."
                    ),
                    "months" to mapOf(
                                            "type" to "integer",
                                            "description" to "Jumlah bulan untuk dateRange=last_n_months."
                                        ),
                                        "startDate" to startDateSchema,
                                        "endDate" to endDateSchema
                )
            )
        )
    )

    val getBalanceSchema: Map<String, Any?> = mapOf(
        "type" to "function",
        "function" to mapOf(
            "name" to "get_balance",
            "description" to "Saldo bersih (pemasukan - pengeluaran). Juga return total pemasukan + pengeluaran. Panggil untuk saldo/sisa/balance/neraca.",
            "parameters" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "dateRange" to dateRangeSchema,
                    "days" to mapOf(
                        "type" to "integer",
                        "description" to "Jumlah hari untuk dateRange=last_n_days."
                    ),
                    "weeks" to mapOf(
                        "type" to "integer",
                        "description" to "Jumlah minggu untuk dateRange=last_n_weeks."
                    ),
                    "months" to mapOf(
                                            "type" to "integer",
                                            "description" to "Jumlah bulan untuk dateRange=last_n_months."
                                        ),
                                        "startDate" to startDateSchema,
                                        "endDate" to endDateSchema
                )
            )
        )
    )


    /** Daftar tool lengkap yang dikirim ke LLM. */
    val allTools: List<Map<String, Any?>> = listOf(
        queryTransactionsSchema,
        getExpensesSchema,
        getIncomeSchema,
        getBalanceSchema
    )

    val knownToolNames: Set<String> = setOf("query_transactions", "get_expenses", "get_income", "get_balance")
}
