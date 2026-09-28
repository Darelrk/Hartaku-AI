package com.example.data

/**
 * One day's total expense, used by chart dashboard slide.
 * [dayStart] is the start-of-day millis (UTC). [total] is the sum of
 * all EXPENSE transactions in that day.
 */
data class DailyExpense(
    val dayStart: Long,
    val total: Double
)
