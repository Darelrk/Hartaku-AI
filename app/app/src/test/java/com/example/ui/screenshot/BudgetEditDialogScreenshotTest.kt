package com.example.ui.screenshot

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.BudgetEditDialog
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
class BudgetEditDialogScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun budgetEditDialog_withIncome() {
        composeTestRule.setContent {
            MyApplicationTheme {
                BudgetEditDialog(
                    currentMonthIncome = 5000000.0,
                    expenseCategories = emptyList(),
                    onSave = { _, _, _, _, _, _, _ -> },
                    onClose = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun budgetEditDialog_noIncome() {
        composeTestRule.setContent {
            MyApplicationTheme {
                BudgetEditDialog(
                    currentMonthIncome = 0.0,
                    expenseCategories = emptyList(),
                    onSave = { _, _, _, _, _, _, _ -> },
                    onClose = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }
}
