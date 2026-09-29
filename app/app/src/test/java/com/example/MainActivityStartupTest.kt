package com.example

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainActivityStartupTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun startupGateShowsExactCopyAndIndeterminateProgress() {
        composeTestRule.onNodeWithText("Menyiapkan data aman…", useUnmergedTree = true).assertExists()
        assertTrue(
            composeTestRule.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
                .fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            composeTestRule.onAllNodesWithText("Home", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        )
    }
}
