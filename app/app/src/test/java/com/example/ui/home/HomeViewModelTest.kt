package com.example.ui.home

import com.example.MainDispatcherRule
import com.example.BuildConfig
import com.example.ai.AiInsightGenerator
import com.example.ai.FakeChatClient
import com.example.ai.LocalRuleBasedChat
import com.example.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeTransactionRepo: FakeTransactionRepository
    private lateinit var fakeBudgetRepo: FakeBudgetRepository
    private lateinit var fakeCategoryRepo: FakeCategoryRepository
    private lateinit var fakeChatClient: FakeChatClient
    private lateinit var generator: AiInsightGenerator
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() = runTest {
        fakeTransactionRepo = FakeTransactionRepository()
        fakeBudgetRepo = FakeBudgetRepository()
        fakeCategoryRepo = FakeCategoryRepository()
        fakeChatClient = FakeChatClient()
        generator = AiInsightGenerator(fakeChatClient)

        // Seed some basic categories
        fakeCategoryRepo.insertAll(listOf(
            Category(id = "c1", name = "Makanan", slug = "makanan", typeClass = "EXPENSE"),
            Category(id = "c2", name = "Transport", slug = "transport", typeClass = "EXPENSE")
        ))

        viewModel = HomeViewModel(
            transactionRepo = fakeTransactionRepo,
            budgetRepo = fakeBudgetRepo,
            categoryRepo = fakeCategoryRepo,
            insightGenerator = generator,
        )
    }

    @Test
    fun testInit_loadsActiveCategories() = runTest {
        advanceUntilIdle()
        val cats = viewModel.categories.value
        assertEquals(2, cats.size)
        assertTrue(cats.any { it.name == "Makanan" })
    }

    @Test
    fun dayData_reusesStateFlowForSameOffset() = runTest {
        val day = viewModel.dayData(0)
        advanceUntilIdle()

        assertSame(day, viewModel.dayData(0))
    }

    @Test
    fun dayData_doesNotRepeatChartRangeQueryAcrossOffsets() = runTest {
        val minChartRangeMillis = 13L * 24 * 60 * 60 * 1_000
        val maxChartRangeMillis = 15L * 24 * 60 * 60 * 1_000
        fun chartRangeQueryCount() = fakeTransactionRepo.transactionRangeCalls.count { (start, end) ->
            end - start in minChartRangeMillis..maxChartRangeMillis
        }

        val chartRangeQueriesBefore = chartRangeQueryCount()
        assertEquals("ViewModel harus membuka satu query chart bersama", 1, chartRangeQueriesBefore)

        for (offset in -2..2) viewModel.dayData(offset)
        advanceUntilIdle()

        assertEquals(
            "memuat beberapa hari tidak boleh menambah query chart 14 hari",
            chartRangeQueriesBefore,
            chartRangeQueryCount()
        )
    }

    @Test
    fun twoWeekExpense_emitsLocalFourteenDayBuckets() = runTest {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        fakeTransactionRepo.insertTransaction(Transaction(
            id = 1,
            amount = 15_000.0,
            description = "Makan siang",
            category = "Makanan",
            type = TransactionType.EXPENSE,
            timestamp = today.timeInMillis
        ))

        val result = viewModel.twoWeekExpense.first { it.daily.size == 14 }

        assertEquals(14, result.daily.size)
        assertEquals(15_000.0, result.daily.last().total, 0.001)
    }

    @Test
    fun testDayData_initialStateLoading() = runTest {
        val flow = viewModel.dayData(0)
        // Before running, state might be loading or completed fast due to Fake.
        advanceUntilIdle()
        val state = flow.value
        assertFalse(state.loading)
        assertEquals(0.0, state.totalSpending, 0.1)
        assertEquals(0.0, state.totalIncome, 0.1)
    }

    @Test
    fun testDayData_withTransactionsLoadsTotals() = runTest {
        val today = Calendar.getInstance()
        val todayMs = today.timeInMillis

        fakeTransactionRepo.insertTransaction(Transaction(
            id = 1,
            amount = 15000.0,
            description = "Bakso",
            category = "Makanan",
            type = TransactionType.EXPENSE,
            timestamp = todayMs
        ))

        fakeTransactionRepo.insertTransaction(Transaction(
            id = 2,
            amount = 50000.0,
            description = "Bonus",
            category = "Gaji",
            type = TransactionType.INCOME,
            timestamp = todayMs
        ))

        // Get day 0 (today)
        val flow = viewModel.dayData(0)
        advanceUntilIdle()

        val state = flow.value
        assertEquals(15000.0, state.totalSpending, 0.1)
        assertEquals(50000.0, state.totalIncome, 0.1)
        assertEquals(35000.0, state.balance, 0.1)
        assertEquals(2, state.transactionCount)
        assertEquals(2, state.transactions.size)
    }

    @Test
    fun testDeleteTransaction() = runTest {
        val today = Calendar.getInstance().timeInMillis
        val tx = Transaction(
            id = 1,
            amount = 15000.0,
            description = "Bakso",
            category = "Makanan",
            type = TransactionType.EXPENSE,
            timestamp = today
        )
        fakeTransactionRepo.insertTransaction(tx)

        val flow = viewModel.dayData(0)
        advanceUntilIdle()
        assertEquals(1, flow.value.transactions.size)

        viewModel.deleteTransaction(tx)
        advanceUntilIdle()
        assertEquals(0, flow.value.transactions.size)
    }

    @Test
    fun testUpdateTransaction() = runTest {
        val today = Calendar.getInstance().timeInMillis
        val tx = Transaction(
            id = 1,
            amount = 15000.0,
            description = "Bakso",
            category = "Makanan",
            type = TransactionType.EXPENSE,
            timestamp = today
        )
        fakeTransactionRepo.insertTransaction(tx)

        val flow = viewModel.dayData(0)
        advanceUntilIdle()
        assertEquals(15000.0, flow.value.totalSpending, 0.1)

        val updated = tx.copy(amount = 20000.0)
        viewModel.updateTransaction(updated)
        advanceUntilIdle()
        assertEquals(20000.0, flow.value.totalSpending, 0.1)
    }

    @Test
    fun testEnsureMockData_seedsOnlyWhenEmpty() = runTest {
        val expectedCount = if (BuildConfig.DEBUG) 12 else 0

        viewModel.ensureMockData()
        advanceUntilIdle()
        assertEquals(expectedCount, fakeTransactionRepo.transactions.value.size)

        viewModel.ensureMockData()
        advanceUntilIdle()
        assertEquals(expectedCount, fakeTransactionRepo.transactions.value.size)
    }

    @Test
    fun testEnsureMockData_seededTransactionsCarryCategoryId() = runTest {
        viewModel.ensureMockData()
        advanceUntilIdle()

        // Breakdown Home LEFT JOIN categories lewat categoryId; null -> "Uncategorized".
        assertTrue(fakeTransactionRepo.transactions.value.all { it.categoryId != null })
    }

    @Test
    fun testSendChatMessage_producesAssistantReply() = runTest {
        // Tanpa API key jalur chat jatuh ke LocalRuleBasedChat; balasannya harus muncul.
        val chatViewModel = HomeViewModel(
            transactionRepo = fakeTransactionRepo,
            budgetRepo = fakeBudgetRepo,
            categoryRepo = fakeCategoryRepo,
            insightGenerator = null,
            localRuleBasedChat = LocalRuleBasedChat(fakeTransactionRepo),
        )
        val history = chatViewModel.getChatHistory(0)
        val collector = launch { history.collect {} }

        chatViewModel.sendChatMessage(0, "berapa pengeluaran hari ini")
        // LocalRuleBasedChat pindah ke Dispatchers.Default (thread nyata) yang tidak
        // dijangkau advanceUntilIdle(), jadi tiap iterasi: majukan scheduler dulu
        // (supaya collector dan emisi StateFlow jalan) lalu tunggu thread nyata.
        val deadline = System.currentTimeMillis() + 5_000
        while (history.value.size < 2 && System.currentTimeMillis() < deadline) {
            advanceUntilIdle()
            Thread.sleep(20)
        }

        val messages = history.value
        assertEquals(listOf("user", "assistant"), messages.map { it.role })
        assertTrue("balasan tidak boleh kosong", messages.last().content.isNotBlank())
        collector.cancel()
    }
}
