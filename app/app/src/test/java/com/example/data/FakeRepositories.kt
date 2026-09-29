package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeTransactionRepository : TransactionRepository(StubTransactionDao()) {
    val transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactionRangeCalls = mutableListOf<Pair<Long, Long>>()

    override fun getAllTransactions(): Flow<List<Transaction>> = transactions

    override fun getRecentTransactions(): Flow<List<Transaction>> {
        return transactions.map { it.take(10) }
    }

    override fun getTransactionsByDay(startOfDay: Long, endOfDay: Long): Flow<List<Transaction>> {
        return transactions.map { list ->
            // Half-open, meniru SQL DAO `timestamp >= :start AND timestamp < :end`.
            // Jangan pakai `..` di sini: batas inklusif membuat totals
            // expenditures dan income berbeda pada rentang yang sama.
            list.filter { it.timestamp in startOfDay until endOfDay }
        }
    }

    override suspend fun insertTransaction(transaction: Transaction) {
        val current = transactions.value.toMutableList()
        current.add(transaction)
        transactions.value = current
    }

    override suspend fun updateTransaction(transaction: Transaction) {
        val current = transactions.value.toMutableList()
        val index = current.indexOfFirst { it.id == transaction.id }
        if (index != -1) {
            current[index] = transaction
            transactions.value = current
        }
    }

    override suspend fun deleteTransaction(transaction: Transaction) {
        val current = transactions.value.toMutableList()
        current.removeAll { it.id == transaction.id }
        transactions.value = current
    }

    override fun getDailyExpense(startOfDay: Long, endOfDay: Long): Flow<Double> {
        return getTransactionsByDay(startOfDay, endOfDay).map { list ->
            list.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        }
    }

    override fun getDailyIncome(startOfDay: Long, endOfDay: Long): Flow<Double> {
        return getTransactionsByDay(startOfDay, endOfDay).map { list ->
            list.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        }
    }

    override fun getExpenseInRange(startDay: Long, endDay: Long): Flow<Double> {
        return transactions.map { list ->
            list.filter { it.type == TransactionType.EXPENSE && it.timestamp in startDay until endDay }.sumOf { it.amount }
        }
    }

    // ponytail: delegates getCategoryBreakdownById to an internal helper
    private fun categoryBreakdown(startOfDay: Long, endOfDay: Long): List<CategoryTotal> {
        val list = transactions.value.filter {
            it.type == TransactionType.EXPENSE && it.timestamp in startOfDay until endOfDay
        }
        return list.groupBy { it.category }.map { (catName, trans) ->
            CategoryTotal(name = catName, total = trans.sumOf { it.amount }, categoryId = catName)
        }
    }

    override fun getCategoryBreakdownById(startOfDay: Long, endOfDay: Long): Flow<List<CategoryTotal>> {
        return getTransactionsByDay(startOfDay, endOfDay).map { list ->
            list.filter { it.type == TransactionType.EXPENSE }
                .groupBy { it.category }
                .map { (catName, trans) ->
                    CategoryTotal(name = catName, total = trans.sumOf { it.amount }, categoryId = catName)
                }
        }
    }

    override fun getIncomeBreakdownById(startOfDay: Long, endOfDay: Long): Flow<List<CategoryTotal>> {
        return getTransactionsByDay(startOfDay, endOfDay).map { list ->
            list.filter { it.type == TransactionType.INCOME }
                .groupBy { it.category }
                .map { (catName, trans) ->
                    CategoryTotal(name = catName, total = trans.sumOf { it.amount }, categoryId = catName)
                }
        }
    }


    override suspend fun softDelete(id: Int, now: Long) {
        val current = transactions.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) current[index] = current[index].copy(deletedAt = now)
        transactions.value = current
    }

    override suspend fun restore(id: Int) {
        val current = transactions.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) current[index] = current[index].copy(deletedAt = null)
        transactions.value = current
    }

    override fun getAllDeleted(): Flow<List<Transaction>> {
        return transactions.map { list -> list.filter { it.deletedAt != null } }
    }

    override fun getTransactionsInRange(startDay: Long, endDay: Long): Flow<List<Transaction>> {
        transactionRangeCalls += startDay to endDay
        return transactions.map { list ->
            list.filter { it.timestamp in startDay until endDay }
        }
    }


    override fun getTransactionsByKeyword(keyword: String): Flow<List<Transaction>> {
        return transactions.map { list ->
            list.filter { it.description.contains(keyword, ignoreCase = true) }
        }
    }

    override fun getTransactionsByCategoryName(categoryName: String): Flow<List<Transaction>> {
        return transactions.map { list ->
            list.filter { it.category.equals(categoryName, ignoreCase = true) }
        }
    }

    override fun getIncomeInRange(startDay: Long, endDay: Long): Flow<Double> {
        return transactions.map { list ->
            list.filter { it.type == TransactionType.INCOME && it.timestamp in startDay until endDay }
                .sumOf { it.amount }
        }
    }

    override fun getTransactionCount(startOfDay: Long, endOfDay: Long): Flow<Int> {
        return getTransactionsByDay(startOfDay, endOfDay).map { it.size }
    }

    override suspend fun dataFingerprint(): String {
        val active = transactions.value.filter { it.deletedAt == null }
        return DataFingerprint(
            txnCount = active.size,
            totalAmount = active.sumOf { it.amount },
            latestTimestamp = active.maxOfOrNull { it.timestamp } ?: 0L
        ).asKey()
    }
}

class FakeBudgetRepository : BudgetRepository(StubBudgetDao()) {
    val budgets = MutableStateFlow<List<Budget>>(emptyList())

    override fun getAllBudgets(): Flow<List<Budget>> = budgets.map { list ->
        list.filter { it.deletedAt == null }
    }

    override suspend fun insertBudget(budget: Budget) {
        val current = budgets.value.toMutableList()
        current.removeAll { it.id == budget.id }
        current.add(budget)
        budgets.value = current
    }

    override suspend fun deleteBudget(budget: Budget) {
        val current = budgets.value.toMutableList()
        current.removeAll { it.id == budget.id }
        budgets.value = current
    }


    override fun getAllDeleted(): Flow<List<Budget>> = budgets.map { list ->
        list.filter { it.deletedAt != null }.sortedByDescending { it.deletedAt }
    }
    override suspend fun count(): Int = budgets.value.count { it.deletedAt == null }

    // getById harus menyembunyikan budget terarsip, sama seperti query SQL-nya.
    override suspend fun getById(id: Int): Budget? =
        budgets.value.firstOrNull { it.id == id && it.deletedAt == null }


    override suspend fun insertAll(budgetsList: List<Budget>) {
        val current = budgets.value.toMutableList()
        current.removeAll { existing -> budgetsList.any { it.id == existing.id } }
        current.addAll(budgetsList)
        budgets.value = current
    }

    override suspend fun updateBudget(budget: Budget) {
        insertBudget(budget)
    }

    override suspend fun softDelete(id: Int, now: Long) {
        val current = budgets.value.map {
            if (it.id == id) it.copy(deletedAt = now) else it
        }
        budgets.value = current
    }

    override suspend fun restore(id: Int) {
        val current = budgets.value.map {
            if (it.id == id) it.copy(deletedAt = null) else it
        }
        budgets.value = current
    }
}

class FakeCategoryRepository : CategoryRepository(StubCategoryDao()) {
    val categories = mutableListOf<Category>()
    var transactionsProvider: () -> List<Transaction> = { emptyList() }

    override suspend fun getAllActive(): List<Category> {
        return categories.filter { it.deletedAt == null }
            .sortedWith(compareBy({ it.sortOrder }, { it.name }))
    }

    override suspend fun getRootCategories(): List<Category> {
        return categories.filter { it.parentId == null && it.deletedAt == null }
    }

    override suspend fun getChildren(parentId: String): List<Category> {
        return categories.filter { it.parentId == parentId && it.deletedAt == null }
    }

    override suspend fun getById(id: String): Category? {
        return categories.firstOrNull { it.id == id && it.deletedAt == null }
    }

    override suspend fun getBySlug(slug: String): Category? {
        return categories.firstOrNull { it.slug == slug && it.deletedAt == null }
    }

    override suspend fun getByTypeClass(typeClass: String): List<Category> {
        return categories.filter { it.typeClass == typeClass && it.deletedAt == null }
    }

    override suspend fun search(query: String): List<Category> {
        val lower = query.lowercase()
        return categories.filter { it.deletedAt == null && (it.name.lowercase().contains(lower) || it.slug.lowercase().contains(lower)) }
    }

    override suspend fun insert(category: Category) {
        categories.removeAll { it.id == category.id }
        categories.add(category)
    }

    override suspend fun insertAll(categories: List<Category>) {
        categories.forEach { insert(it) }
    }

    override suspend fun update(category: Category) {
        insert(category)
    }

    override suspend fun softDelete(id: String) {
        val index = categories.indexOfFirst { it.id == id }
        if (index != -1) {
            val cat = categories[index]
            categories[index] = cat.copy(deletedAt = System.currentTimeMillis())
        }
    }

    override suspend fun rename(id: String, name: String, slug: String) {
        val index = categories.indexOfFirst { it.id == id }
        if (index != -1) {
            val cat = categories[index]
            categories[index] = cat.copy(name = name, slug = slug)
        }
    }

    override suspend fun count(): Int {
        return categories.count { it.deletedAt == null }
    }

    override suspend fun countIncludingDeleted(): Int = categories.size


    override suspend fun getAllDeleted(): List<Category> {
        return categories.filter { it.deletedAt != null }.sortedByDescending { it.deletedAt }
    }

    override suspend fun restore(id: String) {
        val index = categories.indexOfFirst { it.id == id }
        if (index != -1) {
            categories[index] = categories[index].copy(deletedAt = null)
        }
    }
    override suspend fun getOrCreateByName(name: String, typeClass: String): Category {
        val trimmedName = name.trim()
        val capitalizedName = trimmedName.lowercase().replaceFirstChar { it.uppercase() }
        val generatedSlug = capitalizedName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        
        val existing = getAllActive().firstOrNull { it.name.equals(capitalizedName, ignoreCase = true) || it.slug == generatedSlug }
        if (existing != null) {
            return existing
        }
        
        val uuid = java.util.UUID.randomUUID().toString()
        val newCategory = Category(
            id = uuid,
            parentId = null,
            name = capitalizedName,
            slug = generatedSlug,
            typeClass = typeClass,
            aliases = "[]",
            icon = "category",
            color = "#95A5A6",
            sortOrder = 50
        )
        insert(newCategory)
        return newCategory
    }
}

class FakeBillRepository : BillRepository(StubBillDao()) {
    val bills = MutableStateFlow<List<Bill>>(emptyList())
    override fun getActiveBillsForMonth(currentMonthMillis: Long): Flow<List<Bill>> {
        return bills.map { list ->
            list.filter { bill ->
                bill.recurrenceMode == RecurrenceMode.FOREVER ||
                bill.recurrenceMode == RecurrenceMode.ONCE ||
                (bill.recurrenceMode == RecurrenceMode.CUSTOM_RANGE &&
                    (bill.rangeEndMonthMillis == null || bill.rangeEndMonthMillis >= currentMonthMillis))
            }
        }
    }

    override suspend fun insertBill(bill: Bill) {
        val current = bills.value.toMutableList()
        val newBill = if (bill.id == 0) {
            val nextId = (current.maxOfOrNull { it.id } ?: 0) + 1
            bill.copy(id = nextId)
        } else {
            bill
        }
        current.removeAll { it.id == newBill.id }
        current.add(newBill)
        bills.value = current
    }

    override suspend fun markBillPaid(billId: Int, isPaid: Boolean) {
        val current = bills.value.map {
            if (it.id == billId) it.copy(isPaidThisMonth = isPaid) else it
        }
        bills.value = current
    }

    override suspend fun resetMonthlyPaidStatus() {
        val current = bills.value.map {
            it.copy(isPaidThisMonth = false)
        }
        bills.value = current
    }
}

// ── Stub DAOs for fake repo constructors ──
class StubTransactionDao : TransactionDao {
    override fun getAllTransactions(): Flow<List<Transaction>> = MutableStateFlow(emptyList())
    override fun getRecentTransactions(): Flow<List<Transaction>> = MutableStateFlow(emptyList())
    override fun getTransactionsByDay(startOfDay: Long, endOfDay: Long): Flow<List<Transaction>> = MutableStateFlow(emptyList())
    override fun getTransactionsInRange(startDay: Long, endDay: Long): Flow<List<Transaction>> = MutableStateFlow(emptyList())
    override fun getTransactionsByKeyword(keyword: String): Flow<List<Transaction>> = MutableStateFlow(emptyList())
    override fun getTransactionsByCategoryName(categoryName: String): Flow<List<Transaction>> = MutableStateFlow(emptyList())
    override suspend fun insertTransaction(transaction: Transaction) = Unit
    override suspend fun deleteTransaction(transaction: Transaction) = Unit
    override suspend fun updateTransaction(transaction: Transaction) = Unit
    override fun getDailyExpense(startOfDay: Long, endOfDay: Long): Flow<Double> = MutableStateFlow(0.0)
    override fun getExpenseInRange(startDay: Long, endDay: Long): Flow<Double> = MutableStateFlow(0.0)
    override suspend fun softDelete(id: Int, now: Long) = Unit
    override suspend fun restore(id: Int) = Unit
    override fun getAllDeleted(): Flow<List<Transaction>> = MutableStateFlow(emptyList())
    override fun getDailyIncome(startOfDay: Long, endOfDay: Long): Flow<Double> = MutableStateFlow(0.0)
    override fun getIncomeInRange(startDay: Long, endDay: Long): Flow<Double> = MutableStateFlow(0.0)
    override fun getCategoryBreakdownById(startOfDay: Long, endOfDay: Long): Flow<List<CategoryTotal>> = MutableStateFlow(emptyList())
    override fun getIncomeBreakdownById(startOfDay: Long, endOfDay: Long): Flow<List<CategoryTotal>> = MutableStateFlow(emptyList())
    override fun getTransactionCount(startOfDay: Long, endOfDay: Long): Flow<Int> = MutableStateFlow(0)
    override suspend fun activeDataFingerprint(): DataFingerprint = DataFingerprint(0, 0.0, 0L)
}
class StubBudgetDao : BudgetDao {
    override fun getAllBudgets(): Flow<List<Budget>> = MutableStateFlow(emptyList())
    override fun getAllDeleted(): Flow<List<Budget>> = MutableStateFlow(emptyList())
    override suspend fun updateBudget(budget: Budget) = Unit
    override suspend fun countBudgets(): Int = 0
    override suspend fun getById(id: Int): Budget? = null
    override suspend fun insertBudget(budget: Budget) = Unit
    override suspend fun insertAll(budgets: List<Budget>) = Unit
    override suspend fun deleteBudget(budget: Budget) = Unit
    override suspend fun softDelete(id: Int, now: Long) = Unit
    override suspend fun restore(id: Int) = Unit
}
class StubCategoryDao : CategoryDao {
    override suspend fun getAllActive(): List<Category> = emptyList()
    override suspend fun getRootCategories(): List<Category> = emptyList()
    override suspend fun getChildren(parentId: String): List<Category> = emptyList()
    override suspend fun getById(id: String): Category? = null
    override suspend fun getBySlug(slug: String): Category? = null
    override suspend fun getByTypeClass(typeClass: String): List<Category> = emptyList()
    override suspend fun search(query: String): List<Category> = emptyList()
    override suspend fun insert(category: Category) = Unit
    override suspend fun insertAll(categories: List<Category>) = Unit
    override suspend fun update(category: Category) = Unit
    override suspend fun softDelete(id: String, timestamp: Long) = Unit
    override suspend fun rename(id: String, name: String, slug: String, timestamp: Long) = Unit
    override suspend fun count(): Int = 0
    override suspend fun countIncludingDeleted(): Int = 0
    override suspend fun getAllDeleted(): List<Category> = emptyList()
    override suspend fun restore(id: String, timestamp: Long) = Unit
}
class StubBillDao : BillDao {
    override fun getActiveBillsForMonth(currentMonthMillis: Long): Flow<List<Bill>> = MutableStateFlow(emptyList())
    override suspend fun insert(bill: Bill) = Unit
    override suspend fun updatePaidStatus(billId: Int, isPaid: Boolean) = Unit
    override suspend fun resetAllPaidStatus() = Unit
}
