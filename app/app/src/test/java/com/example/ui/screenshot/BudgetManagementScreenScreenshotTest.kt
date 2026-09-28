package com.example.ui.screenshot

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.FakeBudgetRepository
import com.example.data.FakeCategoryRepository
import com.example.data.FakeTransactionRepository
import com.example.ui.BudgetManagementScreen
import com.example.ui.BudgetManagementViewModel
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class BudgetManagementScreenScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun budgetManagementScreen_empty() {
        val fakeBudgetRepo = FakeBudgetRepository()
        val fakeCategoryRepo = FakeCategoryRepository()
        val fakeTxRepo = FakeTransactionRepository()
        val viewModel = BudgetManagementViewModel(fakeBudgetRepo, fakeCategoryRepo, fakeTxRepo)

        composeTestRule.setContent {
            MyApplicationTheme {
                BudgetManagementScreen(
                    onClose = {},
                    onShowDeleted = {},
                    viewModelOverride = viewModel
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }
}
