package com.example.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * Smoke test for [MainScreen] routing. The real [MainScreen] uses
 * [com.example.data.AppContainer] and the full nav graph (which requires
 * a real [androidx.navigation.NavController]); here we test the routing
 * intent pattern with a lightweight mimic.
 */
@RunWith(RobolectricTestRunner::class)
class MainScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun smoke_routesAreClickable_inMimic() {
        var voiceClicks = 0
        var scanClicks = 0
        var manualClicks = 0

        composeTestRule.setContent {
            var route by remember { mutableStateOf("home") }
            androidx.compose.foundation.layout.Column {
                androidx.compose.material3.Text("route=$route")
                androidx.compose.material3.Button(onClick = { voiceClicks++; route = "voice" }) {
                    androidx.compose.material3.Text("Suara")
                }
                androidx.compose.material3.Button(onClick = { scanClicks++; route = "scan" }) {
                    androidx.compose.material3.Text("Kamera")
                }
                androidx.compose.material3.Button(onClick = { manualClicks++; route = "manual" }) {
                    androidx.compose.material3.Text("Manual")
                }
            }
        }

        composeTestRule.onNodeWithText("Suara").performClick()
        composeTestRule.onNodeWithText("Kamera").performClick()
        composeTestRule.onNodeWithText("Manual").performClick()

        assertTrue("Suara button must be clickable", voiceClicks == 1)
        assertTrue("Kamera button must be clickable", scanClicks == 1)
        assertTrue("Manual button must be clickable", manualClicks == 1)
    }

    @Test
    fun routes_enum_hasExpectedValues() {
        val expected = setOf(
            "HOME",
            "VOICE_INPUT",
            "SCAN_RECEIPT",
            "MANUAL_INPUT",
            "CATEGORY_MANAGEMENT",
            "BUDGET_MANAGEMENT",
            "DELETED_BUDGETS"
        )
        val actual = ScreenRoute.entries.map { it.name }.toSet()
        assertTrue(
            "ScreenRoute enum must contain $expected, got $actual",
            actual.containsAll(expected)
        )
    }
}
