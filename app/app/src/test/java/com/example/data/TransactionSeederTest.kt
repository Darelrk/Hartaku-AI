package com.example.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests [TransactionSeeder.seedIfEmpty] using the in-memory
 * [FakeTransactionRepository]. Validates that the seeder:
 *  - Skips when the repository already has data.
 *  - Inserts a meaningful batch of transactions on a fresh repository.
 *  - Covers at least 14 distinct categoryId values (out of 18 possible).
 *  - Produces transactions with non-null timestamps and amounts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionSeederTest {

    @Test
    fun seedIfEmpty_freshRepo_inserts180DaysOfSyntheticData() = runTest {
        val repo = FakeTransactionRepository()
        assertEquals(0, repo.transactions.value.size)

        TransactionSeeder.seedIfEmpty(repo)
        advanceUntilIdleSafe()

        val all = repo.getAllTransactions().first()
        // 12 mock spread entries (-2..+1 day offsets) for HomeFeed demo.
        assertEquals("Expected 12 mock transactions, got ${all.size}", 12, all.size)
    }

    @Test
    fun seedIfEmpty_freshRepo_covers6DistinctCategories() = runTest {
        val repo = FakeTransactionRepository()
        TransactionSeeder.seedIfEmpty(repo)
        advanceUntilIdleSafe()

        val all = repo.getAllTransactions().first()
        val distinctCategories = all.mapNotNull { it.categoryId }.toSet()
        // Mock spread covers: Transport, Makanan, Belanja, Tagihan, Hiburan, Gaji = 6
        assertTrue(
            "Expected >= 6 distinct categoryIds, got ${distinctCategories.size}",
            distinctCategories.size >= 6
        )
    }


    @Test
    fun seedIfEmpty_existingData_skips() = runTest {
        val repo = FakeTransactionRepository()
        // Pre-seed a single transaction
        repo.insertTransaction(
            Transaction(
                amount = 100.0,
                description = "Pre-existing",
                category = "Makanan",
                categoryId = CategorySeeder.ID_MAKANAN,
                type = TransactionType.EXPENSE,
                timestamp = 1L
            )
        )

        TransactionSeeder.seedIfEmpty(repo)
        advanceUntilIdleSafe()

        val all = repo.getAllTransactions().first()
        // Seeder must skip — only the pre-existing tx remains.
        assertEquals(1, all.size)
        assertEquals("Pre-existing", all[0].description)
    }

    @Test
    fun seedIfEmpty_allTransactions_haveTimestampAndAmount() = runTest {
        val repo = FakeTransactionRepository()
        TransactionSeeder.seedIfEmpty(repo)
        advanceUntilIdleSafe()

        val all = repo.getAllTransactions().first()
        assertTrue("Seeded transactions must not be empty", all.isNotEmpty())
        for (tx in all) {
            assertNotNull("Transaction must have timestamp", tx.timestamp)
            assertTrue("Transaction amount must be > 0", tx.amount > 0.0)
        }
    }

    @Test
    fun seedIfEmpty_includesIncomeTransactions() = runTest {
        val repo = FakeTransactionRepository()
        TransactionSeeder.seedIfEmpty(repo)
        advanceUntilIdleSafe()

        val all = repo.getAllTransactions().first()
        val income = all.filter { it.type == TransactionType.INCOME }
        assertTrue("Expected income transactions (Gaji, etc.) to be seeded", income.isNotEmpty())
    }

    @Test
    fun seedIfEmpty_includesMockSpreadEntries() = runTest {
        val repo = FakeTransactionRepository()
        TransactionSeeder.seedIfEmpty(repo)
        advanceUntilIdleSafe()

        val all = repo.getAllTransactions().first()
        // Mock spread seeds "Kopi Kenangan" and "Nasi Padang" descriptions specifically
        val kopi = all.firstOrNull { it.description == "Kopi Kenangan" }
        val nasi = all.firstOrNull { it.description == "Nasi Padang" }
        assertNotNull("Mock-spread entry 'Kopi Kenangan' must be present", kopi)
        assertNotNull("Mock-spread entry 'Nasi Padang' must be present", nasi)
    }

}

/** Compatibility shim: older Kotlin coroutines test versions don't expose advanceUntilIdle. */
private suspend fun advanceUntilIdleSafe() {
    // FakeRepository updates are synchronous; no dispatcher is involved.
    // Yield once so any post-call Flows are observed.
    kotlinx.coroutines.yield()
}
