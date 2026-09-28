package com.example.data

import kotlinx.coroutines.flow.Flow

open class BillRepository(private val billDao: BillDao) {
    open fun getActiveBillsForMonth(currentMonthMillis: Long): Flow<List<Bill>> = billDao.getActiveBillsForMonth(currentMonthMillis)
    open suspend fun insertBill(bill: Bill) = billDao.insert(bill)
    open suspend fun markBillPaid(billId: Int, isPaid: Boolean) = billDao.updatePaidStatus(billId, isPaid)
    open suspend fun resetMonthlyPaidStatus() = billDao.resetAllPaidStatus()
}
