package com.example.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * MonthlyResetScheduler — handles the monthly "new month" boundary
 * for two state bits that need periodic reset:
 *
 *  1. `Bill.isPaidThisMonth` → reset to false at the start of every
 *     month so users can re-mark each bill as paid for the new cycle.
 *     This is a no-op for ONCE and CUSTOM_RANGE (already expired) bills.
 *  2. (Future) `RecurringTransaction.lastGeneratedMonth` → compared
 *     against current month to detect templates that need to be
 *     instantiated.
 *
 * Triggered by:
 *  - A daily AlarmManager broadcast (recommended, see
 *    [com.example.work.DailyCheckReceiver] which we wire in a
 *    follow-up gap).
 *  - Manual invocation via [triggerNow] (useful for testing).
 *  - App cold-start: [resetIfNewMonth] compares the stored lastReset
 *    marker (in SharedPreferences) against the current yearMonth and
 *    runs the reset if it differs.
 *
 * Why not WorkManager: this codebase doesn't yet depend on
 * androidx.work. A simple AlarmManager + SharedPreferences approach
 * is sufficient for monthly cadence and avoids adding a 200KB
 * dependency for one job. Migrate to WorkManager in a follow-up gap
 * if more periodic jobs land.
 */
object MonthlyResetScheduler {

    private const val PREFS_NAME = "hartaku_monthly_reset"
    private const val KEY_LAST_RESET_YEARMONTH = "last_reset_ym"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Call on every app cold start. Resets if the month rolled over. */
    fun resetIfNewMonth(context: Context) {
        // ponytail: never reach AppContainer on the caller's thread — MainActivity calls this
        // on the main thread before the first frame.
        scope.launch {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            synchronized(this@MonthlyResetScheduler) {
                val current = currentYearMonth()
                val last = prefs.getInt(KEY_LAST_RESET_YEARMONTH, 0)
                if (current > last) {
                    triggerNow(context)
                    prefs.edit().putInt(KEY_LAST_RESET_YEARMONTH, current).apply()
                }
            }
        }
    }

    /** Force a reset right now. Used by [resetIfNewMonth] and tests. */
    fun triggerNow(context: Context) {
        val container = AppContainer.getInstance(context)
        CoroutineScope(Dispatchers.IO).launch {
            container.billRepository.resetMonthlyPaidStatus()
        }
    }

    private fun currentYearMonth(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.YEAR) * 100 + (cal.get(Calendar.MONTH) + 1)
    }
}
