package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL ORDER BY timestamp DESC LIMIT 50")
    fun getRecentTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL AND timestamp >= :startOfDay AND timestamp < :endOfDay ORDER BY timestamp DESC")
    fun getTransactionsByDay(startOfDay: Long, endOfDay: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL AND timestamp >= :startDay AND timestamp < :endDay ORDER BY timestamp DESC")
    fun getTransactionsInRange(startDay: Long, endDay: Long): Flow<List<Transaction>>


    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL AND description LIKE '%' || :keyword || '%' ORDER BY timestamp DESC LIMIT 50")
    fun getTransactionsByKeyword(keyword: String): Flow<List<Transaction>>

    /**
     * Look up transactions by category display name. Resolves the name to a
     * UUID via categories table JOIN — source of truth is categoryId after
     * Gap 1 (v15). Kept for callers that still have a String category
     * (e.g. AI tool calls that return category labels, not UUIDs).
     */
    @Query("SELECT t.* FROM transactions t LEFT JOIN categories c ON t.categoryId = c.id WHERE t.deletedAt IS NULL AND LOWER(c.name) = LOWER(:categoryName) ORDER BY t.timestamp DESC LIMIT 50")
    fun getTransactionsByCategoryName(categoryName: String): Flow<List<Transaction>>


    @Insert
    suspend fun insertTransaction(transaction: Transaction)

    @Query("UPDATE transactions SET deletedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Int, now: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: Int)

    @Query("SELECT * FROM transactions WHERE deletedAt IS NOT NULL ORDER BY timestamp DESC")
    fun getAllDeleted(): Flow<List<Transaction>>

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE deletedAt IS NULL AND type = 'EXPENSE' AND timestamp >= :startOfDay AND timestamp < :endOfDay")
    fun getDailyExpense(startOfDay: Long, endOfDay: Long): Flow<Double>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE deletedAt IS NULL AND type = 'INCOME' AND timestamp >= :startOfDay AND timestamp < :endOfDay")
    fun getDailyIncome(startOfDay: Long, endOfDay: Long): Flow<Double>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE deletedAt IS NULL AND type = 'EXPENSE' AND timestamp >= :startDay AND timestamp < :endDay")
    fun getExpenseInRange(startDay: Long, endDay: Long): Flow<Double>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE deletedAt IS NULL AND type = 'INCOME' AND timestamp >= :startDay AND timestamp < :endDay")
    fun getIncomeInRange(startDay: Long, endDay: Long): Flow<Double>


    // Group by categoryId (UUID FK) — single source of truth for category totals.
    // Replaces the legacy string-based `getCategoryBreakdown` (removed in v15)
    // which couldn't handle free-text categories that don't match a Category row.
    @Query("SELECT COALESCE(c.name, 'Uncategorized') as name, COALESCE(SUM(t.amount), 0) as total, t.categoryId as categoryId FROM transactions t LEFT JOIN categories c ON t.categoryId = c.id WHERE t.deletedAt IS NULL AND t.type = 'EXPENSE' AND t.timestamp >= :startOfDay AND t.timestamp < :endOfDay GROUP BY t.categoryId ORDER BY total DESC")
    fun getCategoryBreakdownById(startOfDay: Long, endOfDay: Long): Flow<List<CategoryTotal>>
    @Query("SELECT COALESCE(c.name, 'Uncategorized') as name, COALESCE(SUM(t.amount), 0) as total, t.categoryId as categoryId FROM transactions t LEFT JOIN categories c ON t.categoryId = c.id WHERE t.deletedAt IS NULL AND t.type = 'INCOME' AND t.timestamp >= :startOfDay AND t.timestamp < :endOfDay GROUP BY t.categoryId ORDER BY total DESC")
    fun getIncomeBreakdownById(startOfDay: Long, endOfDay: Long): Flow<List<CategoryTotal>>

    @Query("SELECT COALESCE(COUNT(*), 0) FROM transactions WHERE deletedAt IS NULL AND timestamp >= :startOfDay AND timestamp < :endOfDay")
    fun getTransactionCount(startOfDay: Long, endOfDay: Long): Flow<Int>

    // Sidik jari isi tabel untuk invalidasi cache AI. Nama alias harus sama
    // persis dengan property DataFingerprint agar Room memetakan otomatis.
    @Query("SELECT COUNT(*) AS txnCount, COALESCE(SUM(amount), 0) AS totalAmount, COALESCE(MAX(timestamp), 0) AS latestTimestamp FROM transactions WHERE deletedAt IS NULL")
    suspend fun activeDataFingerprint(): DataFingerprint
}

data class CategoryTotal(
    val name: String,
    val total: Double,
    val categoryId: String? = null
)

