package com.example.data

import kotlinx.coroutines.flow.first

/**
 * Transaction seeder for the first launch.
 *
 * Seeds two layers:
 *  1. Mock spread (-2..+1 day offsets) for the HomeFeed pager to show
 *     realistic content around "today" without scrolling.
 *  2. Synthetic 3 months of deterministic data (seeded) so the AI chatbot
 *     and projection slides have meaningful history.
 *
 * Idempotent: only seeds when the transactions table is empty.
 *
 * Called by [com.example.data.AppContainer] AFTER [CategorySeeder.seedIfEmpty]
 * to guarantee the foreign-key-like lookups (categoryId) resolve. The Transaction
 * entity itself has no FK constraint (categoryId is nullable + indexed only),
 * but seeding after categories avoids any insert-time ordering surprises.
 */
object TransactionSeeder {

    suspend fun seedIfEmpty(repository: TransactionRepository) {
        val existing = repository.getAllTransactions().first()
        if (existing.isNotEmpty()) {
            // DB already has data — skip.
            return
        }
        insertMockSpread(repository)
        // Synthetic 180-day seeder dihapus — data demo mengotori budget calculation.
        // Budget dihitung dari income manual user, jadi seed data yang menambahkan
        // Gaji Bulanan 5jt + Freelance/THR ratusan rb akan membuat currentMonthIncome
        // tidak match dengan income riil user. Mock 4-day spread tetap ada untuk Home demo.
    }

    private suspend fun insertMockSpread(repository: TransactionRepository) {
        val mocks = listOf(
            // offset -2
            mock(-2, 8, 0, 10000.0, "Angkot", "Transport", TransactionType.EXPENSE),
            mock(-2, 12, 30, 35000.0, "Nasi Padang", "Makanan", TransactionType.EXPENSE),
            mock(-2, 17, 0, 120000.0, "Belanja Bulanan", "Belanja", TransactionType.EXPENSE),
            // offset -1
            mock(-1, 7, 30, 30000.0, "Bensin", "Transport", TransactionType.EXPENSE),
            mock(-1, 10, 0, 50000.0, "Pulsa", "Tagihan", TransactionType.EXPENSE),
            mock(-1, 13, 0, 25000.0, "Bakso", "Makanan", TransactionType.EXPENSE),
            mock(-1, 20, 0, 50000.0, "Nonton Bioskop", "Hiburan", TransactionType.EXPENSE),
            // offset 0 (today)
            mock(0, 8, 0, 500000.0, "Gaji Harian", "Gaji", TransactionType.INCOME),
            mock(0, 9, 30, 25000.0, "Kopi Kenangan", "Makanan", TransactionType.EXPENSE),
            mock(0, 12, 30, 60000.0, "Makan Siang", "Makanan", TransactionType.EXPENSE),
            mock(0, 18, 0, 45000.0, "Gojek", "Transport", TransactionType.EXPENSE),
            // offset +1
            mock(1, 9, 0, 100000.0, "Listrik", "Tagihan", TransactionType.EXPENSE)
        )
        mocks.forEach { repository.insertTransaction(it) }
    }

    private fun mock(
        offset: Int,
        hour: Int,
        minute: Int,
        amount: Double,
        desc: String,
        category: String,
        type: TransactionType
    ): Transaction {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, offset)
        cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
        cal.set(java.util.Calendar.MINUTE, minute)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return Transaction(
            amount = amount,
            description = desc,
            category = category,
            categoryId = categoryIdFor(category),
            type = type,
            timestamp = cal.timeInMillis
        )
    }

    private fun categoryIdFor(category: String): String? = when (category.lowercase()) {
        "makanan"    -> CategorySeeder.ID_MAKANAN
        "transport"  -> CategorySeeder.ID_TRANSPORT
        "belanja"    -> CategorySeeder.ID_BELANJA
        "hiburan"    -> CategorySeeder.ID_HIBURAN
        "tagihan"    -> CategorySeeder.ID_TAGIHAN
        "investasi"  -> CategorySeeder.ID_INVESTASI
        "kesehatan"  -> CategorySeeder.ID_KESEHATAN
        "pendidikan" -> CategorySeeder.ID_PENDIDIKAN
        "perawatan"  -> CategorySeeder.ID_PERAWATAN
        "olahraga"   -> CategorySeeder.ID_OLAHRAGA
        "donasi"     -> CategorySeeder.ID_DONASI
        "asuransi"   -> CategorySeeder.ID_ASURANSI
        "perbaikan"  -> CategorySeeder.ID_PERBAIKAN
        "peliharaan" -> CategorySeeder.ID_PELIHARAAN
        "lainnya"    -> CategorySeeder.ID_LAINNYA
        "gaji"       -> CategorySeeder.ID_GAJI
        "penjualan"  -> CategorySeeder.ID_PENJUALAN
        "hadiah"     -> CategorySeeder.ID_HADIAH
        else         -> null
    }
}
