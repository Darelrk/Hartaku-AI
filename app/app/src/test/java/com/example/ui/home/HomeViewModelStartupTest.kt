package com.example.ui.home

import com.example.MainDispatcherRule
import com.example.data.Category
import com.example.data.FakeBudgetRepository
import com.example.data.FakeCategoryRepository
import com.example.data.FakeTransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * HomeFeed meminta HomeViewModel saat komposisi pertama, jadi konstruktor HomeViewModel
 * jalan di main thread. Kalau init menunggu query kategori secara blocking, frame pertama
 * tertahan sampai database terbuka (terukur Davey 3226ms / 183 frame terlewat).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelStartupTest {

    // StandardTestDispatcher, bukan UnconfinedTestDispatcher: launch baru dijalankan saat
    // scheduler di-advance, sehingga "blocking vs tidak" bisa dibedakan.
    private val scheduler = TestCoroutineScheduler()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher(scheduler))

    @Test
    fun construction_doesNotWaitForTheCategoryQuery() = runTest(scheduler) {
        val categoryRepo = FakeCategoryRepository()
        categoryRepo.insertAll(
            listOf(
                Category(id = "c1", name = "Makanan", slug = "makanan", typeClass = "EXPENSE"),
                Category(id = "c2", name = "Transport", slug = "transport", typeClass = "EXPENSE")
            )
        )

        val viewModel = HomeViewModel(
            transactionRepo = FakeTransactionRepository(),
            budgetRepo = FakeBudgetRepository(),
            categoryRepo = categoryRepo,
            insightGenerator = null,
        )

        // Init non-blocking: query belum sempat jalan, state masih kosong.
        assertTrue(viewModel.categories.value.isEmpty())

        advanceUntilIdle()
        assertEquals(2, viewModel.categories.value.size)
    }
}
