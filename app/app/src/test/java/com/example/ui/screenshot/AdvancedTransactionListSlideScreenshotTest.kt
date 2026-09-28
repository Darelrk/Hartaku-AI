package com.example.ui.screenshot

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.Category
import com.example.data.Transaction
import com.example.data.TransactionType
import com.example.ui.home.AdvancedTransactionListSlide
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
class AdvancedTransactionListSlideScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun transactionList_empty() {
        composeTestRule.setContent {
            MyApplicationTheme {
                AdvancedTransactionListSlide()
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun transactionList_withTransactions() {
        val now = System.currentTimeMillis()
        composeTestRule.setContent {
            MyApplicationTheme {
                AdvancedTransactionListSlide(
                    transactions = listOf(
                        Transaction(id = 1, amount = 45000.0, description = "Nasi Goreng", category = "Makanan", type = TransactionType.EXPENSE, timestamp = now),
                        Transaction(id = 2, amount = 15000.0, description = "Ojek Online", category = "Transport", type = TransactionType.EXPENSE, timestamp = now - 3600000),
                        Transaction(id = 3, amount = 500000.0, description = "Gaji Freelance", category = "Income", type = TransactionType.INCOME, timestamp = now - 7200000),
                        Transaction(id = 4, amount = 120000.0, description = "Groceries", category = "Belanja", type = TransactionType.EXPENSE, timestamp = now - 10800000)
                    ),
                    categories = listOf(
                        Category(id = "1", name = "Makanan", slug = "makanan", typeClass = "EXPENSE"),
                        Category(id = "2", name = "Transport", slug = "transport", typeClass = "EXPENSE"),
                        Category(id = "3", name = "Belanja", slug = "belanja", typeClass = "EXPENSE")
                    )
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }
}
