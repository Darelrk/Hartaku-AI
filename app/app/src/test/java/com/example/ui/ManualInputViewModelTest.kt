package com.example.ui

import com.example.ai.ChatResponse
import com.example.MainDispatcherRule
import com.example.ai.FakeChatClient
import com.example.ai.TransactionAiParser
import com.example.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ManualInputViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeTransactionRepo: FakeTransactionRepository
    private lateinit var fakeCategoryRepo: FakeCategoryRepository
    private lateinit var fakeChatClient: FakeChatClient
    private lateinit var parser: TransactionAiParser
    private lateinit var viewModel: ManualInputViewModel

    private val sampleCategories = listOf(
        Category(id = "101", name = "Makanan", slug = "makanan", typeClass = "EXPENSE", aliases = "[\"makan\", \"bakso\", \"kopi\"]"),
        Category(id = "102", name = "Gaji", slug = "gaji", typeClass = "INCOME", aliases = "[\"gaji\", \"gajian\"]"),
        Category(id = "103", name = "Transport", slug = "transport", typeClass = "EXPENSE", aliases = "[\"bensin\", \"ojek\"]"),
        Category(id = "104", name = "Lainnya", slug = "lainnya", typeClass = "EXPENSE", aliases = "[]")
    )

    @Before
    fun setUp() = runTest {
        fakeTransactionRepo = FakeTransactionRepository()
        fakeCategoryRepo = FakeCategoryRepository()
        fakeCategoryRepo.insertAll(sampleCategories)

        fakeChatClient = FakeChatClient()
        parser = TransactionAiParser(fakeChatClient)

        val fakeBudgetRepo = com.example.data.FakeBudgetRepository()
        val fakeBillRepo = com.example.data.FakeBillRepository()
        viewModel = ManualInputViewModel(
            transactionRepo = fakeTransactionRepo,
            categoryRepo = fakeCategoryRepo,
            aiParser = parser,
            budgetRepo = fakeBudgetRepo,
            billRepo = fakeBillRepo
        )
    }

    @Test
    fun testInit_loadsExpenseCategories() = runTest {
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(3, state.categories.size) // Makanan, Transport, Lainnya (excluding INCOME Gaji)
        assertTrue(state.categories.any { it.name == "Makanan" })
        assertTrue(state.categories.any { it.name == "Transport" })
        assertTrue(state.categories.any { it.name == "Lainnya" })
    }

    @Test
    fun testUpdateText_updatesState() = runTest {
        viewModel.updateText("makan bakso 15rb")
        assertEquals("makan bakso 15rb", viewModel.uiState.value.text)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun testSelectCategory_updatesState() = runTest {
        val cat = sampleCategories[0] // Makanan
        viewModel.selectCategory(cat)
        assertEquals(cat, viewModel.uiState.value.selectedCategory)
    }

    @Test
    fun testSaveTransaction_withRegexFallback() = runTest {
        fakeChatClient.responseToReturn = Result.failure(Exception("AI Offline"))
        viewModel.updateText("kopi 20k")

        viewModel.saveTransaction()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isSaved)
        assertEquals("", state.text)
        assertEquals("regex", state.parseSource)

        val txs = fakeTransactionRepo.transactions.value
        assertEquals(1, txs.size)
        val tx = txs[0]
        assertEquals(20000.0, tx.amount, 0.1)
        assertEquals("kopi", tx.description) // Best practice: "k" multiplier di-strip dari description
        assertEquals("Lainnya", tx.category) // Default to "Lainnya" since local alias matching is removed
        assertEquals(TransactionType.EXPENSE, tx.type)
    }

    @Test
    fun testSaveTransaction_withSelectedCategoryOverrides() = runTest {
        fakeChatClient.responseToReturn = Result.failure(Exception("AI Offline"))
        viewModel.updateText("kopi 20k")
        // Manually select Transport category to override Food alias matching
        viewModel.selectCategory(sampleCategories[2]) // Transport

        viewModel.saveTransaction()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isSaved)
        assertNull(state.selectedCategory)

        val txs = fakeTransactionRepo.transactions.value
        assertEquals(1, txs.size)
        val tx = txs[0]
        assertEquals(20000.0, tx.amount, 0.1)
        assertEquals("Transport", tx.category) // Transport was manually selected
    }

    @Test
    fun testSaveTransaction_autoCreatesCategory() = runTest {
        // AI returns unmatched category "Kesehatan"
        val mockJsonResponse = """
            [{"type": "expense", "category": "Kesehatan", "amount": 75000, "description": "Obat flu"}]
        """.trimIndent()
        fakeChatClient.responseToReturn = Result.success(
            ChatResponse(
                content = mockJsonResponse,
                finishReason = "stop",
                promptTokens = 100,
                completionTokens = 30
            )
        )

        viewModel.updateText("beli obat flu 75000")
        viewModel.saveTransaction()
        advanceUntilIdle()

        val txs = fakeTransactionRepo.transactions.value
        assertEquals(1, txs.size)
        val tx = txs[0]
        assertEquals("Kesehatan", tx.category)
        
        // Verify category was created in the category repository
        val createdCat = fakeCategoryRepo.getBySlug("kesehatan")
        assertNotNull(createdCat)
        assertEquals("Kesehatan", createdCat?.name)
        assertEquals("EXPENSE", createdCat?.typeClass)
    }

    @Test
    fun testCategoryResolutionHierarchy() = runTest {
        // AI returns parsed.category = "Transport", but the description matches "Makanan" alias ("kopi")
        val mockJsonResponse = """
            [{"type": "expense", "category": "Transport", "amount": 25000, "description": "kopi"}]
        """.trimIndent()
        fakeChatClient.responseToReturn = Result.success(
            ChatResponse(
                content = mockJsonResponse,
                finishReason = "stop",
                promptTokens = 100,
                completionTokens = 30
            )
        )

        viewModel.updateText("kopi 25000")
        viewModel.saveTransaction()
        advanceUntilIdle()

        val txs = fakeTransactionRepo.transactions.value
        assertEquals(1, txs.size)
        val tx = txs[0]
        // Case 1: No manual selection, AI returned "Transport". AI Classified Category name match should override alias match.
        assertEquals("Transport", tx.category)
    }
}
