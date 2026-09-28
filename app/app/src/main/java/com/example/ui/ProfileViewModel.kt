package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppContainer
import com.example.data.Budget
import com.example.data.Category
import com.example.data.CategoryRepository
import com.example.data.TransactionRepository
import com.example.data.BudgetRepository
import com.example.data.categoryIdsToJson
import com.example.ui.home.currentMonthRange
import com.example.ui.home.withSpent
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

data class ProfileUiState(
    val totalTransactions: Int = 0,
    val activeDays: Int = 0,
    val budgets: List<Budget> = emptyList(),
    val currentMonthIncome: Double = 0.0,
    val expenseCategories: List<Category> = emptyList()
)

class ProfileViewModel(
    private val transactionRepo: TransactionRepository,
    private val budgetRepo: BudgetRepository,
    private val categoryRepo: CategoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadStats()
        loadBudgets()
        loadCurrentMonthIncome()
        loadExpenseCategories()
    }

    private fun loadStats() {
        viewModelScope.launch {
            transactionRepo.getAllTransactions().collect { txs ->
                val activeDayCount = txs.map { tx ->
                    val cal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
                    "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
                }.distinct().count()

                _uiState.update { it.copy(
                    totalTransactions = txs.size,
                    activeDays = activeDayCount
                )}
            }
        }
    }

    private fun loadBudgets() {
        viewModelScope.launch {
            val (monthStart, monthEnd) = currentMonthRange()
            combine(
                budgetRepo.getAllBudgets(),
                transactionRepo.getTransactionsInRange(monthStart, monthEnd),
                transactionRepo.getIncomeInRange(monthStart, monthEnd)
            ) { budgets, txs, income ->
                budgets.withSpent(txs, income)
            }.collect { budgets ->
                _uiState.update { it.copy(budgets = budgets) }
            }
        }
    }

    private fun loadCurrentMonthIncome() {
        viewModelScope.launch {
            val (monthStart, monthEnd) = currentMonthRange()
            transactionRepo.getIncomeInRange(monthStart, monthEnd).collect { income ->
                _uiState.update { it.copy(currentMonthIncome = income) }
            }
        }
    }

    private fun loadExpenseCategories() {
        viewModelScope.launch {
            val cats = categoryRepo.getByTypeClass("EXPENSE")
            _uiState.update { it.copy(expenseCategories = cats) }
        }
    }

    fun createBudget(
        name: String,
        icon: String,
        period: String,
        categoryIds: List<String>,
        percent: Double,
        manualAmount: Double?,
        note: String?
    ) {
        val income = _uiState.value.currentMonthIncome
        val amount = if (percent > 0.0 && income > 0.0) income * (percent / 100.0)
            else manualAmount ?: 0.0
        if (amount <= 0.0) return
        viewModelScope.launch {
            budgetRepo.insertBudget(
                Budget(
                    name = name.trim(),
                    icon = icon.ifBlank { "wallet" },
                    amount = amount,
                    categoryId = null,
                    categoryIds = categoryIdsToJson(categoryIds),
                    period = period,
                    percent = if (percent > 0.0) percent else 0.0,
                    note = note?.takeIf { it.isNotBlank() }
                )
            )
        }
    }

    companion object {
        fun factory(context: android.content.Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val container = AppContainer.getInstance(context)
                return ProfileViewModel(
                    container.transactionRepository,
                    container.budgetRepository,
                    container.categoryRepository
                ) as T
            }
        }
    }
}
