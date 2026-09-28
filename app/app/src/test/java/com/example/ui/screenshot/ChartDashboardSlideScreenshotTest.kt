package com.example.ui.screenshot

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.DailyExpense
import com.example.ui.home.ChartDashboardSlide
import com.example.ui.home.TwoWeekExpense
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
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
}
