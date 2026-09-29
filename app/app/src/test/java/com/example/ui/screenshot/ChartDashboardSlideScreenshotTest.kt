package com.example.ui.screenshot

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.example.data.DailyExpense
import com.example.ui.home.ChartDashboardSlide
import com.example.ui.home.TwoWeekExpense
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ChartDashboardSlideScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun chartDashboardSlide_withData() {
        // Create 14 days of sample data
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -14)
        
        val dailyData = (0..13).map { daysAgo ->
            val dayCal = Calendar.getInstance()
            dayCal.add(Calendar.DAY_OF_YEAR, -daysAgo)
            // Simulate varying expenses: higher in recent week
            val expense = if (daysAgo < 7) 50000.0 + (daysAgo * 5000.0) else 30000.0 + (daysAgo * 2000.0)
            DailyExpense(dayStart = dayCal.timeInMillis, total = expense)
        }.reversed() // oldest to newest
        
        val thisWeekTotal = dailyData.takeLast(7).sumOf { it.total }
        val prevWeekTotal = dailyData.take(7).sumOf { it.total }
        
        composeTestRule.setContent {
            MyApplicationTheme {
                ChartDashboardSlide(
                    twoWeek = TwoWeekExpense(
                        daily = dailyData,
                        thisWeekTotal = thisWeekTotal,
                        prevWeekTotal = prevWeekTotal
                    ),
                    dayOffset = 0
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun chartDashboardSlide_empty() {
        composeTestRule.setContent {
            MyApplicationTheme {
                ChartDashboardSlide(
                    twoWeek = TwoWeekExpense(
                        daily = emptyList(),
                        thisWeekTotal = 0.0,
                        prevWeekTotal = 0.0
                    ),
                    dayOffset = 0
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun chartDashboardSlide_withDelta() {
        // Create data showing clear week-over-week change
        val dailyData = (0..13).map { daysAgo ->
            val dayCal = Calendar.getInstance()
            dayCal.add(Calendar.DAY_OF_YEAR, -daysAgo)
            // Previous week: 40k/day, This week: 60k/day (50% increase)
            val expense = if (daysAgo < 7) 60000.0 else 40000.0
            DailyExpense(dayStart = dayCal.timeInMillis, total = expense)
        }.reversed()
        
        val thisWeekTotal = dailyData.takeLast(7).sumOf { it.total }
        val prevWeekTotal = dailyData.take(7).sumOf { it.total }
        
        composeTestRule.setContent {
            MyApplicationTheme {
                ChartDashboardSlide(
                    twoWeek = TwoWeekExpense(
                        daily = dailyData,
                        thisWeekTotal = thisWeekTotal,
                        prevWeekTotal = prevWeekTotal
                    ),
                    dayOffset = 0
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun zeroExpenseBarHasNoVisibleWidth() {
        val today = Calendar.getInstance().timeInMillis
        val daily = (0..13).map { index ->
            DailyExpense(today + index * 86_400_000L, if (index == 13) 100.0 else 0.0)
        }
        composeTestRule.setContent {
            MyApplicationTheme {
                ChartDashboardSlide(
                    twoWeek = TwoWeekExpense(daily, thisWeekTotal = 100.0, prevWeekTotal = 0.0)
                )
            }
        }

        val width = composeTestRule.onNodeWithTag("expenseBar-0").fetchSemanticsNode().boundsInRoot.width
        assertTrue("zero expense bars have no visible width", width == 0f)
    }

    @Test
    fun changedExpenseBarWidthAnimatesToTarget() {
        composeTestRule.mainClock.autoAdvance = false
        val today = Calendar.getInstance().timeInMillis
        val daily = (0..13).map { index ->
            DailyExpense(today + index * 86_400_000L, if (index == 13) 100.0 else 0.0)
        }
        val chart = mutableStateOf(TwoWeekExpense(daily, thisWeekTotal = 100.0, prevWeekTotal = 0.0))
        composeTestRule.setContent {
            MyApplicationTheme { ChartDashboardSlide(twoWeek = chart.value) }
        }
        composeTestRule.waitForIdle()

        val bar = composeTestRule.onNodeWithTag("expenseBar-0")
        val track = composeTestRule.onNodeWithTag("expenseBarTrack-0")
        val startWidth = bar.fetchSemanticsNode().boundsInRoot.width
        composeTestRule.runOnIdle {
            chart.value = chart.value.copy(
                daily = daily.mapIndexed { index, expense ->
                    if (index == 0) expense.copy(total = 100.0) else expense
                }
            )
        }
        // Flush the changed layout before advancing the manually controlled animation clock.
        bar.fetchSemanticsNode()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(110)
        val intermediateWidth = bar.fetchSemanticsNode().boundsInRoot.width
        composeTestRule.mainClock.advanceTimeBy(120)
        val finalWidth = bar.fetchSemanticsNode().boundsInRoot.width
        val trackWidth = track.fetchSemanticsNode().boundsInRoot.width

        assertTrue("bar grows during the tween", intermediateWidth > startWidth)
        assertTrue("bar is still animating at 110 ms", intermediateWidth < trackWidth)
        assertTrue("bar reaches full target after the tween", finalWidth == trackWidth)
    }
}
