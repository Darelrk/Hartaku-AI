package com.example.ui

import com.example.MainDispatcherRule
import com.example.data.Budget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests the deleted-budgets flow by exercising [BudgetManagementViewModel]
 * directly (no Compose). The Compose-level rendering is covered by the
 * existing screenshot tests in `ui/screenshot/`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DeletedBudgetsScreenTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var budgetRepo: com.example.data.FakeBudgetRepository
    private lateinit var categoryRepo: com.example.data.FakeCategoryRepository
    private lateinit var transactionRepo: com.example.data.FakeTransactionRepository

    @Before
    fun setUp() {
        budgetRepo = com.example.data.FakeBudgetRepository()
        categoryRepo = com.example.data.FakeCategoryRepository()
        transactionRepo = com.example.data.FakeTransactionRepository()
    }

    private fun newViewModel() = BudgetManagementViewModel(
        budgetRepo = budgetRepo,
        categoryRepo = categoryRepo,
        transactionRepo = transactionRepo
    )

    @Test
    fun emptyState_deletedBudgetsIsEmpty() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()

        assertEquals(0, vm.uiState.value.deletedBudgets.size)
    }

    @Test
    fun populatedState_showsCount() = runTest {
        // Use insertAll so both rows land; insert(id=0) would replace the
        // previous one (FakeBudgetRepository.insert uses indexOfFirst(id)).
        budgetRepo.insertAll(listOf(
            Budget(id = 1, name = "Makanan", amount = 1000.0, deletedAt = 1L),
            Budget(id = 2, name = "Transport", amount = 500.0, deletedAt = 2L)
        ))
        val vm = newViewModel()
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.deletedBudgets.size)
    }

    @Test
    fun restore_removesFromDeletedList() = runTest {
        val b1 = Budget(id = 1, name = "Makanan", amount = 1000.0, deletedAt = 1L)
        val b2 = Budget(id = 2, name = "Transport", amount = 500.0, deletedAt = 2L)
        budgetRepo.insertAll(listOf(b1, b2))
        val vm = newViewModel()
        advanceUntilIdle()
        val before = vm.uiState.value.deletedBudgets.size
        assertEquals("expected 2 deleted before restore, got $before", 2, before)

        vm.restoreBudget(b1.id)
        advanceUntilIdle()
        val after = vm.uiState.value.deletedBudgets.size
        assertEquals("expected 1 deleted after restore, got $after", 1, after)
        assertEquals(
            "remaining deleted budget must be b2",
            "Transport",
            vm.uiState.value.deletedBudgets.first().name
        )
    }

    @Test
    fun deletedBudgets_excludesActiveBudgets() = runTest {
        // Active budget (deletedAt = null) should NOT appear in deleted list
        budgetRepo.insertAll(listOf(
            Budget(id = 1, name = "Active", amount = 1000.0, deletedAt = null),
            Budget(id = 2, name = "Archived", amount = 500.0, deletedAt = 5L)
        ))
        val vm = newViewModel()
        advanceUntilIdle()

        val deleted = vm.uiState.value.deletedBudgets
        assertEquals(1, deleted.size)
        assertEquals("Archived", deleted.first().name)
    }
}
