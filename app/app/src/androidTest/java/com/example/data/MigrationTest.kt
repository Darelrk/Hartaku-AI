package com.example.data

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * Room migration tests for HartaKu database.
 *
 * Validates that MIGRATION_10_11 correctly creates the `bills` table
 * with all required columns.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val TEST_DB_NAME = "migration-test-db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate10To11_createsBillsTable() {
        // Start with a v10 database (no bills table)
        helper.createDatabase(TEST_DB_NAME, 10).apply {
            // v10 has: transactions, budgets, categories, ai_cache
            // Verify bills table does NOT exist
            val tables = query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
                val names = mutableListOf<String>()
                while (cursor.moveToNext()) {
                    names.add(cursor.getString(0))
                }
                names
            }
            assertTrue("bills should not exist in v10", tables.none { it == "bills" })
            close()
        }

        // Run migration 10 → 11
        helper.runMigrationsAndValidate(TEST_DB_NAME, 11, true, MIGRATION_10_11).apply {
            // Verify bills table exists with correct columns
            val cursor = query("PRAGMA table_info(bills)")
            val columns = mutableListOf<String>()
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
            cursor.close()

            assertTrue("id column exists", "id" in columns)
            assertTrue("name column exists", "name" in columns)
            assertTrue("amount column exists", "amount" in columns)
            assertTrue("dueDate column exists", "dueDate" in columns)
            assertTrue("categoryId column exists", "categoryId" in columns)
            assertTrue("isPaidThisMonth column exists", "isPaidThisMonth" in columns)
            assertTrue("notifyBeforeDays column exists", "notifyBeforeDays" in columns)
            assertTrue("recurrenceMode column exists", "recurrenceMode" in columns)
            assertTrue("rangeEndMonthMillis column exists", "rangeEndMonthMillis" in columns)

            assertEquals(9, columns.size)
            close()
        }
    }

    @Test
    fun migrate10To11_insertsBillSuccessfully() {
        helper.createDatabase(TEST_DB_NAME, 10).apply { close() }

        helper.runMigrationsAndValidate(TEST_DB_NAME, 11, true, MIGRATION_10_11).apply {
            // Insert a bill
            execSQL(
                "INSERT INTO bills (name, amount, dueDate, categoryId, isPaidThisMonth, notifyBeforeDays, recurrenceMode) " +
                "VALUES ('Listrik', 500000.0, 10, 1, 0, 1, 'ONCE')"
            )

            // Query it back
            val cursor = query("SELECT name, amount, isPaidThisMonth FROM bills WHERE name = 'Listrik'")
            assertTrue(cursor.moveToFirst())
            assertEquals("Listrik", cursor.getString(0))
            assertEquals(500000.0, cursor.getDouble(1), 0.01)
            assertEquals(0, cursor.getInt(2))
            cursor.close()
            close()
        }
    }

    @Test
    fun fullSchema11_hasAllTables() {
        // Validate the full schema matches v11 expectations
        helper.createDatabase(TEST_DB_NAME, 11).apply {
            val tables = query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
                val names = mutableListOf<String>()
                while (cursor.moveToNext()) {
                    names.add(cursor.getString(0))
                }
                names
            }

            assertTrue("transactions exists", "transactions" in tables)
            assertTrue("budgets exists", "budgets" in tables)
            assertTrue("categories exists", "categories" in tables)
            assertTrue("ai_cache exists", "ai_cache" in tables)
            assertTrue("bills exists", "bills" in tables)
            close()
        }
    }

    @Test
    fun migrate27To28_dropsAgentTasksAndPreservesTransactions() {
        helper.createDatabase(TEST_DB_NAME, 27).apply {
            // v27 masih punya kolom NOT NULL `source` + `rawInput`; keduanya dibuang di v28.
            execSQL(
                "INSERT INTO transactions (amount, description, category, source, rawInput, type, timestamp) " +
                "VALUES (50000.0, 'Test expense', 'Makanan', 'manual', 'Test expense', 'EXPENSE', ${System.currentTimeMillis()})"
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB_NAME, 28, true, MIGRATION_27_28).apply {
            // Verify transaction data survived migration
            val cursor = query("SELECT amount, description FROM transactions WHERE description = 'Test expense'")
            assertTrue(cursor.moveToFirst())
            assertEquals(50000.0, cursor.getDouble(0), 0.01)
            assertEquals("Test expense", cursor.getString(1))
            cursor.close()

            // Verify agent_tasks table is gone
            val tables = query("SELECT name FROM sqlite_master WHERE type='table'").use { c ->
                val names = mutableListOf<String>()
                while (c.moveToNext()) names.add(c.getString(0))
                names
            }
            assertTrue("agent_tasks should be dropped", "agent_tasks" !in tables)

            close()
        }
    }
}
