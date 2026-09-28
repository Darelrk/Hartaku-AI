package com.example.ui.home

import com.example.data.Budget
import com.example.data.Transaction
import com.example.data.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * `Budget.spent` adalah field TERSIMPAN dan praktis selalu 0. Nilainya hanya
 * pernah dihitung ulang oleh `withSpent`, yang dipakai layar Budget Management
 * dan Profile.
 *
 * Home (`HomeViewModel.loadDay`) dan `DailyCheckWorker` sebelumnya membaca field
 * mentah itu, sehingga KPI budget-vs-aktual di chart dan alert harian
 * menampilkan nol atau angka lama. Test ini mengunci perilaku `withSpent` yang
 * sekarang dipakai kedua jalur tersebut.
 *
 * Robolectric wajib: `Budget.parseCategoryIds` memakai `org.json.JSONArray` yang
 * ter-stub di test JVM biasa, sehingga exception-nya tertelan dan fallback
 * kategori selalu menghasilkan nol.
 */
@RunWith(RobolectricTestRunner::class)
class WithSpentTest {

    private fun budget(
        id: Int,
        categoryIds: String,
        percent: Double = 0.0,
        amount: Double = 1_000_000.0,
        period: String = "monthly"
    ) = Budget(
        id = id,
        name = "Budget $id",
        amount = amount,
        spent = 0.0,
        percent = percent,
        period = period,
        categoryIds = categoryIds
    )

    private fun expense(
        amount: Double,
        categoryId: String?,
        timestamp: Long = 1_700_000_000_000L,
        budgetId: Int? = null
    ) = Transaction(
        amount = amount,
        description = "d",
        category = categoryId ?: "Lainnya",
        categoryId = categoryId,
        budgetId = budgetId,
        type = TransactionType.EXPENSE,
        timestamp = timestamp
    )

    @Test
    fun computesSpentFromCategoryFallback() {
        val txs = listOf(
            expense(85_000.0, "c1"),
            expense(45_000.0, "c2"),
            expense(10_000.0, "c3")
        )
        val result = listOf(budget(1, "[\"c1\",\"c2\"]")).withSpent(txs, 0.0)
        assertEquals(130_000.0, result.first().spent, 0.001)
    }

    @Test
    fun explicitBudgetIdDoesNotDiscardCategoryMatchedExpenses() {
        val txs = listOf(
            expense(70_000.0, "c1", budgetId = 1),
            // Tanpa budgetId, jadi jatuh ke pemilik kategori c1 — yang juga
            // budget 1. Semuanya HARUS terhitung: sebelumnya `?: fallback`
            // membuat 20.000 ini hilang begitu budget punya satu transaksi
            // ber-budgetId, sehingga spent lebih kecil dari pengeluaran nyata.
            expense(20_000.0, "c1")
        )
        val result = listOf(budget(1, "[\"c1\"]")).withSpent(txs, 0.0)
        assertEquals(90_000.0, result.first().spent, 0.001)
        assertEquals(
            "tidak boleh ada transaksi yang hilang dari budget mana pun",
            90_000.0,
            result.sumOf { it.spent },
            0.001
        )
    }

    @Test
    fun incomeIsNeverCountedAsSpent() {
        val income = Transaction(
            amount = 9_000_000.0,
            description = "gaji",
            category = "Gaji",
            categoryId = "c1",
            type = TransactionType.INCOME,
            timestamp = 1_700_000_000_000L
        )
        val result = listOf(budget(1, "[\"c1\"]")).withSpent(listOf(income), 0.0)
        assertEquals(0.0, result.first().spent, 0.001)
    }

    @Test
    fun percentBudgetScalesWithMonthlyIncome() {
        val txs = listOf(expense(300_000.0, "c1"))
        val result = listOf(budget(1, "[\"c1\"]", percent = 30.0)).withSpent(txs, 1_000_000.0)
        // 30% dari 1.000.000
        assertEquals(300_000.0, result.first().amount, 0.001)
        assertEquals(300_000.0, result.first().spent, 0.001)
    }

    @Test
    fun budgetWithoutMatchingCategoryStaysAtZero() {
        val result = listOf(budget(1, "[\"lain\"]")).withSpent(listOf(expense(85_000.0, "c1")), 0.0)
        assertEquals(0.0, result.first().spent, 0.001)
    }

    @Test
    fun emptyTransactionListProducesZeroSpentNotNegative() {
        val result = listOf(budget(1, "[\"c1\"]")).withSpent(emptyList(), 0.0)
        assertTrue("spent tidak boleh negatif", result.first().spent >= 0.0)
        assertEquals(0.0, result.first().spent, 0.001)
    }

    private fun at(daysAgo: Double, from: Long) = from - (daysAgo * 24 * 60 * 60 * 1000).toLong()

    @Test
    fun weeklyBudgetOnlyCountsLastSevenDays() {
        val now = 1_700_000_000_000L
        val txs = listOf(
            expense(90_000.0, "c1", at(1.0, now)),
            expense(60_000.0, "c1", at(6.0, now)),
            // Di luar jendela 7 hari — tidak boleh ikut untuk budget mingguan.
            expense(300_000.0, "c1", at(20.0, now))
        )
        val weekly = listOf(budget(1, "[\"c1\"]", period = "weekly")).withSpent(txs, 0.0, now)
        assertEquals(150_000.0, weekly.first().spent, 0.001)

        // Budget bulanan atas data yang sama tetap melihat seluruh bulan.
        val monthly = listOf(budget(2, "[\"c1\"]", period = "monthly"))
            .withSpent(txs, 0.0, now)
        assertEquals(450_000.0, monthly.first().spent, 0.001)
    }

    @Test
    fun weeklyPercentTargetUsesWindowIncomeNotMonthlyIncome() {
        val now = 1_700_000_000_000L
        val salary = Transaction(
            amount = 1_000_000.0,
            description = "gaji",
            category = "Gaji",
            categoryId = "g",
            type = TransactionType.INCOME,
            timestamp = at(2.0, now)
        )
        val oldSalary = salary.copy(amount = 9_000_000.0, timestamp = at(20.0, now))
        val txs = listOf(salary, oldSalary, expense(200_000.0, "c1", at(1.0, now)))

        val weekly = listOf(budget(3, "[\"c1\"]", percent = 50.0, period = "weekly"))
            .withSpent(txs, monthlyIncome = 10_000_000.0, now = now)
        // Basisnya income 7 hari terakhir (1.000.000), bukan 10.000.000 bulanan.
        assertEquals(500_000.0, weekly.first().amount, 0.001)
        assertEquals(200_000.0, weekly.first().spent, 0.001)
    }

    @Test
    fun overlappingCategoriesDoNotDoubleCountTheSameExpense() {
        val txs = listOf(expense(90_000.0, "c1"))
        // Dua budget sama-sama mengklaim kategori c1.
        val result = listOf(
            budget(1, "[\"c1\"]"),
            budget(2, "[\"c1\"]")
        ).withSpent(txs, 0.0)

        assertEquals(90_000.0, result[0].spent, 0.001)
        assertEquals(
            "kategori yang sama tidak boleh dihitung dua kali",
            0.0, result[1].spent, 0.001
        )
        assertEquals(
            "total spent tidak boleh melebihi pengeluaran nyata",
            90_000.0,
            result.sumOf { it.spent },
            0.001
        )
    }

    @Test
    fun explicitBudgetIdWinsOverAnotherBudgetsCategoryFallback() {
        val txs = listOf(expense(70_000.0, "c1", budgetId = 2))
        val result = listOf(
            budget(1, "[\"c1\"]"),
            budget(2, "[\"c2\"]")
        ).withSpent(txs, 0.0)

        assertEquals(
            "budget dengan budgetId eksplisit yang harus mengakui transaksi",
            70_000.0, result[1].spent, 0.001
        )
        assertEquals("budget lain tidak ikut menghitung", 0.0, result[0].spent, 0.001)
    }
}
