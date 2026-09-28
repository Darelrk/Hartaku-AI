package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai.InputMode
import com.example.ai.TransactionAiParser
import com.example.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ManualInputUiState(
    val text: String = "",
    val selectedCategory: Category? = null,
    val categories: List<Category> = emptyList(),
    val isProcessing: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
    val parseSource: String? = null,    // "ai" | "regex" — untuk debug
    val inputMode: InputMode = InputMode.EXPENSE,
    val useDefaultAllocation: Boolean = true, // Gunakan Rencana Persen Default vs Kustom
    val customAllocations: Map<String, Double> = emptyMap(), // budget name to percentage map
    val budgetCategories: List<Budget> = emptyList(), // active budgets (for both income allocation + manual assignment)
    val selectedBudgetId: Int? = null // manual expense → budget assignment (null = "Tanpa Budget", no attribution)
)

class ManualInputViewModel(
    private val transactionRepo: TransactionRepository,
    private val categoryRepo: CategoryRepository,
    private val budgetRepo: BudgetRepository,
    private val aiParser: TransactionAiParser?,
    private val billRepo: BillRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManualInputUiState())
    val uiState: StateFlow<ManualInputUiState> = _uiState.asStateFlow()

    init {
        loadCategories()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            val mode = _uiState.value.inputMode
            val typeClass = when (mode) {
                InputMode.INCOME -> "INCOME"
                InputMode.EXPENSE -> "EXPENSE"
                InputMode.BILL -> "EXPENSE" // Bills are expenses
            }
            val cats = categoryRepo.getByTypeClass(typeClass)
            _uiState.update { it.copy(categories = cats) }
        }
        viewModelScope.launch {
            budgetRepo.getAllBudgets().collect { budgets ->
                _uiState.update { it.copy(budgetCategories = budgets) }
            }
        }
    }

    fun setInputMode(mode: InputMode) {
        _uiState.update { it.copy(inputMode = mode, selectedCategory = null) }
        loadCategories()
    }

    fun setUseDefaultAllocation(useDefault: Boolean) {
        _uiState.update { it.copy(useDefaultAllocation = useDefault) }
    }

    fun updateCustomAllocation(category: String, percent: Double) {
        _uiState.update {
            val updated = it.customAllocations.toMutableMap().apply {
                put(category, percent)
            }
            it.copy(customAllocations = updated)
        }
    }

    fun updateText(text: String) {
        _uiState.update { it.copy(text = text, error = null) }
    }

    fun selectCategory(category: Category) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    /**
     * Set the budget that an EXPENSE transaction should be attributed to.
     * Pass `null` to mark the transaction as "Tanpa Budget" (no budget attribution).
     * Only meaningful for [InputMode.EXPENSE] — income auto-allocates and bills have
     * their own attribution model.
     */
    fun setSelectedBudgetId(id: Int?) {
        _uiState.update { it.copy(selectedBudgetId = id) }
    }

    fun saveTransaction() {
        if (_uiState.value.isProcessing) return  // ponytail: prevent double-submit race
        if (_uiState.value.text.isBlank()) return
        _uiState.update { it.copy(isProcessing = true, error = null) }
        val state = _uiState.value

        viewModelScope.launch {
            try {
                val parseSource: String
                val allCategories = categoryRepo.getAllActive()
                val mode = state.inputMode

                // List transaksi yang akan di-save
                val txsToSave = mutableListOf<Transaction>()
                // List bills yang akan di-save
                val billsToSave = mutableListOf<Bill>()

                if (aiParser != null) {
                    val result = aiParser.parse(state.text, allCategories, mode)
                    parseSource = result.source

                    // === Transactions ===
                    result.transactions.forEach { parsed ->
                        val isIncomeType = parsed.type == "income"
                        val typeClass = if (isIncomeType) "INCOME" else "EXPENSE"
                        val matchedCat = state.selectedCategory
                            ?: if (parsed.category.isNotBlank()) categoryRepo.getOrCreateByName(parsed.category, typeClass) else allCategories.find { it.slug == "lainnya" }

                        val tx = Transaction(
                            amount = parsed.amount,
                            description = parsed.description,
                            category = matchedCat?.name ?: parsed.category,
                            categoryId = matchedCat?.id,
                            // Only EXPENSE transactions get a manual budgetId assignment.
                            // INCOME auto-allocates via percent → budget (see below), so
                            // budgetId is intentionally null for income.
                            budgetId = if (isIncomeType) null else state.selectedBudgetId,
                            type = if (isIncomeType) TransactionType.INCOME else TransactionType.EXPENSE,
                            timestamp = System.currentTimeMillis()
                        )
                        txsToSave.add(tx)
                    }

                    // === Bills ===
                    result.bills.forEach { parsed ->
                        val catForBill = if (parsed.category.isNotBlank()) {
                            categoryRepo.getOrCreateByName(parsed.category, "EXPENSE")
                        } else allCategories.find { it.slug == "lainnya" }

                        val bill = Bill(
                            name = parsed.name,
                            amount = parsed.amount,
                            dueDate = parsed.dueDate,
                            categoryId = catForBill?.id,
                            notifyBeforeDays = parsed.notifyBeforeDays,
                            recurrenceMode = when (parsed.recurrenceMode) {
                                "FOREVER" -> RecurrenceMode.FOREVER
                                "CUSTOM_RANGE" -> RecurrenceMode.CUSTOM_RANGE
                                else -> RecurrenceMode.ONCE
                            },
                            rangeEndMonthMillis = parsed.rangeEndMonthMillis
                        )
                        billsToSave.add(bill)
                    }
                } else {
                    parseSource = "regex"
                    val amount = extractAmount(state.text)
                    val desc = extractDescription(state.text)
                    val matchedCat = state.selectedCategory
                        ?: if (mode == InputMode.INCOME) allCategories.find { it.typeClass == "INCOME" } ?: allCategories.find { it.slug == "lainnya" }
                           else allCategories.find { it.slug == "lainnya" }

                    val tx = Transaction(
                        amount = amount,
                        description = desc,
                        category = matchedCat?.name ?: "Lainnya",
                        categoryId = matchedCat?.id,
                        // Only EXPENSE transactions get a manual budgetId assignment.
                        // INCOME auto-allocates via percent → budget (see below), so
                        // budgetId is intentionally null for income.
                        budgetId = if (mode == InputMode.INCOME) null else state.selectedBudgetId,
                        type = if (mode == InputMode.INCOME) TransactionType.INCOME else TransactionType.EXPENSE,
                        timestamp = System.currentTimeMillis()
                    )
                    if (mode != InputMode.BILL) {
                        txsToSave.add(tx)
                    }
                }

                // Simpan transaksi (skip di BILL mode)
                txsToSave.forEach { transactionRepo.insertTransaction(it) }

                // Simpan bills (skip di INCOME mode)
                billsToSave.forEach { billRepo.insertBill(it) }

                // Income allocation: hanya jika ada income transactions yang di-save.
                // Empty budgetCategories is handled gracefully: the forEach below is a no-op
                // when the user has no budgets yet, so the transaction is saved without any
                // allocation. The UI (IncomeAllocationSection) renders an A3 CTA when empty
                // to nudge the user to set up budgets via BudgetManagementScreen, but the
                // save flow is never blocked by missing budgets.
                if (mode == InputMode.INCOME && txsToSave.isNotEmpty()) {
                    val totalIncomeAmount = txsToSave.sumOf { it.amount }
                    if (totalIncomeAmount > 0.0) {
                        state.budgetCategories.forEach { budget ->
                            val percent = if (state.useDefaultAllocation) {
                                budget.percent
                            } else {
                                state.customAllocations[budget.name] ?: budget.percent
                            }
                            if (percent > 0.0) {
                                val allocatedAmount = totalIncomeAmount * (percent / 100.0)
                                val updatedBudget = budget.copy(amount = allocatedAmount)
                                budgetRepo.updateBudget(updatedBudget)
                            }
                        }
                    }
                }

                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        isSaved = true,
                        text = "",
                        selectedCategory = null,
                        selectedBudgetId = null,
                        parseSource = parseSource
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isProcessing = false, error = e.message ?: "Gagal menyimpan")
                }
            }
        }
    }



    fun resetSaved() {
        _uiState.update { it.copy(isSaved = false) }
    }

    private fun extractAmount(text: String): Double {
        val numberPattern = Regex("""(\d[\d.,]*)""")
        val match = numberPattern.find(text)
        return match?.value?.replace(".", "")?.replace(",", "")?.toDoubleOrNull() ?: 0.0
    }

    private fun extractDescription(text: String): String {
        return text.replace(Regex("""\d[\d.,]*"""), "").trim()
    }

    companion object {
        fun factory(context: android.content.Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val container = AppContainer.getInstance(context)
                return ManualInputViewModel(
                    container.transactionRepository,
                    container.categoryRepository,
                    container.budgetRepository,
                    container.transactionAiParser,
                    container.billRepository
                ) as T
            }
        }
    }
}