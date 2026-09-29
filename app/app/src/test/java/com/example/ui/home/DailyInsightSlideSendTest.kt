package com.example.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import com.example.ai.ChatMessageItem
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
class DailyInsightSlideSendTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun sendButton_isDisabledWhenInputIsBlank() {
        composeTestRule.setContent {
            MyApplicationTheme { DailyInsightSlide() }
        }

        composeTestRule.onNodeWithContentDescription("Kirim pesan").assertIsNotEnabled()
    }

    @Test
    fun sendButton_isDisabledWhileChatIsLoading() {
        composeTestRule.setContent {
            MyApplicationTheme { DailyInsightSlide(isChatLoading = true) }
        }
        composeTestRule.onNode(hasSetTextAction()).performTextInput("Ringkas pengeluaran")

        composeTestRule.onNodeWithContentDescription("Kirim pesan").assertIsNotEnabled()
    }

    @Test
    fun sendButton_sendsTypedMessageOnceWhenReady() {
        val sentMessages = mutableListOf<String>()
        composeTestRule.setContent {
            MyApplicationTheme { DailyInsightSlide(onSendMessage = sentMessages::add) }
        }
        composeTestRule.onNode(hasSetTextAction()).performTextInput("Ringkas pengeluaran")

        composeTestRule.onNodeWithContentDescription("Kirim pesan").assertIsEnabled().performClick()
        composeTestRule.runOnIdle {
            assertEquals(listOf("Ringkas pengeluaran"), sentMessages)
        }
    }

    @Test
    fun newMessageDoesNotMoveChatWhenUserReadsOlderHistory() {
        var history by mutableStateOf((0..30).map { ChatMessageItem("user", "Pesan $it") })
        composeTestRule.setContent {
            MyApplicationTheme { DailyInsightSlide(chatHistory = history) }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dailyInsightMessages").performScrollToIndex(0)
        composeTestRule.onNodeWithText("Pesan 0").assertIsDisplayed()

        composeTestRule.runOnIdle {
            history = history + ChatMessageItem("assistant", "Jawaban terbaru")
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Pesan 0").assertIsDisplayed()
    }

    @Test
    fun loadingAndNewReplyStayVisibleWhenChatWasNearBottom() {
        var history by mutableStateOf((0..30).map { ChatMessageItem("user", "Pesan $it") })
        var loading by mutableStateOf(false)
        composeTestRule.setContent {
            MyApplicationTheme {
                DailyInsightSlide(chatHistory = history, isChatLoading = loading)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dailyInsightMessages").performScrollToIndex(30)

        composeTestRule.runOnIdle { loading = true }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("AI sedang berpikir...").assertIsDisplayed()

        composeTestRule.runOnIdle {
            history = history + ChatMessageItem("assistant", "Jawaban terbaru")
            loading = false
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Jawaban terbaru").assertIsDisplayed()
    }
}
