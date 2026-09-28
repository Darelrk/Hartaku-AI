package com.example.ui.screenshot

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.FakeBudgetRepository
import com.example.data.FakeCategoryRepository
import com.example.data.FakeTransactionRepository
import com.example.ui.ProfileScreen
import com.example.ui.ProfileViewModel
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
class ProfileScreenScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun profileScreen_empty() {
        val fakeTxRepo = FakeTransactionRepository()
        val fakeBudgetRepo = FakeBudgetRepository()
        val fakeCategoryRepo = FakeCategoryRepository()
        val viewModel = ProfileViewModel(fakeTxRepo, fakeBudgetRepo, fakeCategoryRepo)
        composeTestRule.setContent {
            MyApplicationTheme {
                ProfileScreen(viewModelOverride = viewModel)
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }
}
