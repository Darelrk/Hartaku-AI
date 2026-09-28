package com.example.data

import kotlinx.coroutines.flow.Flow

open class BudgetRepository(private val dao: BudgetDao) {
    open fun getAllBudgets(): Flow<List<Budget>> = dao.getAllBudgets()
    open fun getAllDeleted(): Flow<List<Budget>> = dao.getAllDeleted()
    open suspend fun count(): Int = dao.countBudgets()
    open suspend fun getById(id: Int): Budget? = dao.getById(id)
    open suspend fun insertBudget(budget: Budget) = dao.insertBudget(budget)
    open suspend fun insertAll(budgets: List<Budget>) = dao.insertAll(budgets)
    open suspend fun updateBudget(budget: Budget) = dao.updateBudget(budget)
    open suspend fun deleteBudget(budget: Budget) = dao.deleteBudget(budget)
    open suspend fun softDelete(id: Int, now: Long = System.currentTimeMillis()) = dao.softDelete(id, now)
    open suspend fun restore(id: Int) = dao.restore(id)
}
