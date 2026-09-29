package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainScreenSaveSnackbarTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun pendingSaveMessage_isShownAndConsumedOnce() {
        val pendingMessage = mutableStateOf<String?>("2 transaksi tersimpan")
        lateinit var snackbarHostState: SnackbarHostState
        var consumedCount = 0

        composeTestRule.setContent {
            MaterialTheme {
                val hostState = androidx.compose.runtime.remember { SnackbarHostState() }
                snackbarHostState = hostState
                Box(Modifier.fillMaxSize()) {
                    SnackbarHost(hostState = hostState)
                    PendingSaveSnackbarEffect(
                        pendingMessage = pendingMessage.value,
                        snackbarHostState = hostState,
                        onMessageConsumed = {
                            consumedCount++
                            pendingMessage.value = null
                        }
                    )
                }
            }
        }

        composeTestRule.waitUntil(5_000) {
            snackbarHostState.currentSnackbarData?.visuals?.message == "2 transaksi tersimpan"
        }
        composeTestRule.onNodeWithText("2 transaksi tersimpan").assertIsDisplayed()

        composeTestRule.runOnIdle { snackbarHostState.currentSnackbarData?.dismiss() }
        composeTestRule.waitUntil(5_000) {
            pendingMessage.value == null && snackbarHostState.currentSnackbarData == null
        }
        composeTestRule.waitForIdle()

        assertNull(pendingMessage.value)
        assertEquals(1, consumedCount)
    }
}
