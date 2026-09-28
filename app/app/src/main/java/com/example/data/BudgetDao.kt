package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE deletedAt IS NULL")
    fun getAllBudgets(): Flow<List<Budget>>

    @Query("SELECT * FROM budgets WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun getAllDeleted(): Flow<List<Budget>>

    // Harus memfilter `deletedAt IS NULL`, sama seperti getAllBudgets. Tanpa
    // ini caller yang mengandalkan getById bisa membaca dan mengedit budget
    // yang sudah diarsipkan.
    @Query("SELECT * FROM budgets WHERE id = :id AND deletedAt IS NULL")
    suspend fun getById(id: Int): Budget?

    @Insert
    suspend fun insertBudget(budget: Budget)

    @Insert
    suspend fun insertAll(budgets: List<Budget>)

    @Update
    suspend fun updateBudget(budget: Budget)

    @Delete
    suspend fun deleteBudget(budget: Budget)
    @Query("UPDATE budgets SET deletedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Int, now: Long)

    @Query("UPDATE budgets SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: Int)

    @Query("SELECT COUNT(*) FROM budgets WHERE deletedAt IS NULL")
    suspend fun countBudgets(): Int
}
