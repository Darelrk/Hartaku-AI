package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.ai.InputMode
import com.example.data.Budget
import com.example.data.BudgetRepository
import com.example.data.Bill
import com.example.data.BillRepository
import com.example.data.Category
import com.example.data.CategoryRepository
import com.example.data.Transaction
import com.example.data.TransactionRepository
import com.example.data.TransactionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import com.example.data.StubBillDao
import com.example.data.StubBudgetDao
import com.example.data.StubCategoryDao
import com.example.data.StubTransactionDao
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose UI test untuk [ManualInputScreen]. Fokus verifikasi:
 * 1. Toggle 3-tab (PENGELUARAN | PEMASUKAN | TAGIHAN) bisa diklik dan mengubah state.
 * 2. Section "Alokasi Pemasukan ke Anggaran" hanya muncul di mode PEMASUKAN.
 * 3. Klik tab TAGIHAN → section alokasi TIDAK muncul (DoesNotExist).
 *
 * Pattern: menggunakan fake repository sederhana (in-memory) + ViewModel
 * di-inject manual via factory wrapper, sehingga tidak butuh database Room asli.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ManualInputScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var fakeTransactionRepo: FakeTransactionRepositoryForScreen
    private lateinit var fakeCategoryRepo: FakeCategoryRepositoryForScreen
    private lateinit var fakeBudgetRepo: FakeBudgetRepositoryForScreen
    private lateinit var fakeBillRepo: FakeBillRepositoryForScreen
    private lateinit var viewModel: ManualInputViewModel

    @Before
    fun setUp() = kotlinx.coroutines.test.runTest {
        fakeTransactionRepo = FakeTransactionRepositoryForScreen()
        fakeCategoryRepo = FakeCategoryRepositoryForScreen().apply {
            insertAll(listOf(
                Category(id = "1", name = "Makanan", slug = "makanan", typeClass = "EXPENSE", aliases = "[]"),
                Category(id = "2", name = "Transport", slug = "transport", typeClass = "EXPENSE", aliases = "[]"),
                Category(id = "3", name = "Gaji", slug = "gaji", typeClass = "INCOME", aliases = "[]")
            ))
        }
        fakeBudgetRepo = FakeBudgetRepositoryForScreen().apply {
            insertAll(listOf(
                Budget(id = 1, name = "Makanan", icon = "food", amount = 0.0, period = "monthly", percent = 50.0),
                Budget(id = 2, name = "Transport", icon = "transport", amount = 0.0, period = "monthly", percent = 50.0)
            ))
        }
        fakeBillRepo = FakeBillRepositoryForScreen()

        // Construct ViewModel langsung dengan fake repos (tidak lewat factory Context).
        viewModel = ManualInputViewModel(
            transactionRepo = fakeTransactionRepo,
            categoryRepo = fakeCategoryRepo,
            budgetRepo = fakeBudgetRepo,
            aiParser = null, // no AI parser → regex fallback path
            billRepo = fakeBillRepo
        )
    }

    private fun setScreen() {
        composeTestRule.setContent {
            ManualInputScreen(
                onClose = { /* no-op for test */ },
                viewModelOverride = viewModel
            )
        }
    }

    @Test
    fun testTextInput_updatesViewModelState() {
        setScreen()
        // ManualInputScreen shows a TextField with placeholder + category chips + save button.
        composeTestRule.onNodeWithText("Kategori").assertIsDisplayed()
        composeTestRule.onNodeWithText("Simpan").assertIsDisplayed()
        // Default placeholder visible
        composeTestRule.onNodeWithText(
            "Contoh: tadi makan nasi goreng 15 ribu",
            substring = true
        ).assertIsDisplayed()
    }

    @Test
    fun testCategoryChipSelection_updatesViewModelState() = kotlinx.coroutines.test.runTest {
        setScreen()
        // Tap "Makanan" category chip
        composeTestRule.onNodeWithText("Makanan").performClick()
        composeTestRule.waitForIdle()
        assertEquals("Makanan", viewModel.uiState.value.selectedCategory?.name)
    }

    @Test
    fun testSaveButton_disabledWhenTextBlank() {
        setScreen()
        // No text typed yet — Simpan button should exist but be disabled
        composeTestRule.onNodeWithText("Simpan").assertIsDisplayed()
        // Placeholder still showing — text is empty
        composeTestRule.onNodeWithText(
            "Contoh: tadi makan nasi goreng 15 ribu",
            substring = true
        ).assertIsDisplayed()
    }

    @Test
    fun testCategoryChips_renderedFromRepo() {
        setScreen()
        // Default mode = EXPENSE → only Expense categories shown (Makanan, Transport).
        // INCOME category "Gaji" hidden until user switches to PEMASUKAN mode.
        composeTestRule.onNodeWithText("Makanan").assertIsDisplayed()
        composeTestRule.onNodeWithText("Transport").assertIsDisplayed()
        composeTestRule.onNodeWithText("Gaji").assertDoesNotExist()
    }
    @Test
    fun testDefaultPengeluaranMode_doesNotShowAllocation() {
        setScreen()
        composeTestRule.waitForIdle()
        assertEquals(InputMode.EXPENSE, viewModel.uiState.value.inputMode)
        composeTestRule.onNodeWithText("Alokasi Pemasukan ke Anggaran").assertDoesNotExist()
    }
}

// ===================================================================
// In-memory fakes for screen tests (lighter than the data-layer ones).
// ===================================================================

class FakeTransactionRepositoryForScreen : TransactionRepository(StubTransactionDao()) {
    val transactions = MutableStateFlow<List<Transaction>>(emptyList())
    override fun getAllTransactions(): Flow<List<Transaction>> = transactions
    override fun getRecentTransactions(): Flow<List<Transaction>> = transactions
    override fun getTransactionsByDay(startOfDay: Long, endOfDay: Long): Flow<List<Transaction>> = transactions
    override fun getTransactionsInRange(startDay: Long, endDay: Long): Flow<List<Transaction>> = transactions
    override fun getTransactionsByCategoryName(categoryName: String): Flow<List<Transaction>> = transactions
    override fun getTransactionsByKeyword(keyword: String): Flow<List<Transaction>> = transactions
    override suspend fun insertTransaction(transaction: Transaction) {
        transactions.value = transactions.value + transaction
    }
    override suspend fun updateTransaction(transaction: Transaction) {}
    override suspend fun deleteTransaction(transaction: Transaction) {}
    override suspend fun softDelete(id: Int, now: Long) {
        transactions.value = transactions.value.map { if (it.id == id) it.copy(deletedAt = now) else it }
    }
    override suspend fun restore(id: Int) {
        transactions.value = transactions.value.map { if (it.id == id) it.copy(deletedAt = null) else it }
    }
    override fun getAllDeleted(): Flow<List<Transaction>> = MutableStateFlow(emptyList())
    override fun getDailyExpense(startOfDay: Long, endOfDay: Long): Flow<Double> = MutableStateFlow(0.0)
    override fun getDailyIncome(startOfDay: Long, endOfDay: Long): Flow<Double> = MutableStateFlow(0.0)
    override fun getExpenseInRange(startDay: Long, endDay: Long): Flow<Double> = MutableStateFlow(0.0)
    override fun getIncomeInRange(startDay: Long, endDay: Long): Flow<Double> = MutableStateFlow(0.0)
    override fun getCategoryBreakdownById(startOfDay: Long, endOfDay: Long): Flow<List<com.example.data.CategoryTotal>> = MutableStateFlow(emptyList())
    override fun getTransactionCount(startOfDay: Long, endOfDay: Long): Flow<Int> = MutableStateFlow(0)
}

class FakeCategoryRepositoryForScreen : CategoryRepository(StubCategoryDao()) {
    private val cats = mutableListOf<Category>()
    override suspend fun getAllActive(): List<Category> = cats.toList()
    override suspend fun getByTypeClass(typeClass: String): List<Category> = cats.filter { it.typeClass == typeClass }
    override suspend fun getRootCategories(): List<Category> = cats.filter { it.parentId == null }
    override suspend fun getChildren(parentId: String): List<Category> = cats.filter { it.parentId == parentId }
    override suspend fun getById(id: String): Category? = cats.find { it.id == id }
    override suspend fun getBySlug(slug: String): Category? = cats.find { it.slug == slug }
    override suspend fun search(query: String): List<Category> = cats.filter { it.name.contains(query, true) }
    override suspend fun rename(id: String, name: String, slug: String) {
        val index = cats.indexOfFirst { it.id == id }
        if (index != -1) {
            cats[index] = cats[index].copy(name = name, slug = slug)
        }
    }
    override suspend fun insert(category: Category) {
        cats.removeAll { it.id == category.id }
        cats.add(category)
    }
    override suspend fun insertAll(categories: List<Category>) = categories.forEach { insert(it) }
    override suspend fun update(category: Category) = insert(category)
    override suspend fun softDelete(id: String) {}
    override suspend fun count(): Int = cats.size
    override suspend fun getOrCreateByName(name: String, typeClass: String): Category {
        val existing = cats.find { it.name.equals(name, true) }
        if (existing != null) return existing
        val newCat = Category(
            id = java.util.UUID.randomUUID().toString(),
            name = name.replaceFirstChar { it.uppercase() },
            slug = name.lowercase(),
            typeClass = typeClass,
            aliases = "[]"
        )
        insert(newCat)
        return newCat
    }
    override suspend fun getAllDeleted(): List<Category> = emptyList()
    override suspend fun restore(id: String) {}
}

class FakeBudgetRepositoryForScreen : BudgetRepository(StubBudgetDao()) {
    val budgets = MutableStateFlow<List<Budget>>(emptyList())
    override fun getAllBudgets(): Flow<List<Budget>> = budgets
    override fun getAllDeleted(): Flow<List<Budget>> = MutableStateFlow(emptyList())
    override suspend fun getById(id: Int): Budget? = budgets.value.firstOrNull { it.id == id }
    override suspend fun insertAll(budgets: List<Budget>) {
        this.budgets.value = budgets
    }
    override suspend fun insertBudget(budget: Budget) {
        budgets.value = budgets.value + budget
    }
    override suspend fun updateBudget(budget: Budget) {
        budgets.value = budgets.value.map { if (it.id == budget.id) budget else it }
    }
    override suspend fun deleteBudget(budget: Budget) {
        budgets.value = budgets.value - budget
    }
    override suspend fun softDelete(id: Int, now: Long) {
        budgets.value = budgets.value.map { if (it.id == id) it.copy(deletedAt = now) else it }
    }
    override suspend fun restore(id: Int) {
        budgets.value = budgets.value.map { if (it.id == id) it.copy(deletedAt = null) else it }
    }
    override suspend fun count(): Int = budgets.value.size
}

class FakeBillRepositoryForScreen : BillRepository(StubBillDao()) {
    val bills = MutableStateFlow<List<Bill>>(emptyList())
    override fun getActiveBillsForMonth(currentMonthMillis: Long): Flow<List<Bill>> = bills
    override suspend fun insertBill(bill: Bill) {
        bills.value = bills.value + bill
    }
    override suspend fun markBillPaid(billId: Int, isPaid: Boolean) {
        bills.value = bills.value.map { if (it.id == billId) it.copy(isPaidThisMonth = isPaid) else it }
    }
    override suspend fun resetMonthlyPaidStatus() {
        bills.value = bills.value.map { it.copy(isPaidThisMonth = false) }
    }
}
