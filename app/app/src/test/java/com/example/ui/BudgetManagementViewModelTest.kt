package com.example.ui

import com.example.MainDispatcherRule
import com.example.data.Budget
import com.example.data.Category
import com.example.data.CategorySeeder
import com.example.data.FakeBudgetRepository
import com.example.data.FakeCategoryRepository
import com.example.data.FakeTransactionRepository
import com.example.data.Transaction
import com.example.data.TransactionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for [BudgetManagementViewModel].
 *
 * Coverage:
 *  1. Load active budgets from repo → UiState.activeBudgets populated
 *  2. Load expense categories → UiState.expenseCategories populated (only EXPENSE)
 *  3. Create a new budget → row appears in active list
 *  4. Update a budget (limit + alokasi) → spent NOT reset per Decision #8
 *  5. Archive a budget → row leaves active list, appears in deleted list, justArchived set
 *  6. Undo archive → row returns to active list
 *  7. Restore a budget by id (used by DeletedBudgetsScreen) → row back in active
 *  8. Validation: createBudget with amount <= 0 sets error
 *  9. Validation: createBudget with blank name sets error
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BudgetManagementViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun makeExpenseCategory(
        id: String = CategorySeeder.ID_MAKANAN,
        name: String = "Makanan"
    ) = Category(
        id = id,
        parentId = null,
        name = name,
        slug = name.lowercase(),
        typeClass = "EXPENSE",
        aliases = "[]",
        icon = "category",
        color = "#95A5A6",
        sortOrder = 50
    )

    private fun makeBudget(
        id: Int = 0,
        name: String = "Makanan",
        icon: String = "wallet",
        amount: Double = 1_000_000.0,
        spent: Double = 0.0,
        percent: Double = 0.0,
        deletedAt: Long? = null
    ) = Budget(
        id = id,
        name = name,
        icon = icon,
        amount = amount,
        spent = spent,
        period = "monthly",
        percent = percent,
        deletedAt = deletedAt
    )

    // ---- Test 1: Load active budgets ----
    @Test
    fun testLoadActiveBudgets_populatesState() = runTest {
        val budgetRepo = FakeBudgetRepository()
        val categoryRepo = FakeCategoryRepository()
        budgetRepo.insertAll(listOf(
            makeBudget(id = 1, name = "Makanan", amount = 1500000.0),
            makeBudget(id = 2, name = "Transport", amount = 500000.0)
        ))

        val viewModel = BudgetManagementViewModel(budgetRepo, categoryRepo, FakeTransactionRepository())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.activeBudgets.size)
        assertEquals("Makanan", state.activeBudgets[0].name)
        assertEquals("Transport", state.activeBudgets[1].name)
        assertTrue("isLoading should be false after first emission", !state.isLoading)
    }

    // ---- Test 2: Load expense categories (only EXPENSE) ----
    @Test
    fun testLoadExpenseCategories_filtersToExpenseOnly() = runTest {
        val budgetRepo = FakeBudgetRepository()
        val categoryRepo = FakeCategoryRepository()
        categoryRepo.insert(makeExpenseCategory(id = CategorySeeder.ID_MAKANAN, name = "Makanan"))
        categoryRepo.insert(makeExpenseCategory(id = CategorySeeder.ID_TRANSPORT, name = "Transport"))
        categoryRepo.insert(
            Category(
                id = CategorySeeder.ID_GAJI,
                parentId = null,
                name = "Gaji",
                slug = "gaji",
                typeClass = "INCOME",  // ← NOT EXPENSE
                aliases = "[]",
                icon = "category",
                color = "#95A5A6",
                sortOrder = 50
            )
        )

        val viewModel = BudgetManagementViewModel(budgetRepo, categoryRepo, FakeTransactionRepository())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.expenseCategories.size)
        assertTrue(state.expenseCategories.all { it.typeClass == "EXPENSE" })
        assertTrue(state.expenseCategories.any { it.id == CategorySeeder.ID_MAKANAN })
        assertTrue(state.expenseCategories.any { it.id == CategorySeeder.ID_TRANSPORT })
    }

    // ---- Test 3: Create a new budget (percent-based) ----
    @Test
    fun testCreateBudget_appendsToActiveList() = runTest {
        val budgetRepo = FakeBudgetRepository()
        val categoryRepo = FakeCategoryRepository()
        val transactionRepo = FakeTransactionRepository()
        // Seed income so currentMonthIncome > 0 and amount can be derived
        transactionRepo.insertTransaction(Transaction(
            id = 1,
            amount = 2_000_000.0,
            description = "Gaji",
            category = "Gaji",
            type = TransactionType.INCOME,
            timestamp = System.currentTimeMillis()
        ))
        categoryRepo.insert(makeExpenseCategory())

        val viewModel = BudgetManagementViewModel(budgetRepo, categoryRepo, transactionRepo)
        advanceUntilIdle()

        viewModel.createBudget(
            name = "Makanan",
            icon = "food",
            period = "monthly",
            categoryIds = listOf("cat_makanan"),
            percent = 30.0,
            manualAmount = null,
            note = "Makan + kopi"
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.activeBudgets.size)
        val created = state.activeBudgets[0]
        assertEquals("Makanan", created.name)
        assertEquals("food", created.icon)
        // amount = 2jt × 30% = 600k (derived)
        assertEquals(600_000.0, created.amount, 0.01)
        assertEquals(30.0, created.percent, 0.01)
        assertEquals("Makan + kopi", created.note)
        assertEquals("monthly", created.period)
    }

    // ---- Test 4: Update budget (spent NOT reset per Decision #8) ----
    @Test
    fun testUpdateBudget_doesNotResetSpent() = runTest {
        val budgetRepo = FakeBudgetRepository()
        val categoryRepo = FakeCategoryRepository()
        val transactionRepo = FakeTransactionRepository()
        budgetRepo.insertAll(listOf(
            makeBudget(id = 1, amount = 1_000_000.0, spent = 0.0)
        ))
        // Seed income so currentMonthIncome > 0 and amount can be derived
        transactionRepo.insertTransaction(Transaction(
            id = 1,
            amount = 5_000_000.0,
            description = "Gaji",
            category = "Gaji",
            type = TransactionType.INCOME,
            timestamp = System.currentTimeMillis()
        ))
        // Pre-existing 350k of attributed expenses. withMonthlySpent (from t-002)
        // sums these into the budget's `spent` field on every load.
        transactionRepo.insertTransaction(Transaction(
            id = 2,
            amount = 350_000.0,
            description = "Makan existing",
            category = "Makanan",
            budgetId = 1,
            type = TransactionType.EXPENSE,
            timestamp = System.currentTimeMillis()
        ))
        val viewModel = BudgetManagementViewModel(budgetRepo, categoryRepo, transactionRepo)
        advanceUntilIdle()

        val original = viewModel.uiState.value.activeBudgets.first { it.id == 1 }
        assertEquals(350_000.0, original.spent, 0.01)  // sanity: pre-update spent is 350k

        viewModel.updateBudget(original, newPercent = 25.0)
        advanceUntilIdle()

        val updated = viewModel.uiState.value.activeBudgets.first { it.id == 1 }
        // amount = 5jt × 25% = 1.25jt (derived from income)
        assertEquals(1_250_000.0, updated.amount, 0.01)
        assertEquals(25.0, updated.percent, 0.01)
        // Spent MUST be preserved (re-computed by withMonthlySpent from the same transaction)
        assertEquals(350_000.0, updated.spent, 0.01)
    }

    // ---- Test 5: Archive (soft delete) ----
    @Test
    fun testArchiveBudget_movesToDeletedList() = runTest {
        val budgetRepo = FakeBudgetRepository()
        val categoryRepo = FakeCategoryRepository()
        budgetRepo.insertAll(listOf(
            makeBudget(id = 1, name = "Makanan"),
            makeBudget(id = 2, name = "Transport")
        ))

        val viewModel = BudgetManagementViewModel(budgetRepo, categoryRepo, FakeTransactionRepository())
        advanceUntilIdle()

        val target = viewModel.uiState.value.activeBudgets.first { it.id == 1 }
        viewModel.archiveBudget(target)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.activeBudgets.size)
        assertEquals("Transport", state.activeBudgets[0].name)
        assertEquals(1, state.deletedBudgets.size)
        assertEquals("Makanan", state.deletedBudgets[0].name)
        assertNotNull("justArchived should be set for snackbar undo", state.justArchived)
        assertEquals(1, state.justArchived!!.id)
    }

    // ---- Test 6: Undo archive ----
    @Test
    fun testUndoArchive_bringsBackToActive() = runTest {
        val budgetRepo = FakeBudgetRepository()
        val categoryRepo = FakeCategoryRepository()
        budgetRepo.insertAll(listOf(
            makeBudget(id = 1, name = "Makanan")
        ))

        val viewModel = BudgetManagementViewModel(budgetRepo, categoryRepo, FakeTransactionRepository())
        advanceUntilIdle()

        viewModel.archiveBudget(viewModel.uiState.value.activeBudgets[0])
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.activeBudgets.size)

        viewModel.undoArchive()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.activeBudgets.size)
        assertEquals("Makanan", state.activeBudgets[0].name)
        assertEquals(0, state.deletedBudgets.size)
        assertNull("justArchived should be cleared after undo", state.justArchived)
    }

    // ---- Test 7: Restore by id (used by DeletedBudgetsScreen) ----
    @Test
    fun testRestoreBudget_byId_bringsBack() = runTest {
        val budgetRepo = FakeBudgetRepository()
        val categoryRepo = FakeCategoryRepository()
        budgetRepo.insertAll(listOf(
            makeBudget(id = 1, name = "Makanan")
        ))

        val viewModel = BudgetManagementViewModel(budgetRepo, categoryRepo, FakeTransactionRepository())
        advanceUntilIdle()

        viewModel.archiveBudget(viewModel.uiState.value.activeBudgets[0])
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.deletedBudgets.size)

        viewModel.restoreBudget(1)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.activeBudgets.size)
        assertEquals(0, state.deletedBudgets.size)
    }

    // ---- Test 8: Validation: percent <= 0 ----
    @Test
    fun testCreateBudget_invalidPercent_setsError() = runTest {
        val budgetRepo = FakeBudgetRepository()
        val categoryRepo = FakeCategoryRepository()
        categoryRepo.insert(makeExpenseCategory())
        val viewModel = BudgetManagementViewModel(budgetRepo, categoryRepo, FakeTransactionRepository())
        advanceUntilIdle()

         viewModel.createBudget(
            name = "Makanan",
            icon = "food",
            period = "monthly",
            categoryIds = emptyList(),
            percent = 0.0,
            manualAmount = null,
            note = null
        )
        advanceUntilIdle()

        assertEquals("Jumlah anggaran harus > Rp 0", viewModel.uiState.value.error)
        assertEquals(0, viewModel.uiState.value.activeBudgets.size)

    }

    // ---- Test 9: Validation: blank name ----
    @Test
    fun testCreateBudget_blankName_setsError() = runTest {
        val budgetRepo = FakeBudgetRepository()
        val categoryRepo = FakeCategoryRepository()

        val viewModel = BudgetManagementViewModel(budgetRepo, categoryRepo, FakeTransactionRepository())
        advanceUntilIdle()

        viewModel.createBudget(
            name = "",
            icon = "wallet",
            period = "monthly",
            categoryIds = emptyList(),
            percent = 0.0,
            manualAmount = null,
            note = null
        )
        advanceUntilIdle()

        assertEquals("Nama budget wajib diisi", viewModel.uiState.value.error)
        assertEquals(0, viewModel.uiState.value.activeBudgets.size)
    }

    // ---- Test 10: Edit dialog open/close ----
    @Test
    fun testEditDialog_openClose() = runTest {
        val budgetRepo = FakeBudgetRepository()
        val categoryRepo = FakeCategoryRepository()
        budgetRepo.insertAll(listOf(makeBudget(id = 1)))

        val viewModel = BudgetManagementViewModel(budgetRepo, categoryRepo, FakeTransactionRepository())
        advanceUntilIdle()

        assertTrue(!viewModel.uiState.value.showEditDialog)
        assertNull(viewModel.uiState.value.editingBudget)

        val target = viewModel.uiState.value.activeBudgets[0]
        viewModel.openEditDialog(target)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showEditDialog)
        assertEquals(1, viewModel.uiState.value.editingBudget?.id)

        viewModel.closeEditDialog()
        advanceUntilIdle()

        assertTrue(!viewModel.uiState.value.showEditDialog)
        assertNull(viewModel.uiState.value.editingBudget)
    }

    // ---- Test 11: Clear error ----
    @Test
    fun testClearError_clearsError() = runTest {
        val budgetRepo = FakeBudgetRepository()
        val categoryRepo = FakeCategoryRepository()

        val viewModel = BudgetManagementViewModel(budgetRepo, categoryRepo, FakeTransactionRepository())
        advanceUntilIdle()

        viewModel.createBudget("", "wallet", "monthly", emptyList(), 0.0, null, null)
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.error)

        viewModel.clearError()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.error)
    }

    // ---- Test 12: getAllBudgets Flow filters out deleted ----
    @Test
    fun testGetAllBudgetsFlow_filtersOutDeleted() = runTest {
        val budgetRepo = FakeBudgetRepository()
        budgetRepo.insertAll(listOf(
            makeBudget(id = 1, name = "Makanan"),
            makeBudget(id = 2, name = "Transport", deletedAt = System.currentTimeMillis())  // already deleted
        ))

        val active = budgetRepo.getAllBudgets().first()
        assertEquals(1, active.size)
        assertEquals("Makanan", active[0].name)
    }
}
