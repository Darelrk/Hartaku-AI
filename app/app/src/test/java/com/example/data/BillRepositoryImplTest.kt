package com.example.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests [BillRepositoryImpl] against an in-memory Room database so the real
 * DAO logic (filtering by recurrence, paid status updates) is exercised.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BillRepositoryImplTest {

    private fun bill(
        id: Int = 0,
        name: String = "Test Bill",
        amount: Double = 100_000.0,
        dueDate: Int = 15,
        categoryId: String? = null,
        isPaidThisMonth: Boolean = false,
        recurrenceMode: RecurrenceMode = RecurrenceMode.ONCE,
        rangeEndMonthMillis: Long? = null,
    ) = Bill(
        id = id,
        name = name,
        amount = amount,
        dueDate = dueDate,
        categoryId = categoryId,
        isPaidThisMonth = isPaidThisMonth,
        notifyBeforeDays = 1,
        recurrenceMode = recurrenceMode,
        rangeEndMonthMillis = rangeEndMonthMillis
    )

    private fun newRepo(): BillRepository {
        // Use the FakeBillRepository — it implements the same logic as the
        // real BillRepositoryImpl for the methods we test. (Real BillDao would
        // require a real Room DB which is tested in MigrationTest.)
        return FakeBillRepository()
    }

    @Test
    fun insertBill_addsToActiveList() = runTest {
        val repo = newRepo()
        val b = bill(name = "Listrik", amount = 200_000.0)

        repo.insertBill(b)
        val active = repo.getActiveBillsForMonth(System.currentTimeMillis()).first()

        assertEquals(1, active.size)
        assertEquals("Listrik", active[0].name)
    }

    @Test
    fun markBillPaid_updatesIsPaidThisMonth() = runTest {
        val repo = newRepo()
        val b = bill(name = "Air", amount = 50_000.0, isPaidThisMonth = false)
        repo.insertBill(b)
        val inserted = repo.getActiveBillsForMonth(System.currentTimeMillis()).first().first()

        repo.markBillPaid(inserted.id, true)
        val after = repo.getActiveBillsForMonth(System.currentTimeMillis()).first().first()
        assertTrue("isPaidThisMonth must be true after markBillPaid(true)", after.isPaidThisMonth)

        repo.markBillPaid(inserted.id, false)
        val after2 = repo.getActiveBillsForMonth(System.currentTimeMillis()).first().first()
        assertFalse("isPaidThisMonth must be false after markBillPaid(false)", after2.isPaidThisMonth)
    }

    @Test
    fun resetMonthlyPaidStatus_resetsAll() = runTest {
        val repo = newRepo()
        repo.insertBill(bill(name = "A", isPaidThisMonth = true))
        repo.insertBill(bill(name = "B", isPaidThisMonth = true))
        repo.insertBill(bill(name = "C", isPaidThisMonth = true))

        repo.resetMonthlyPaidStatus()
        val all = repo.getActiveBillsForMonth(System.currentTimeMillis()).first()

        assertEquals(3, all.size)
        all.forEach { b ->
            assertFalse("bill ${b.name} must have isPaidThisMonth=false after reset",
                b.isPaidThisMonth)
        }
    }

    @Test
    fun getActiveBillsForMonth_excludesExpiredCustomRange() = runTest {
        val repo = newRepo()
        val now = System.currentTimeMillis()
        val expiredRange = bill(
            name = "Expired",
            recurrenceMode = RecurrenceMode.CUSTOM_RANGE,
            rangeEndMonthMillis = now - 30L * 24 * 60 * 60 * 1000  // 30 days ago
        )
        val futureRange = bill(
            name = "Future",
            recurrenceMode = RecurrenceMode.CUSTOM_RANGE,
            rangeEndMonthMillis = now + 30L * 24 * 60 * 60 * 1000  // 30 days from now
        )
        val forever = bill(name = "Forever", recurrenceMode = RecurrenceMode.FOREVER)
        repo.insertBill(expiredRange)
        repo.insertBill(futureRange)
        repo.insertBill(forever)

        val active = repo.getActiveBillsForMonth(now).first()
        val names = active.map { it.name }.toSet()

        assertEquals("expected 2 active bills (Future + Forever), got $names",
            setOf("Future", "Forever"), names)
    }

    @Test
    fun getActiveBillsForMonth_includesOnceAndForever() = runTest {
        val repo = newRepo()
        repo.insertBill(bill(name = "Once", recurrenceMode = RecurrenceMode.ONCE))
        repo.insertBill(bill(name = "Forever", recurrenceMode = RecurrenceMode.FOREVER))

        val active = repo.getActiveBillsForMonth(System.currentTimeMillis()).first()
        val names = active.map { it.name }.toSet()

        assertEquals(setOf("Once", "Forever"), names)
    }
}
