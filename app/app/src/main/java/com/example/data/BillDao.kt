package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {
    @Query("SELECT * FROM bills WHERE (recurrenceMode = 'FOREVER' OR recurrenceMode = 'ONCE' OR (recurrenceMode = 'CUSTOM_RANGE' AND (rangeEndMonthMillis IS NULL OR rangeEndMonthMillis >= :currentMonthMillis)))")
    fun getActiveBillsForMonth(currentMonthMillis: Long): Flow<List<Bill>>

    @Query("UPDATE bills SET isPaidThisMonth = :isPaid WHERE id = :billId")
    suspend fun updatePaidStatus(billId: Int, isPaid: Boolean)

    @Query("UPDATE bills SET isPaidThisMonth = 0")
    suspend fun resetAllPaidStatus()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(bill: Bill)
}