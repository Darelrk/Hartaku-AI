package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppContainer
import com.example.data.Budget
import com.example.data.BudgetRepository
import com.example.data.Category
import com.example.data.CategoryRepository
import com.example.data.categoryIdsToJson
import com.example.data.TransactionRepository
import com.example.ui.home.currentMonthRange
import com.example.ui.home.withSpent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for [BudgetManagementScreen] and its dialogs.
 *
 * @property activeBudgets active (non-deleted) budgets from the repo, sorted as returned by DAO
 * @property expenseCategories EXPENSE-type categories, used by the C2 form's kategori dropdown
 * @property editingBudget set when the ringkas edit dialog is open; null when no dialog
 * @property showEditDialog true when the ringkas edit dialog should be visible
 * @property justArchived last archived budget — drives the snackbar undo affordance
 * @property error transient error message (e.g. validation failure); cleared by [clearError]
 * @property isLoading false once the initial flows have emitted at least once
 */
data class BudgetManagementUiState(
    val activeBudgets: List<Budget> = emptyList(),
    val deletedBudgets: List<Budget> = emptyList(),
    val expenseCategories: List<Category> = emptyList(),
    val editingBudget: Budget? = null,
    val showEditDialog: Boolean = false,
    val justArchived: Budget? = null,
    val error: String? = null,
    val isLoading: Boolean = true,
    val currentMonthIncome: Double = 0.0
)

/**
 * ViewModel for the budget management flow.
 *
 * Responsibilities:
 *  - Stream the active budgets list (via [BudgetRepository.getAllBudgets])
 *  - Stream the EXPENSE categories (via [CategoryRepository.getByTypeClass]) for the C2 dropdown
 *  - Create / update / soft-delete / restore budgets
 *  - Hold the "just archived" handle for the snackbar undo
 *
 * Design notes:
 *  - Create flow accepts a `note` (C2 form field). Edit flow is ringkas (limit + alokasi only)
 *    per Decision #7 — spent is NEVER reset on edit (Decision #8).
 *  - Soft delete stamps `deletedAt`; restore clears it. The user can recover from
 *    DeletedBudgetsScreen.
 */
class BudgetManagementViewModel(
    private val budgetRepo: BudgetRepository,
    private val categoryRepo: CategoryRepository,
    private val transactionRepo: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetManagementUiState())
    val uiState: StateFlow<BudgetManagementUiState> = _uiState.asStateFlow()

    init {
        loadActiveBudgets()
        loadDeletedBudgets()
        loadExpenseCategories()
        loadCurrentMonthIncome()
    }

    private fun loadActiveBudgets() {
        viewModelScope.launch {
            val (monthStart, monthEnd) = currentMonthRange()
            combine(
                budgetRepo.getAllBudgets(),
                transactionRepo.getTransactionsInRange(monthStart, monthEnd),
                transactionRepo.getIncomeInRange(monthStart, monthEnd)
            ) { budgets, txs, income ->
                budgets.withSpent(txs, income)
            }.collect { budgets ->
                _uiState.update { it.copy(activeBudgets = budgets, isLoading = false) }
            }
        }
    }

    private fun loadDeletedBudgets() {
        viewModelScope.launch {
            budgetRepo.getAllDeleted().collect { budgets ->
                _uiState.update { it.copy(deletedBudgets = budgets) }
            }
        }
    }

    private fun loadExpenseCategories() {
        viewModelScope.launch {
            val categories = categoryRepo.getByTypeClass("EXPENSE")
            _uiState.update { it.copy(expenseCategories = categories) }
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

    // ---- Edit dialog (ringkas: percent only) -------------------------------------

    fun openEditDialog(budget: Budget) {
        _uiState.update { it.copy(editingBudget = budget, showEditDialog = true) }
    }

    fun closeEditDialog() {
        _uiState.update { it.copy(editingBudget = null, showEditDialog = false) }
    }

    fun updateBudget(
        budget: Budget,
        newPercent: Double
    ) {
        val income = _uiState.value.currentMonthIncome
        if (newPercent <= 0.0) {
            _uiState.update { it.copy(error = "Alokasi harus > 0%") }
            return
        }
        if (income <= 0.0) {
            _uiState.update { it.copy(error = "Tambah income dulu") }
            return
        }
        val newAmount = income * (newPercent / 100.0)
        viewModelScope.launch {
            // Spent TIDAK di-reset per Decision #8 — spent adalah data riil dari transaksi.
            val updated = budget.copy(
                amount = newAmount,
                percent = newPercent
            )
            budgetRepo.updateBudget(updated)
            closeEditDialog()
        }
    }

    // ---- Create flow (full control) ------------------------------------------------

    /**
     * Create a new budget with full user control.
     *
     * @param name free-text budget name (wajib)
     * @param icon icon key
     * @param period "weekly" or "monthly"
     * @param categoryIds list of category UUIDs for this budget
     * @param percent income allocation percent (0 = manual amount mode)
     * @param manualAmount user-entered amount (null if percent-based)
     * @param note catatan (opsional)
     */
    fun createBudget(
        name: String,
        icon: String,
        period: String,
        categoryIds: List<String>,
        percent: Double,
        manualAmount: Double?,
        note: String?
    ) {
        if (name.isBlank()) {
            _uiState.update { it.copy(error = "Nama budget wajib diisi") }
            return
        }
        val amount = if (percent > 0.0 && _uiState.value.currentMonthIncome > 0.0)
            _uiState.value.currentMonthIncome * (percent / 100.0)
        else manualAmount ?: 0.0
        if (amount <= 0.0) {
            _uiState.update { it.copy(error = "Jumlah anggaran harus > Rp 0") }
            return
        }
        viewModelScope.launch {
            val budget = Budget(
                name = name.trim(),
                icon = icon.ifBlank { "wallet" },
                amount = amount,
                categoryId = null,       // new budgets use categoryIds (no FK constraint)
                categoryIds = categoryIdsToJson(categoryIds),
                period = period,
                percent = if (percent > 0.0) percent else 0.0,
                note = note?.takeIf { it.isNotBlank() }
            )
            budgetRepo.insertBudget(budget)
        }
    }

    // ---- Archive / restore / undo ------------------------------------------------

    fun archiveBudget(budget: Budget) {
        viewModelScope.launch {
            budgetRepo.softDelete(budget.id)
            // Hold the archived handle for the snackbar undo (5s window).
            _uiState.update { it.copy(justArchived = budget) }
        }
    }

    fun undoArchive() {
        val archived = _uiState.value.justArchived ?: return
        viewModelScope.launch {
            budgetRepo.restore(archived.id)
            _uiState.update { it.copy(justArchived = null) }
        }
    }

    fun clearJustArchived() {
        _uiState.update { it.copy(justArchived = null) }
    }

    fun restoreBudget(id: Int) {
        viewModelScope.launch {
            budgetRepo.restore(id)
        }
    }

    // ---- Error handling ----------------------------------------------------------

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val container = AppContainer.getInstance(context)
                return BudgetManagementViewModel(
                    container.budgetRepository,
                    container.categoryRepository,
                    container.transactionRepository
                ) as T
            }
        }
    }
}
