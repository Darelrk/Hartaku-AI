package com.example.ui.screenshot

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.FakeBillRepository
import com.example.data.FakeBudgetRepository
import com.example.data.FakeCategoryRepository
import com.example.data.FakeTransactionRepository
import com.example.ui.ManualInputScreen
import com.example.ui.ManualInputViewModel
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
class ManualInputScreenScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun manualInputScreen_empty() {
        val fakeTxRepo = FakeTransactionRepository()
        val fakeCategoryRepo = FakeCategoryRepository()
        val fakeBudgetRepo = FakeBudgetRepository()
        val fakeBillRepo = FakeBillRepository()
        val viewModel = ManualInputViewModel(fakeTxRepo, fakeCategoryRepo, fakeBudgetRepo, null, fakeBillRepo)

        composeTestRule.setContent {
            MyApplicationTheme {
                ManualInputScreen(
                    onClose = {},
                    viewModelOverride = viewModel
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }
}
