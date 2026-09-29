package com.example.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.data.Transaction
import com.example.data.TransactionType
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AdvancedTransactionListSlideGestureTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val transaction = Transaction(
        id = 1,
        amount = 45_000.0,
        description = "Nasi Goreng",
        category = "Makanan",
        type = TransactionType.EXPENSE,
        timestamp = 1_759_132_800_000L
    )

    @Test
    fun swipeLeftConfirmsDeleteOnce_thenSwipeRightOpensEdit() {
        val deleted = mutableListOf<Transaction>()
        showTransactionList(onDelete = deleted::add)

        swipeTransaction(direction = -1)
        composeTestRule.onNodeWithText("Hapus transaksi?").assertExists()
        composeTestRule.onNodeWithText("Hapus").performClick()
        composeTestRule.runOnIdle {
            assertEquals(listOf(transaction), deleted)
        }

        swipeTransaction(direction = 1)
        composeTestRule.onNodeWithText("EDIT TRANSAKSI").assertExists()
    }

    private fun showTransactionList(onDelete: (Transaction) -> Unit = {}) {
        composeTestRule.setContent {
            MyApplicationTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    Box(Modifier.requiredSize(320.dp, 640.dp).testTag("gestureHost")) {
                        AdvancedTransactionListSlide(
                            transactions = listOf(transaction),
                            onDelete = onDelete
                        )
                    }
                }
            }
        }
    }

    private fun swipeTransaction(direction: Int) {
        val host = composeTestRule.onNodeWithTag("gestureHost")
        val hostBounds = host.fetchSemanticsNode().boundsInRoot
        val itemBounds = composeTestRule.onNodeWithText(transaction.description)
            .fetchSemanticsNode()
            .boundsInRoot
        val start = Offset(hostBounds.width / 2f, itemBounds.center.y - hostBounds.top)

        // 122px crosses 35% of the 280px row while staying below the previous 150px cutoff.
        host.performTouchInput {
            swipe(
                start = start,
                end = start + Offset(direction * hostBounds.width * 0.38f, 0f),
                durationMillis = 1_000
            )
        }
    }
}
