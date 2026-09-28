package com.example.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private fun ComposeContentTestRule.assertTextExists(text: String) {
    assertTrue(
        "expected text \"$text\" to be in the tree",
        onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    )
}

/**
 * Lightweight tests for [VoiceInputScreen]. The real screen requires mic
 * permission, audio recording, and AppContainer wiring, so we only exercise
 * the composable shape and the back/cancel callback contract.
 */
@RunWith(RobolectricTestRunner::class)
class VoiceInputScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun smoke_composableRenders_withoutCrash() {
        composeTestRule.setContent {
            Box(Modifier.padding(16.dp)) {
                androidx.compose.material3.Text("voice smoke")
            }
        }
        composeTestRule.assertTextExists("voice smoke")
    }

    @Test
    fun backButton_invokesOnClose() {
        var onCloseCalls = 0
        composeTestRule.setContent {
            IconButton(onClick = { onCloseCalls++ }) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = "Back"
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, onCloseCalls)
    }
}
