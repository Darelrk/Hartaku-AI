package com.example.ui

import com.example.MainDispatcherRule
import com.example.data.Budget
import com.example.data.CategorySeeder
import com.example.data.FakeBudgetRepository
import com.example.data.FakeCategoryRepository
import com.example.data.FakeTransactionRepository
import com.example.data.Transaction
import com.example.data.TransactionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProfileViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun testBudgets_recalculatesSpentFromCurrentMonthTransactions() = runTest {
        val transactionRepo = FakeTransactionRepository()
        val budgetRepo = FakeBudgetRepository()
        val categoryRepo = FakeCategoryRepository()
        val todayMs = Calendar.getInstance().timeInMillis

        budgetRepo.insertAll(listOf(
            Budget(
                id = 1,
                name = "Transport",
                icon = "transport",
                amount = 500_000.0,
                spent = 0.0
            )
        ))
        // withMonthlySpent now matches by budgetId (not category). Both transactions
        // are explicitly attributed to budget id=1 so they sum into its `spent` field.
        transactionRepo.insertTransaction(Transaction(
            id = 1,
            amount = 45_000.0,
            description = "Gojek",
            category = "Transport",
            categoryId = CategorySeeder.ID_TRANSPORT,
            budgetId = 1,
            type = TransactionType.EXPENSE,
            timestamp = todayMs
        ))
        transactionRepo.insertTransaction(Transaction(
            id = 2,
            amount = 30_000.0,
            description = "Bensin",
            category = "Transport",
            categoryId = CategorySeeder.ID_TRANSPORT,
            budgetId = 1,
            type = TransactionType.EXPENSE,
            timestamp = todayMs
        ))

        val viewModel = ProfileViewModel(transactionRepo, budgetRepo, categoryRepo)
        advanceUntilIdle()

        val budget = viewModel.uiState.value.budgets.first { it.name == "Transport" }
        assertEquals(75_000.0, budget.spent, 0.1)
        assertEquals(500_000.0, budget.amount, 0.1)
    }
}
