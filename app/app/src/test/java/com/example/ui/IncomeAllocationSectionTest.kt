package com.example.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.Budget
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose UI Test untuk [IncomeAllocationSection].
 *
 * Test ini berjalan di JVM melalui Robolectric, sehingga tidak butuh
 * emulator/perangkat fisik. Kita panggil composable secara langsung
 * dengan state terkontrol (hoisted state) untuk verifikasi perilaku UI:
 *   - Toggle default vs kustom
 *   - Render input persen per kategori budget
 *   - Validasi total persen (harus 100% untuk simpan)
 */
@RunWith(RobolectricTestRunner::class)
class IncomeAllocationSectionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleBudgets = listOf(
        Budget(id = 1, name = "Makanan", icon = "food", amount = 0.0, period = "monthly", percent = 40.0),
        Budget(id = 2, name = "Transport", icon = "transport", amount = 0.0, period = "monthly", percent = 30.0),
        Budget(id = 3, name = "Tabungan", icon = "savings", amount = 0.0, period = "monthly", percent = 30.0)
    )

    @Test
    fun sectionHeader_isVisible() {
        composeTestRule.setContent {
            MyApplicationTheme {
                IncomeAllocationSection(
                    useDefaultAllocation = true,
                    customAllocations = emptyMap(),
                    budgetCategories = sampleBudgets,
                    onUseDefaultChange = {},
                    onCustomAllocationChange = { _, _ -> }
                )
            }
        }
        composeTestRule.onNodeWithText("Alokasi Pemasukan ke Anggaran").assertIsDisplayed()
    }

    @Test
    fun defaultRadio_isSelectedByDefault() {
        composeTestRule.setContent {
            MyApplicationTheme {
                IncomeAllocationSection(
                    useDefaultAllocation = true,
                    customAllocations = emptyMap(),
                    budgetCategories = sampleBudgets,
                    onUseDefaultChange = {},
                    onCustomAllocationChange = { _, _ -> }
                )
            }
        }
        composeTestRule.onNodeWithText("Gunakan Rencana Persen Default").assertIsDisplayed()
        composeTestRule.onNodeWithText("Kustom Alokasi (%)").assertIsDisplayed()
    }

    @Test
    fun customMode_hidesInputsByDefault() {
        composeTestRule.setContent {
            MyApplicationTheme {
                IncomeAllocationSection(
                    useDefaultAllocation = true,
                    customAllocations = emptyMap(),
                    budgetCategories = sampleBudgets,
                    onUseDefaultChange = {},
                    onCustomAllocationChange = { _, _ -> }
                )
            }
        }
        // In default mode, total label should NOT be visible because the
        // custom-mode branch is not entered.
        composeTestRule.onNodeWithText("Total Kustom: 100.0% (Harus 100% untuk menyimpan)")
            .assertDoesNotExist()
    }

    @Test
    fun clickingCustom_rendersAllocationInputs() {
        var useDefault by mutableStateOf(true)
        composeTestRule.setContent {
            MyApplicationTheme {
                IncomeAllocationSection(
                    useDefaultAllocation = useDefault,
                    customAllocations = emptyMap(),
                    budgetCategories = sampleBudgets,
                    onUseDefaultChange = { useDefault = it },
                    onCustomAllocationChange = { _, _ -> }
                )
            }
        }

        composeTestRule.onNodeWithText("Kustom Alokasi (%)").performClick()
        composeTestRule.waitForIdle()

        // After clicking, all three category labels should appear
        composeTestRule.onNodeWithText("Makanan").assertIsDisplayed()
        composeTestRule.onNodeWithText("Transport").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tabungan").assertIsDisplayed()
    }

    @Test
    fun customMode_showsTotalLabelWithCurrentAllocation() {
        val custom = mapOf(
            "Makanan" to 50.0,
            "Transport" to 30.0,
            "Tabungan" to 20.0
        )
        composeTestRule.setContent {
            MyApplicationTheme {
                IncomeAllocationSection(
                    useDefaultAllocation = false,
                    customAllocations = custom,
                    budgetCategories = sampleBudgets,
                    onUseDefaultChange = {},
                    onCustomAllocationChange = { _, _ -> }
                )
            }
        }
        composeTestRule
            .onNodeWithText("Total Kustom: 100.0% (Harus 100% untuk menyimpan)")
            .assertIsDisplayed()
    }

    @Test
    fun customMode_showsWarningWhenTotalNotHundred() {
        val custom = mapOf(
            "Makanan" to 50.0,
            "Transport" to 20.0,
            "Tabungan" to 20.0
        ) // total = 90
        composeTestRule.setContent {
            MyApplicationTheme {
                IncomeAllocationSection(
                    useDefaultAllocation = false,
                    customAllocations = custom,
                    budgetCategories = sampleBudgets,
                    onUseDefaultChange = {},
                    onCustomAllocationChange = { _, _ -> }
                )
            }
        }
        composeTestRule
            .onNodeWithText("Total Kustom: 90.0% (Harus 100% untuk menyimpan)")
            .assertIsDisplayed()
    }

    @Test
    fun emptyBudgets_rendersA3CtaCard() {
        composeTestRule.setContent {
            MyApplicationTheme {
                IncomeAllocationSection(
                    useDefaultAllocation = false,
                    customAllocations = emptyMap(),
                    budgetCategories = emptyList(),
                    onUseDefaultChange = {},
                    onCustomAllocationChange = { _, _ -> }
                )
            }
        }
        // A3 empty state: CTA card with "Setup Anggaran Dulu" is shown instead of
        // the radio buttons / custom form. Save flow is NOT blocked.
        composeTestRule
            .onNodeWithText("Setup Anggaran Dulu", substring = true)
            .assertIsDisplayed()
        // The old "Total Kustom" label should NOT be present (section returns early).
        composeTestRule
            .onNodeWithText("Total Kustom: 0.0% (Harus 100% untuk menyimpan)")
            .assertDoesNotExist()
    }

    @Test
    fun togglingBetweenDefaultAndCustom_invokesCallback() {
        var useDefault by mutableStateOf(true)
        var lastUseDefaultValue: Boolean? = null
        composeTestRule.setContent {
            MyApplicationTheme {
                IncomeAllocationSection(
                    useDefaultAllocation = useDefault,
                    customAllocations = emptyMap(),
                    budgetCategories = sampleBudgets,
                    onUseDefaultChange = {
                        lastUseDefaultValue = it
                        useDefault = it
                    },
                    onCustomAllocationChange = { _, _ -> }
                )
            }
        }

        // Click "Kustom Alokasi (%)" label area
        composeTestRule.onNodeWithText("Kustom Alokasi (%)").performClick()
        composeTestRule.waitForIdle()
        assertEquals(false, lastUseDefaultValue)
        assertEquals(false, useDefault)
    }
}
