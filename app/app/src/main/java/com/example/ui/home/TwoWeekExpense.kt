package com.example.ui.home

import com.example.data.DailyExpense

/**
 * 14-day expense window for the chart dashboard slide.
 *
 * @property daily List of 14 entries, ordered oldest→newest. Each entry is
 *   [DailyExpense] with UTC day-start millis and the day's expense total.
 *   First 7 are previous week (dimmed in chart), last 7 are current week
 *   (colored by delta).
 * @property thisWeekTotal Sum of the last 7 days.
 * @property prevWeekTotal Sum of the first 7 days.
 * @property deltaPct Percent change `thisWeekTotal` vs `prevWeekTotal`.
 *   null if `prevWeekTotal == 0` (avoid div-by-zero).
 * @property avgDaily Average per-day expense across the 7-day current week.
 */
data class TwoWeekExpense(
    val daily: List<DailyExpense>,
    val thisWeekTotal: Double,
    val prevWeekTotal: Double,
    val deltaPct: Double? = null,
    val avgDaily: Double = 0.0
)
