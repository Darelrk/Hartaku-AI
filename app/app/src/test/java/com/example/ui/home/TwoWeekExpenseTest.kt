package com.example.ui.home

import com.example.data.DailyExpense
import com.example.data.FakeTransactionRepository
import com.example.data.Transaction
import com.example.data.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar

/**
 * Tests bucketing into 14 local-day buckets, half-open boundaries, income
 * exclusion, weekly totals, and zero-denominator handling.
 */
@RunWith(RobolectricTestRunner::class)
class TwoWeekExpenseTest {

    private val dayMs = 24L * 60 * 60 * 1000

    private fun dayStartAt(offset: Int): Long {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, offset)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun expense(amount: Double, timestamp: Long) = Transaction(
        amount = amount,
        description = "d",
        category = "c",
        type = TransactionType.EXPENSE,
        timestamp = timestamp
    )

    private fun build(txs: List<Transaction>) =
        HomeViewModel.buildTwoWeekExpense(txs, Calendar.getInstance())

    @Test
    fun alwaysProducesFourteenDaysOldestFirst() {
        val result = build(emptyList())
        assertEquals("chart harus selalu punya 14 hari", 14, result.daily.size)
        assertEquals(dayStartAt(-13), result.daily.first().dayStart)
        assertEquals(dayStartAt(0), result.daily.last().dayStart)
        result.daily.zipWithNext { a, b ->
            assertTrue("hari harus terurut menaik", b.dayStart > a.dayStart)
        }
    }

    @Test
    fun bucketsEachExpenseIntoItsOwnDay() {
        val txs = listOf(
            expense(10_000.0, dayStartAt(-13) + 3_600_000),
            expense(20_000.0, dayStartAt(-7)),
            expense(30_000.0, dayStartAt(0) + 7_200_000)
        )
        val result = build(txs)

        assertEquals(10_000.0, result.daily[0].total, 0.001)
        assertEquals(20_000.0, result.daily[6].total, 0.001)
        assertEquals(30_000.0, result.daily[13].total, 0.001)
        assertEquals(60_000.0, result.thisWeekTotal + result.prevWeekTotal, 0.001)
    }

    @Test
    fun incomeIsExcludedFromTheChart() {
        val income = Transaction(
            amount = 5_000_000.0,
            description = "gaji",
            category = "Gaji",
            type = TransactionType.INCOME,
            timestamp = dayStartAt(-2) + 1_000
        )
        val result = build(listOf(expense(40_000.0, dayStartAt(-1)), income))
        assertEquals(40_000.0, result.thisWeekTotal + result.prevWeekTotal, 0.001)
    }

    @Test
    fun transactionExactlyOnNextDayStartBelongsToTheNextDay() {
        // Batas half-open: timestamp tepat di awal hari berikutnya milik hari itu,
        // bukan hari sebelumnya. Ini yang membuat chart tidak bergeser sehari.
        val result = build(listOf(expense(70_000.0, dayStartAt(-6))))
        assertEquals(70_000.0, result.daily[7].total, 0.001)
        assertEquals(0.0, result.daily[6].total, 0.001)
    }

    @Test
    fun deltaIsNullWhenPreviousWeekIsZero() {
        val result = build(listOf(expense(30_000.0, dayStartAt(-1))))
        assertEquals(0.0, result.prevWeekTotal, 0.001)
        assertNull("pembagian nol harus menghasilkan null, bukan NaN/Infinity", result.deltaPct)
    }

    @Test
    fun deltaComparesThisWeekAgainstPreviousWeek() {
        val txs = listOf(
            expense(70_000.0, dayStartAt(-1)),
            expense(30_000.0, dayStartAt(-8))
        )
        val result = build(txs)
        assertEquals(70_000.0, result.thisWeekTotal, 0.001)
        assertEquals(30_000.0, result.prevWeekTotal, 0.001)
        // (70 - 30) / 30 * 100
        assertEquals(133.333, result.deltaPct!!, 0.01)
        assertEquals(10_000.0, result.avgDaily, 0.001)
    }

}
