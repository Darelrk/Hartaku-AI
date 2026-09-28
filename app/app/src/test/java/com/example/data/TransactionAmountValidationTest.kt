package com.example.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Nominal transaksi harus lebih besar dari nol dan berupa angka berhingga.
 *
 * `ManualInputViewModel.extractAmount` mengembalikan 0.0 kalau teks tidak memuat
 * angka, jadi tanpa guard di repository, mengetik "makan siang" tanpa nominal
 * akan tersimpan sebagai transaksi Rp 0 — lolos tanpa error, `isSaved = true`,
 * lalu tercampur ke setiap total, breakdown kategori, dan konteks AI.
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

    /** Pesan penolakan, atau null bila insert diam-diam berhasil. */
    private suspend fun rejectionMessage(amount: Double): String? =
        try {
            repo().insertTransaction(tx(amount))
            null
        } catch (e: IllegalArgumentException) {
            e.message
        }

    @Test
    fun zeroAmountIsRejected() = runBlocking {
        val message = rejectionMessage(0.0)
        assertNotNull("transaksi Rp 0 harus ditolak", message)
        assertTrue(
            "pesan harus menjelaskan nominal, dapat: $message",
            message!!.contains("lebih besar dari nol")
        )
    }

    @Test
    fun negativeAmountIsRejected() = runBlocking {
        val message = rejectionMessage(-50_000.0)
        assertNotNull("nominal negatif harus ditolak", message)
        assertTrue(message!!.contains("lebih besar dari nol"))
    }

    @Test
    fun nonFiniteAmountIsRejected() = runBlocking {
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            val message = rejectionMessage(bad)
            assertNotNull("nominal $bad harus ditolak", message)
            assertTrue(message!!.contains("lebih besar dari nol"))
        }
    }

    @Test
    fun positiveAmountPassesValidation() = runBlocking {
        // Tidak melempar = guard tidak menyaring nominal sah.
        repo().insertTransaction(tx(85_000.0))
    }
}
