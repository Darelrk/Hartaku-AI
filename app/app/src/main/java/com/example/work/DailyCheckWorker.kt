package com.example.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.ai.AgentProactiveEngine
import com.example.ai.BillInfo
import com.example.ai.BudgetInfo
import com.example.data.AppContainer
import com.example.data.TaskType
import com.example.ui.home.withSpent
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class DailyCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = AppContainer.getInstance(applicationContext)
        val engine = AgentProactiveEngine(
            container.transactionRepository,
            container.budgetRepository,
            container.billRepository
        )
        val now = System.currentTimeMillis()

        // Rentang bulan berjalan dipakai oleh budget spent DAN reminder tagihan,
        // jadi dihitung sekali di atas.
        val cal2 = java.util.Calendar.getInstance()
        cal2.set(java.util.Calendar.DAY_OF_MONTH, 1)
        cal2.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal2.set(java.util.Calendar.MINUTE, 0)
        cal2.set(java.util.Calendar.SECOND, 0)
        cal2.set(java.util.Calendar.MILLISECOND, 0)
        val monthStart = cal2.timeInMillis
        val monthEndCal = cal2.clone() as java.util.Calendar
        monthEndCal.add(java.util.Calendar.MONTH, 1)
        val monthEnd = monthEndCal.timeInMillis
        // Budget alerts. `Budget.spent` adalah field TERSIMPAN dan praktis
        // selalu 0, jadi alert sebelumnya membandingkan limit terhadap nol dan
        // tidak pernah memicu. Hitung ulang dengan `withSpent` agar sama dengan
        // yang dilihat pengguna di layar.
        val allBudgets = container.budgetRepository.getAllBudgets().first()
        val monthTx = container.transactionRepository
            .getTransactionsInRange(monthStart, monthEnd).first()
        val monthIncome = container.transactionRepository
            .getIncomeInRange(monthStart, monthEnd).first()
        for (b in allBudgets.withSpent(monthTx, monthIncome)) {
            if (b.deletedAt != null) continue
            val msg = engine.checkBudgetAlert(BudgetInfo(b.name, b.amount, b.spent), now)
            if (msg != null && AgentPrefs.isEnabled(applicationContext, TaskType.BUDGET_ALERT)) {
                NotificationHelper.show(applicationContext, "⚠️ Budget Alert", msg)
            }
        }

        // Bill reminders — use getActiveBillsForMonth with current month
        val allBills = container.billRepository.getActiveBillsForMonth(monthStart).first()
        for (b in allBills) {
            val msg = engine.checkBillReminder(BillInfo(b.name, b.amount, b.isPaidThisMonth))
            if (msg != null && AgentPrefs.isEnabled(applicationContext, TaskType.BILL_REMINDER)) {
                NotificationHelper.show(applicationContext, "💡 Tagihan", msg)
            }
        }

        // Inactivity reminder (18:00 → 64800 seconds after midnight)
        val cal3 = java.util.Calendar.getInstance()
        val secondsSinceMidnight = cal3.get(java.util.Calendar.HOUR_OF_DAY) * 3600 +
                cal3.get(java.util.Calendar.MINUTE) * 60
        if (secondsSinceMidnight >= 64800) {
            val msg = engine.checkInactivityReminder(now)
            if (msg != null && AgentPrefs.isEnabled(applicationContext, TaskType.INACTIVITY_REMINDER)) {
                NotificationHelper.show(applicationContext, "📝 Catat Pengeluaran", msg)
            }
        }

        // Daily summary
        val summary = engine.checkSpendingSummary(now)
        if (summary != null && AgentPrefs.isEnabled(applicationContext, TaskType.SPENDING_CHECK)) {
            NotificationHelper.show(applicationContext, "📊 Ringkasan Harian", summary)
        }

        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "daily_agent_check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyCheckWorker>(
                1440, TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}

/**
 * SharedPreferences-based toggle per TaskType.
 */
object AgentPrefs {
    private const val PREFS = "hartaku_agent_prefs"
    fun isEnabled(context: Context, type: TaskType): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(type.name, true)
    }
    fun setEnabled(context: Context, type: TaskType, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(type.name, enabled).apply()
    }
    fun all(context: Context): Map<TaskType, Boolean> {
        return TaskType.values().associateWith { isEnabled(context, it) }
    }
}
