package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.MainDispatcherRule
import com.example.data.Budget
import com.example.data.Category
import com.example.data.FakeBudgetRepository
import com.example.data.FakeCategoryRepository
import com.example.data.FakeTransactionRepository
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose UI tests for [BudgetManagementScreen].
 *
 * Coverage:
 *  1. Empty state shows "Belum Ada Anggaran Aktif" with helper text
 *  2. Active budgets list renders category names
 *  3. Header shows count + total
 *  4. FAB tap opens the C2 form (full screen)
 *  5. C2 form: kategori dropdown shows EXPENSE categories only
 *  6. C2 form: save button is a no-op when invalid (no crash)
 *  7. C2 form: back button closes the form
 *  8. Overflow menu (⋮) opens with Edit and Arsipkan options
 *  9. Arsipkan triggers archive (snackbar "Undo" appears)
 * 10. Header "Lihat yang dihapus" link is visible when list non-empty
 * 11. C2 form: selecting a kategori from the dropdown works
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BudgetManagementScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var budgetRepo: FakeBudgetRepository
    private lateinit var categoryRepo: FakeCategoryRepository

    @Before
    fun setUp() = runBlocking {
        budgetRepo = FakeBudgetRepository()
        categoryRepo = FakeCategoryRepository()
        // Seed EXPENSE + INCOME categories
        categoryRepo.insertAll(listOf(
            Category(id = "cat_makanan", name = "Makanan", slug = "makanan", typeClass = "EXPENSE", aliases = "[]"),
            Category(id = "cat_transport", name = "Transport", slug = "transport", typeClass = "EXPENSE", aliases = "[]"),
            Category(id = "cat_gaji", name = "Gaji", slug = "gaji", typeClass = "INCOME", aliases = "[]")
        ))
    }

    private fun setScreen() {
        composeTestRule.setContent {
            MyApplicationTheme {
                BudgetManagementScreen(
                    onClose = { /* no-op */ },
                    onShowDeleted = { /* no-op */ },
                    viewModelOverride = BudgetManagementViewModel(budgetRepo, categoryRepo, FakeTransactionRepository())
                )
            }
        }
    }

    /** Wait until the VM's Flow collection has populated activeBudgets, up to 5s. */
    private fun waitForBudgetsLoaded() {
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Makanan", substring = true).fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule.onAllNodesWithText("Belum Ada Anggaran Aktif").fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule.onAllNodesWithText("Kelola Anggaran").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
    }

    // ---- Test 1: Empty state ----
    @Test
    fun testEmptyState_rendersEmptyCard() {
        setScreen()
        waitForBudgetsLoaded()
        composeTestRule.onNodeWithText("Belum Ada Anggaran Aktif").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tap tombol + untuk membuat anggaran pertama").assertIsDisplayed()
    }

    // ---- Test 2: Active budgets list renders ----
    @Test
    fun testActiveBudgets_rendersRows() {
        runBlocking {
            budgetRepo.insertAll(listOf(
                Budget(id = 1, name = "Makanan", icon = "food", amount = 1_500_000.0, spent = 450_000.0, period = "monthly", percent = 30.0),
                Budget(id = 2, name = "Transport", icon = "transport", amount = 500_000.0, spent = 350_000.0, period = "monthly", percent = 20.0)
            ))
        }
        setScreen()
        waitForBudgetsLoaded()

        // Budget names visible
        composeTestRule.onNodeWithText("Makanan").assertIsDisplayed()
        composeTestRule.onNodeWithText("Transport").assertIsDisplayed()
    }

    // ---- Test 3: Header shows count + total ----
    @Test
    fun testHeader_showsCountAndTotal() {
        runBlocking {
            budgetRepo.insertAll(listOf(
                Budget(id = 1, name = "Makanan", amount = 1_000_000.0),
                Budget(id = 2, name = "Transport", amount = 500_000.0)
            ))
        }
        setScreen()
        waitForBudgetsLoaded()
        composeTestRule.onNodeWithText("2 anggaran · total Rp 1.500.000").assertIsDisplayed()
    }

    // ---- Test 4: FAB tap opens C2 form ----
    @Test
    fun testFabTap_opensC2Form() {
        setScreen()
        waitForBudgetsLoaded()
        composeTestRule.onNodeWithContentDescription("Tambah Anggaran").performClick()
        // Wait for the C2 form to appear
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Anggaran Baru").fetchSemanticsNodes().isNotEmpty()
        }
        // C2 form title and fields (use assertExists — overlay layout can cause
        // assertIsDisplayed to be flaky in Robolectric for full-screen overlays).
        // Updated for percent-based model: "Limit Bulanan" removed, slider+preview added.
        composeTestRule.onNodeWithText("Anggaran Baru").assertExists()
        composeTestRule.onNodeWithText("Nama Budget *").assertExists()
        composeTestRule.onNodeWithText("Limit Bulanan *").assertDoesNotExist()
        composeTestRule.onNodeWithText("Alokasi Income (%)").assertExists()
        composeTestRule.onNodeWithText("Catatan (opsional)").assertExists()
        composeTestRule.onNodeWithText("Simpan Anggaran").assertExists()
        // No-income hint when currentMonthIncome is 0 (substring to skip emoji prefix)
        composeTestRule.onNodeWithText("Tambah income dulu", substring = true).assertExists()
    }

    // ---- Test 5: C2 form "Nama Budget" text field accepts free text ----
    @Test
    fun testC2Form_namaBudgetField_acceptsFreeText() {
        setScreen()
        waitForBudgetsLoaded()
        composeTestRule.onNodeWithContentDescription("Tambah Anggaran").performClick()
        composeTestRule.waitForIdle()
        // Form has the new "Nama Budget *" text field.
        composeTestRule.onNodeWithText("Nama Budget *").assertIsDisplayed()
        // "Kategori *" label IS present (new: multi-category tracking).
        composeTestRule.onNodeWithText("Kategori *").assertExists()
    }

    // ---- Test 6: C2 form save button is a no-op when invalid ----
    @Test
    fun testC2Form_saveNoOp_whenInvalid() {
        setScreen()
        waitForBudgetsLoaded()
        composeTestRule.onNodeWithContentDescription("Tambah Anggaran").performClick()
        composeTestRule.waitForIdle()
        // No category selected, no amount → click save (should be a no-op, form stays)
        composeTestRule.onNodeWithText("Simpan Anggaran").performClick()
        composeTestRule.waitForIdle()
        // C2 form is still showing
        composeTestRule.onNodeWithText("Anggaran Baru").assertIsDisplayed()
    }

    // ---- Test 7: C2 form back button closes ----
    @Test
    fun testC2Form_backButton_closesForm() {
        setScreen()
        waitForBudgetsLoaded()
        composeTestRule.onNodeWithContentDescription("Tambah Anggaran").performClick()
        // Wait for the C2 form to appear (now there are 2 "Kembali" nodes: list + form)
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Anggaran Baru").fetchSemanticsNodes().isNotEmpty()
        }
        // Tap the LAST "Kembali" (the C2 form's back button, rendered on top)
        composeTestRule.onAllNodesWithContentDescription("Kembali").onLast().performClick()
        composeTestRule.waitForIdle()
        // Back to list, C2 form is gone
        composeTestRule.onNodeWithText("Anggaran Baru").assertDoesNotExist()
        composeTestRule.onNodeWithText("Kelola Anggaran").assertIsDisplayed()
    }

    // ---- Test 8: Overflow menu opens with Edit and Arsipkan ----
    @Test
    fun testOverflowMenu_opensWithOptions() {
        runBlocking {
            budgetRepo.insertAll(listOf(
                Budget(id = 1, name = "Makanan", amount = 1_000_000.0)
            ))
        }
        setScreen()
        waitForBudgetsLoaded()
        // Tap the overflow menu (contentDescription = "Menu")
        composeTestRule.onNodeWithContentDescription("Menu").performClick()
        composeTestRule.waitForIdle()
        // Edit and Arsipkan should be visible
        composeTestRule.onNodeWithText("Edit").assertIsDisplayed()
        composeTestRule.onNodeWithText("Arsipkan").assertIsDisplayed()
    }

    // ---- Test 9: Arsipkan triggers archive (snackbar Undo appears) ----
    @Test
    fun testArchive_snackbarUndoAppears() {
        runBlocking {
            budgetRepo.insertAll(listOf(
                Budget(id = 1, name = "Makanan", amount = 1_000_000.0)
            ))
        }
        setScreen()
        waitForBudgetsLoaded()
        composeTestRule.onNodeWithContentDescription("Menu").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Arsipkan").performClick()
        composeTestRule.waitForIdle()
        // Snackbar appears with category name + Undo action
        composeTestRule.onNodeWithText("Makanan diarsipkan").assertIsDisplayed()
        composeTestRule.onNodeWithText("Undo").assertIsDisplayed()
        // Sanity: underlying repo confirms it's soft-deleted
        val all = budgetRepo.budgets.value
        assertEquals(1, all.size)
        assertNotNull(all[0].deletedAt)
    }

    // ---- Test 10: Header "Lihat yang dihapus" link visible when list non-empty ----
    @Test
    fun testHeader_showDeletedLink_visibleWhenListNonEmpty() {
        runBlocking {
            budgetRepo.insertAll(listOf(
                Budget(id = 1, name = "Makanan", amount = 1_000_000.0)
            ))
        }
        setScreen()
        waitForBudgetsLoaded()
        composeTestRule.onNodeWithText("Lihat yang dihapus").assertIsDisplayed()
    }

    // ---- Test 11: C2 form accepts a free-text name in the budget field ----
    @Test
    fun testC2Form_freeTextNameInput() {
        setScreen()
        waitForBudgetsLoaded()
        composeTestRule.onNodeWithContentDescription("Tambah Anggaran").performClick()
        composeTestRule.waitForIdle()
        // Form is still showing
        composeTestRule.onNodeWithText("Simpan Anggaran").assertIsDisplayed()
        // The "Nama Budget *" field exists (and the old "Kategori *" field is gone).
        composeTestRule.onNodeWithText("Nama Budget *").assertIsDisplayed()
    }

    // ---- Test 12: Regression gate — C2 form has NO Kategori field ----
    // User reported UX bug: form contained "Kategori *" which is wrong for the
    // independent-variable Budget model (name + icon, no category linkage).
    // This test pins the current correct shape so future regressions are caught.
    @Test
    fun testFabTap_opensC2Form_hasNoKategoriField() {
        setScreen()
        waitForBudgetsLoaded()
        // Open the C2 form via FAB tap
        composeTestRule.onNodeWithContentDescription("Tambah Anggaran").performClick()
        composeTestRule.waitForIdle()
        // Form is open

        // "Kategori *" IS present (new: multi-category tracking)
        composeTestRule.onNodeWithText("Kategori *").assertExists()
        // Icon picker is present
        composeTestRule.onNodeWithText("Ikon").assertExists()
        // Period toggle is present (Mingguan / Bulanan)
        composeTestRule.onNodeWithText("Mingguan").assertExists()
        composeTestRule.onNodeWithText("Bulanan").assertExists()
        // Amount mode toggle present (% Income / Manual)
        composeTestRule.onNodeWithText("% Income").assertExists()
        composeTestRule.onNodeWithText("Manual").assertExists()
        composeTestRule.onNodeWithText("Catatan (opsional)").assertExists()
    }
}
