package com.example.data

import kotlinx.coroutines.flow.Flow

open class TransactionRepository(
    private val dao: TransactionDao,
    private var onInserted: (Transaction) -> Unit = {},
    private var onUpdated: (Transaction) -> Unit = {},
    private var onSoftDeleted: (Int) -> Unit = {}
) {
    /**
     * Replace the no-op hooks with real callbacks. Used by [com.example.data.AppContainer]
     * after the sync layer is constructed, to break the circular init order
     * (sync needs repo, repo's hooks need sync).
     */
    fun setHooks(
        onInserted: (Transaction) -> Unit,
        onUpdated: (Transaction) -> Unit,
        onSoftDeleted: (Int) -> Unit
    ) {
        this.onInserted = onInserted
        this.onUpdated = onUpdated
        this.onSoftDeleted = onSoftDeleted
    }

    open fun getAllTransactions(): Flow<List<Transaction>> = dao.getAllTransactions()
    open fun getRecentTransactions(): Flow<List<Transaction>> = dao.getRecentTransactions()
    open fun getTransactionsByDay(startOfDay: Long, endOfDay: Long): Flow<List<Transaction>> = dao.getTransactionsByDay(startOfDay, endOfDay)
    open fun getTransactionsInRange(startDay: Long, endDay: Long): Flow<List<Transaction>> = dao.getTransactionsInRange(startDay, endDay)
    open fun getTransactionsByKeyword(keyword: String): Flow<List<Transaction>> = dao.getTransactionsByKeyword(keyword)
    open fun getTransactionsByCategoryName(categoryName: String): Flow<List<Transaction>> = dao.getTransactionsByCategoryName(categoryName)
    open suspend fun insertTransaction(transaction: Transaction) {
        // Titik satu-satunya yang dilalui manual, suara, dan AI parser.
        // Tanpa guard di sini, teks tanpa nominal (extractAmount mengembalikan
        // 0.0) tersimpan sebagai transaksi Rp 0 yang ikut diam-diam mencemari
        // setiap total, breakdown kategori, dan konteks AI.
        require(transaction.amount.isFinite() && transaction.amount > 0.0) {
            "Nominal transaksi harus lebih besar dari nol"
        }
        dao.insertTransaction(transaction)
        onInserted(transaction)
    }
    open suspend fun deleteTransaction(transaction: Transaction) = dao.deleteTransaction(transaction)
    open suspend fun softDelete(id: Int, now: Long = System.currentTimeMillis()) {
        dao.softDelete(id, now)
        onSoftDeleted(id)
    }
    open fun getAllDeleted(): Flow<List<Transaction>> = dao.getAllDeleted()
    open suspend fun updateTransaction(transaction: Transaction) {
        dao.updateTransaction(transaction)
        onUpdated(transaction)
    }
    open suspend fun restore(id: Int) = dao.restore(id)
    open fun getDailyExpense(startOfDay: Long, endOfDay: Long): Flow<Double> = dao.getDailyExpense(startOfDay, endOfDay)
    open fun getDailyIncome(startOfDay: Long, endOfDay: Long): Flow<Double> = dao.getDailyIncome(startOfDay, endOfDay)
    open fun getExpenseInRange(startDay: Long, endDay: Long): Flow<Double> = dao.getExpenseInRange(startDay, endDay)
    open fun getIncomeInRange(startDay: Long, endDay: Long): Flow<Double> = dao.getIncomeInRange(startDay, endDay)
    open fun getCategoryBreakdownById(startOfDay: Long, endOfDay: Long): Flow<List<CategoryTotal>> = dao.getCategoryBreakdownById(startOfDay, endOfDay)
    open fun getIncomeBreakdownById(startOfDay: Long, endOfDay: Long): Flow<List<CategoryTotal>> = dao.getIncomeBreakdownById(startOfDay, endOfDay)
    open fun getTransactionCount(startOfDay: Long, endOfDay: Long): Flow<Int> = dao.getTransactionCount(startOfDay, endOfDay)

    /**
     * Sidik jari data untuk invalidasi cache AI. Kegagalan tidak boleh
     * menggagalkan pemanggil — kembalikan string kosong agar cache bust sekali
     * jalan, bukan error fatal.
     */
    open suspend fun dataFingerprint(): String =
        runCatching { dao.activeDataFingerprint().asKey() }.getOrDefault("")
}
