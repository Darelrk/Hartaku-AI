package com.example.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Tests [BillSeeder.seedIfEmpty] using the in-memory [FakeBillRepository].
 * Validates that the seeder:
 *  - Inserts 12 bills on a fresh repository.
 *  - Mixes FOREVER (8), CUSTOM_RANGE (2), and ONCE (2) recurrence modes.
 *  - Skips on a repository that already has bills.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BillSeederTest {

    private fun startOfCurrentMonthMillis(): Long {
        val now = Calendar.getInstance()
        return Calendar.getInstance().apply {
            set(now.get(Calendar.YEAR), now.get(Calendar.MONTH), 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun seedIfEmpty_freshRepo_inserts12Bills() = runTest {
        val repo = FakeBillRepository()
        assertTrue(repo.bills.value.isEmpty())

        BillSeeder.seedIfEmpty(repo)
        advanceUntilIdleSafe()

        val active = repo.getActiveBillsForMonth(startOfCurrentMonthMillis()).first()
        assertEquals("Expected 12 active bills, got ${active.size}", 12, active.size)
    }

    @Test
    fun seedIfEmpty_freshRepo_includes8ForeverBills() = runTest {
        val repo = FakeBillRepository()
        BillSeeder.seedIfEmpty(repo)
        advanceUntilIdleSafe()

        val active = repo.getActiveBillsForMonth(startOfCurrentMonthMillis()).first()
        val forever = active.filter { it.recurrenceMode == RecurrenceMode.FOREVER }
        assertEquals("Expected 8 FOREVER bills, got ${forever.size}", 8, forever.size)
    }

    @Test
    fun seedIfEmpty_freshRepo_includes2CustomRangeBills() = runTest {
        val repo = FakeBillRepository()
        BillSeeder.seedIfEmpty(repo)
        advanceUntilIdleSafe()

        val active = repo.getActiveBillsForMonth(startOfCurrentMonthMillis()).first()
        val custom = active.filter { it.recurrenceMode == RecurrenceMode.CUSTOM_RANGE }
        assertEquals("Expected 2 CUSTOM_RANGE bills, got ${custom.size}", 2, custom.size)
        // Both should have a non-null rangeEndMonthMillis
        for (b in custom) {
            assertNotNull("CUSTOM_RANGE bill ${b.name} must have rangeEndMonthMillis", b.rangeEndMonthMillis)
        }
    }

    @Test
    fun seedIfEmpty_freshRepo_includes2OnceBills() = runTest {
        val repo = FakeBillRepository()
        BillSeeder.seedIfEmpty(repo)
        advanceUntilIdleSafe()

        val active = repo.getActiveBillsForMonth(startOfCurrentMonthMillis()).first()
        val once = active.filter { it.recurrenceMode == RecurrenceMode.ONCE }
        assertEquals("Expected 2 ONCE bills, got ${once.size}", 2, once.size)
    }

    @Test
    fun seedIfEmpty_freshRepo_allBillsHavePositiveAmount() = runTest {
        val repo = FakeBillRepository()
        BillSeeder.seedIfEmpty(repo)
        advanceUntilIdleSafe()

        val active = repo.getActiveBillsForMonth(startOfCurrentMonthMillis()).first()
        for (b in active) {
            assertTrue("Bill ${b.name} should have amount > 0", b.amount > 0.0)
        }
    }

    @Test
    fun seedIfEmpty_freshRepo_allBillsHaveValidDueDate() = runTest {
        val repo = FakeBillRepository()
        BillSeeder.seedIfEmpty(repo)
        advanceUntilIdleSafe()

        val active = repo.getActiveBillsForMonth(startOfCurrentMonthMillis()).first()
        for (b in active) {
            assertTrue(
                "Bill ${b.name} dueDate should be 1..31, got ${b.dueDate}",
                b.dueDate in 1..31
            )
        }
    }

    @Test
    fun seedIfEmpty_existingBills_skips() = runTest {
        val repo = FakeBillRepository()
        // Pre-insert one bill
        repo.insertBill(
            Bill(
                name = "Pre-existing",
                amount = 50_000.0,
                dueDate = 10,
                categoryId = null,
                recurrenceMode = RecurrenceMode.ONCE
            )
        )

        BillSeeder.seedIfEmpty(repo)
        advanceUntilIdleSafe()

        val active = repo.getActiveBillsForMonth(startOfCurrentMonthMillis()).first()
        // Seeder must skip — only the pre-existing bill remains.
        assertEquals(1, active.size)
        assertEquals("Pre-existing", active[0].name)
    }

    /** Compatibility shim: FakeRepository updates are synchronous, no dispatcher involved. */
    private suspend fun advanceUntilIdleSafe() {
        kotlinx.coroutines.yield()
    }
}
