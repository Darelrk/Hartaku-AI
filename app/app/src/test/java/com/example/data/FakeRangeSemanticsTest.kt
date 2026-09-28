package com.example.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Fake range harus meniru SQL DAO persis: `timestamp >= :start AND timestamp < :end`
 * (half-open). Kalau fake memakai `..`, transaksi tepat pada batas akhir ikut
 * terhitung, dan totals pengeluaran dan income untuk rentang yang sama saling
 * berbeda — persis yang membuat angka korpus eval tidak bisa dipercaya.
 *
 * Test ini menahan fake agar tidak menyimpang dari SQL lagi.
 */
@RunWith(RobolectricTestRunner::class)
class FakeRangeSemanticsTest {

    private val dayMs = 24L * 60 * 60 * 1000

    private fun repo() = FakeTransactionRepository()

    private fun tx(amount: Double, type: TransactionType, timestamp: Long) = Transaction(
        amount = amount,
        description = "d",
        category = "c",
        type = type,
        timestamp = timestamp
    )

    @Test
    fun startBoundaryIncludedEndBoundaryExcluded() = runBlocking {
        val repo = repo()
        val t = 1_700_000_000_000L

        // Tepat pada `start` — harus masuk. Tepat pada `end` — harus TIDAK masuk.
        repo.insertTransaction(tx(100.0, TransactionType.EXPENSE, t))
        repo.insertTransaction(tx(70.0, TransactionType.EXPENSE, t + dayMs))
        // Di tengah, wajib masuk.
        repo.insertTransaction(tx(30.0, TransactionType.EXPENSE, t + dayMs / 2))
        // Di luar sebelum start, wajib tidak masuk.
        repo.insertTransaction(tx(900.0, TransactionType.EXPENSE, t - 1))

        val expense = repo.getExpenseInRange(t, t + dayMs).first()
        assertEquals("hanya 100 + 30 = 130; 70 ada tepat di batas end dan 900 di luar start", 130.0, expense, 0.001)
    }

    @Test
    fun expenseAndIncomeAgreeOnSameRange() = runBlocking {
        val repo = repo()
        val t = 1_700_000_000_000L

        repo.insertTransaction(tx(100.0, TransactionType.EXPENSE, t))
        repo.insertTransaction(tx(40.0, TransactionType.INCOME, t))
        repo.insertTransaction(tx(70.0, TransactionType.EXPENSE, t + dayMs))
        repo.insertTransaction(tx(60.0, TransactionType.INCOME, t + dayMs))

        val expense = repo.getExpenseInRange(t, t + dayMs).first()
        val income = repo.getIncomeInRange(t, t + dayMs).first()
        val rows = repo.getTransactionsInRange(t, t + dayMs).first()

        assertEquals(100.0, expense, 0.001)
        assertEquals(40.0, income, 0.001)
        // Baris pada t+dayMs boleh terhitung pada tiga pemanggil sekaligus
        // atau tidak sama sekali — yang dilarang adalah tidak konsisten.
        assertEquals(
            "getExpenseInRange/getIncomeInRange/getTransactionsInRange harus sepakat soal batas",
            expense + income,
            rows.filter { it.timestamp in t until t + dayMs }.sumOf { it.amount },
            0.001
        )
    }

    @Test
    fun dailyAndRangeAggregatesAgreeWithTransactionList() = runBlocking {
        val repo = repo()
        val t = 1_700_000_000_000L

        repo.insertTransaction(tx(100.0, TransactionType.EXPENSE, t))
        repo.insertTransaction(tx(30.0, TransactionType.EXPENSE, t + dayMs / 2))
        repo.insertTransaction(tx(25.0, TransactionType.INCOME, t + dayMs / 3))
        repo.insertTransaction(tx(70.0, TransactionType.EXPENSE, t + dayMs))

        val rows = repo.getTransactionsByDay(t, t + dayMs).first()
        val inRange = rows.filter { it.timestamp in t until t + dayMs }

        // Tiga baris berada di dalam [t, t+dayMs): 100 di t, 25 di t+dayMs/3,
        // 30 di t+dayMs/2. Baris 70 di t+dayMs tepat di batas end dan terbuang.
        assertEquals(3, inRange.size)
        assertEquals(130.0, repo.getDailyExpense(t, t + dayMs).first(), 0.001)
        assertEquals(25.0, repo.getDailyIncome(t, t + dayMs).first(), 0.001)
        assertEquals(3, repo.getTransactionCount(t, t + dayMs).first())
        assertEquals(
            "total expense breakdown harus sama dengan agregat harian",
            130.0,
            repo.getCategoryBreakdownById(t, t + dayMs).first().sumOf { it.total },
            0.001
        )
    }
}
