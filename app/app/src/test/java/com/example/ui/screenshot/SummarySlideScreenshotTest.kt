package com.example.ui.screenshot

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.Budget
import com.example.data.CategoryTotal
import com.example.ui.home.SummarySlide
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SummarySlideScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun summarySlide_withData() {
        composeTestRule.setContent {
            MyApplicationTheme {
                SummarySlide(
                    totalSpending = 150000.0,
                    totalIncome = 500000.0,
                    previousDaySpending = 100000.0,
                    transactionCount = 5,
                    categoryBreakdown = listOf(
                        CategoryTotal("Makanan", 80000.0),
                        CategoryTotal("Transport", 20000.0),
                        CategoryTotal("Belanja", 50000.0)
                    ),
                    budgets = listOf(
                        Budget(id = 1, name = "Makan", amount = 200000.0, spent = 45000.0, percent = 40.0),
                        Budget(id = 2, name = "Transport", amount = 100000.0, spent = 30000.0, percent = 20.0)
                    ),
                    insightText = "Pengeluaran hari ini naik 28% dari kemarin"
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun summarySlide_overBudget() {
        composeTestRule.setContent {
            MyApplicationTheme {
                SummarySlide(
                    totalSpending = 350000.0,
                    totalIncome = 100000.0,
                    previousDaySpending = 200000.0,
                    transactionCount = 12,
                    categoryBreakdown = listOf(
                        CategoryTotal("Makanan", 150000.0),
                        CategoryTotal("Shopping", 200000.0)
                    )
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }
}
