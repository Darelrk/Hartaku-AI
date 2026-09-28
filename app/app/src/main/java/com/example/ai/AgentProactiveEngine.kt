package com.example.ai

import com.example.data.BillRepository
import com.example.data.BudgetRepository
import com.example.data.TransactionRepository
import kotlinx.coroutines.flow.first
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

/**
 * Executes proactive agent checks. Each check* returns a notification
 * message or null if the condition is not met.
 */
class AgentProactiveEngine(
    private val transactionRepository: TransactionRepository,
    private val budgetRepository: BudgetRepository,
    private val billRepository: BillRepository
) {
    private val fmt = NumberFormat.getNumberInstance(Locale("id", "ID"))

    suspend fun checkBudgetAlert(budget: BudgetInfo, nowMillis: Long): String? {
        if (budget.amount <= 0) return null
        val pct = (budget.spent / budget.amount * 100).toInt()
        if (pct >= 80) {
            return "Budget ${budget.name} sudah $pct% " +
                   "(Rp ${fmt.format(budget.spent.toLong())} dari Rp ${fmt.format(budget.amount.toLong())})"
        }
        return null
    }

    suspend fun checkBillReminder(bill: BillInfo): String? {
        if (!bill.isPaidThisMonth) {
            return "Tagihan ${bill.name} sebesar Rp ${fmt.format(bill.amount.toLong())} belum dibayar"
        }
        return null
    }

    suspend fun checkInactivityReminder(nowMillis: Long): String? {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis
        val endOfDay = startOfDay + 86400000

        val txCount = transactionRepository.getTransactionsInRange(startOfDay, endOfDay).first().size
        return if (txCount == 0) "Hari ini belum ada catatan pengeluaran. Yuk catat!" else null
    }

    suspend fun checkSpendingSummary(nowMillis: Long): String? {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis
        val endOfDay = startOfDay + 86400000

        val txs = transactionRepository.getTransactionsInRange(startOfDay, endOfDay).first()
        val expense = txs.filter { it.type.name == "EXPENSE" }.sumOf { it.amount }
        return "Ringkasan hari ini: Rp ${fmt.format(expense.toLong())}"
    }
}

data class BudgetInfo(val name: String, val amount: Double, val spent: Double)
data class BillInfo(val name: String, val amount: Double, val isPaidThisMonth: Boolean)
