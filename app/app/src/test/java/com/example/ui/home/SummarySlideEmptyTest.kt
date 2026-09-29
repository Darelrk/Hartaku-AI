package com.example.ui.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import com.example.data.CategoryTotal
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SummarySlideEmptyTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun zeroTransactions_showsEmptyStateEvenWhenAmountsExist() {
        composeTestRule.setContent {
            MyApplicationTheme {
                SummarySlide(
                    totalSpending = 45_000.0,
                    totalIncome = 120_000.0,
                    transactionCount = 0,
                    categoryBreakdown = listOf(CategoryTotal("Makanan", 45_000.0)),
                    insightText = "Pengeluaran hari ini naik"
                )
            }
        }

        composeTestRule.onNodeWithText("Belum ada transaksi pada tanggal ini").assertIsDisplayed()
        composeTestRule.onNodeWithText("Catat lewat suara, kamera, atau tulis manual").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("MASUK").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("TOP KATEGORI").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Pengeluaran hari ini naik").assertCountEquals(0)
    }

    @Test
    fun oneTransaction_hidesEmptyStateEvenWhenAmountsAreZero() {
        composeTestRule.setContent {
            MyApplicationTheme {
                SummarySlide(transactionCount = 1)
            }
        }

        composeTestRule.onAllNodesWithText("Belum ada transaksi pada tanggal ini").assertCountEquals(0)
        composeTestRule.onNodeWithText("SALDO HARI INI").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 transaksi").assertIsDisplayed()
    }
}
