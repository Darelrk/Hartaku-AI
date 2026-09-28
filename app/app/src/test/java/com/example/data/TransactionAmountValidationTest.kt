package com.example.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Nominal transaksi harus lebih besar dari nol dan berupa angka berhingga —
 * pada insert maupun update.
 *
 * Insert: `ManualInputViewModel.extractAmount` mengembalikan 0.0 kalau teks tidak
 * memuat angka, jadi "makan siang" tanpa nominal akan tersimpan sebagai transaksi
 * Rp 0 — lolos tanpa error, `isSaved = true`, lalu tercampur ke setiap total,
 * breakdown kategori, dan konteks AI.
 *
 * Update: `EditTransactionSheet` mengurai nominal dengan `filter { it.isDigit() }`,
 * jadi mengetik "0" menghasilkan 0.0.
 */
@RunWith(RobolectricTestRunner::class)
class TransactionAmountValidationTest {

    private fun tx(amount: Double) = Transaction(
        amount = amount,
        description = "test",
        category = "Makanan",
        type = TransactionType.EXPENSE,
        timestamp = 1_700_000_000_000L
    )

    /**
     * Repository ASLI dengan DAO stub, bukan `FakeTransactionRepository`.
     * Fake meng-override `insertTransaction` untuk menyimpan ke daftar memori,
     * sehingga ia melewati guard di kelas dasar persis seperti di produksi.
     */
    private fun repo() = TransactionRepository(StubTransactionDao())

    private val invalidAmounts =
        listOf(0.0, -50_000.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)

    /** Pesan penolakan, atau null bila operasi diam-diam berhasil. */
    private suspend fun insertRejection(amount: Double): String? =
        try {
            repo().insertTransaction(tx(amount))
            null
        } catch (e: IllegalArgumentException) {
            e.message
        }

    private suspend fun updateRejection(amount: Double): String? =
        try {
            repo().updateTransaction(tx(amount))
            null
        } catch (e: IllegalArgumentException) {
            e.message
        }

    @Test
    fun insertRejectsZeroWithReadableMessage() = runBlocking {
        val message = insertRejection(0.0)
        assertNotNull("transaksi Rp 0 harus ditolak", message)
        assertTrue(
            "pesan harus menjelaskan nominal, dapat: $message",
            message!!.contains("lebih besar dari nol")
        )
    }

    @Test
    fun insertRejectsInvalidAmounts() = runBlocking {
        for (bad in invalidAmounts) {
            assertNotNull("insert dengan nominal $bad harus ditolak", insertRejection(bad))
        }
    }

    @Test
    fun updateRejectsInvalidAmounts() = runBlocking {
        for (bad in invalidAmounts) {
            val message = updateRejection(bad)
            assertNotNull("update dengan nominal $bad harus ditolak", message)
            assertTrue(message!!.contains("lebih besar dari nol"))
        }
    }

    @Test
    fun positiveAmountPassesBothGuards() = runBlocking {
        // Tidak melempar = guard tidak menyaring nominal sah.
        repo().insertTransaction(tx(85_000.0))
        repo().updateTransaction(tx(120_000.0))
    }
}
