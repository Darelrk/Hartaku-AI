package com.example.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests [AiCacheSeeder.seedIfEmpty] using the in-memory [FakeAiCacheDao].
 * Validates that the seeder:
 *  - Inserts 5 pre-cached insight entries on a fresh DAO.
 *  - Skips existing entries (idempotency via getCache check).
 *  - Persists non-empty responseJson for every entry.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiCacheSeederTest {

    private val EXPECTED_HASHES = setOf(
        "insight:today_summary",
        "insight:week_trend",
        "insight:budget_warning",
        "insight:income_pattern",
        "insight:bill_reminder"
    )

    @Test
    fun seedIfEmpty_freshDao_inserts5Entries() = runTest {
        val dao = FakeAiCacheDao()
        assertEquals(0, dao.count())

        AiCacheSeeder.seedIfEmpty(dao)
        advanceUntilIdleSafe()

        assertEquals(5, dao.count())
    }

    @Test
    fun seedIfEmpty_freshDao_insertsExpectedHashes() = runTest {
        val dao = FakeAiCacheDao()
        AiCacheSeeder.seedIfEmpty(dao)
        advanceUntilIdleSafe()

        val actual = dao.hashes()
        for (hash in EXPECTED_HASHES) {
            assertTrue("Missing cache hash: $hash", hash in actual)
        }
    }

    @Test
    fun seedIfEmpty_freshDao_allEntriesHaveNonEmptyResponseJson() = runTest {
        val dao = FakeAiCacheDao()
        AiCacheSeeder.seedIfEmpty(dao)
        advanceUntilIdleSafe()

        for (entry in dao.snapshot()) {
            assertNotNull("Cache ${entry.queryHash} must have responseJson", entry.responseJson)
            assertTrue(
                "Cache ${entry.queryHash} responseJson should not be blank",
                entry.responseJson.isNotBlank()
            )
            // Each response should contain at least "insight" and "saran" keys (Indonesian format)
            assertTrue(
                "Cache ${entry.queryHash} responseJson should contain 'insight' key",
                entry.responseJson.contains("\"insight\"")
            )
            assertTrue(
                "Cache ${entry.queryHash} responseJson should contain 'saran' key",
                entry.responseJson.contains("\"saran\"")
            )
        }
    }

    @Test
    fun seedIfEmpty_freshDao_allEntriesHaveValidCreatedAt() = runTest {
        val dao = FakeAiCacheDao()
        AiCacheSeeder.seedIfEmpty(dao)
        advanceUntilIdleSafe()

        for (entry in dao.snapshot()) {
            assertTrue(
                "Cache ${entry.queryHash} createdAt should be > 0",
                entry.createdAt > 0L
            )
        }
    }

    @Test
    fun seedIfEmpty_existingEntries_doesNotDuplicate() = runTest {
        val dao = FakeAiCacheDao()
        AiCacheSeeder.seedIfEmpty(dao)
        advanceUntilIdleSafe()
        val firstCount = dao.count()
        assertEquals(5, firstCount)

        // Call seeder a second time — should still be 5 entries, not 10.
        AiCacheSeeder.seedIfEmpty(dao)
        advanceUntilIdleSafe()

        assertEquals(5, dao.count())
    }

    @Test
    fun seedIfEmpty_partiallySeeded_fillsMissingEntries() = runTest {
        val dao = FakeAiCacheDao()
        // Pre-seed one entry with a distinct responseJson to verify the seeder
        // preserves it (does NOT overwrite via the getCache check).
        dao.insertCache(
            AiCache(
                queryHash = "insight:today_summary",
                rawInput = "summary_today",
                responseJson = """{"insight":"preserved_by_test","saran":"preserved_by_test"}"""
            )
        )
        assertEquals(1, dao.count())

        AiCacheSeeder.seedIfEmpty(dao)
        advanceUntilIdleSafe()

        // All 5 entries should now be present
        assertEquals(5, dao.count())
        // Pre-existing entry's responseJson should be preserved (not overwritten)
        val pre = dao.firstCache("insight:today_summary")
        assertNotNull("Pre-existing entry should remain", pre)
        assertEquals(
            "Pre-existing entry's responseJson should be preserved (not overwritten by seeder)",
            """{"insight":"preserved_by_test","saran":"preserved_by_test"}""",
            pre!!.responseJson
        )
    }

    /** Compatibility shim: FakeRepository updates are synchronous, no dispatcher involved. */
    private suspend fun advanceUntilIdleSafe() {
        kotlinx.coroutines.yield()
    }
}
