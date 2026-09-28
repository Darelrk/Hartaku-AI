package com.example.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import com.example.ai.ChatLogger
import com.example.ai.ChatMessageItem
import com.example.ai.ChatbotRAGManager
import com.example.ai.FeedbackCollector
import com.example.ai.LocalRuleBasedChat
import com.example.ai.AgentProactiveEngine
import com.example.ai.AiInsightGenerator
import com.example.data.ConversationRepository
import com.example.data.AppContainer
import com.example.data.Budget
import com.example.data.BudgetRepository
import com.example.data.Category
import com.example.data.CategoryRepository
import com.example.data.CategoryTotal
import com.example.data.Transaction
import com.example.data.TransactionRepository
import com.example.data.TransactionType
import com.example.RupiahFormatter
import com.example.BuildConfig
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID
import com.example.data.ConversationMessage
import com.example.data.TransactionSeeder
import com.example.data.DailyExpense

/**
 * Per-day UI state. Satu instance per dayOffset (-2..+2).
 * X-axis pager render data hari sesuai offset-nya, bukan data hari ini semua.
 */
data class DayUiState(
    val dayOffset: Int = 0,
    val totalSpending: Double = 0.0,
    val totalIncome: Double = 0.0,
    val previousDaySpending: Double = 0.0,
    val transactionCount: Int = 0,
    val transactions: List<Transaction> = emptyList(),
    val categoryBreakdown: List<CategoryTotal> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val insightText: String = "",
    val insightSaran: String = "",
    val twoWeekExpense: TwoWeekExpense = TwoWeekExpense(emptyList(), 0.0, 0.0, null, 0.0),
    val loading: Boolean = true
) {
    val balance: Double get() = totalIncome - totalSpending
}

private data class DayPartial(
    val expense: Double,
    val income: Double,
    val count: Int,
    val txs: List<Transaction>,
    val breakdown: List<CategoryTotal>
)

class HomeViewModel(
    private val transactionRepo: TransactionRepository,
    private val budgetRepo: BudgetRepository,
    private val categoryRepo: CategoryRepository,
    private val insightGenerator: AiInsightGenerator?,
    private val chatbotRAGManager: ChatbotRAGManager? = null,
    private val localRuleBasedChat: LocalRuleBasedChat? = null,
    private val conversationRepository: ConversationRepository? = null,
    private val feedbackCollector: FeedbackCollector? = null,
    private val agentProactiveEngine: AgentProactiveEngine? = null
) : ViewModel() {

    private val dayCache = mutableMapOf<Int, MutableStateFlow<DayUiState>>()
    private val insightRequested = mutableSetOf<Int>()
    private var seeded = false
    // Chat state per day offset
    private val _dayChatHistories = MutableStateFlow<Map<Int, List<ChatMessageItem>>>(emptyMap())
    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()
    private val _feedbackIdMap = mutableMapOf<String, String>()  // "dayOffset:index" -> feedbackId

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    init {
        // ponytail: was runBlocking on the category query — it blocked the main thread while
        // HomeFeed composed, which delayed the first frame. The UI already collects this
        // flow, so an async load is transparent.
        viewModelScope.launch { _categories.value = categoryRepo.getAllActive() }
    }

    init {
        // Load chat history from DB on restart
        viewModelScope.launch {
            conversationRepository?.let { repo ->
                for (offset in -2..2) {
                    val msgs = repo.getMessages(offset.toString())
                    if (msgs.isNotEmpty()) {
                        _dayChatHistories.update { map ->
                            map + (offset to msgs.map { ChatMessageItem(it.role, it.content) })
                        }
                    }
                }
            }
        }
    }
    init {
    }

    private val _chatHistoryCache = mutableMapOf<Int, StateFlow<List<ChatMessageItem>>>()

    fun getChatHistory(dayOffset: Int): StateFlow<List<ChatMessageItem>> {
        return _chatHistoryCache.getOrPut(dayOffset) {
            _dayChatHistories.map { it[dayOffset] ?: emptyList() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        }
    }

    fun dayData(offset: Int): StateFlow<DayUiState> =
        dayCache.getOrPut(offset) {
            MutableStateFlow(DayUiState(dayOffset = offset)).also { loadDay(offset, it) }
        }.asStateFlow()

    private fun loadDay(offset: Int, flow: MutableStateFlow<DayUiState>) {
        val (start, end) = dayRange(offset)
        val (prevStart, prevEnd) = dayRange(offset - 1)

        viewModelScope.launch {
            combine(
                transactionRepo.getDailyExpense(start, end),
                transactionRepo.getDailyIncome(start, end),
                transactionRepo.getTransactionCount(start, end),
                transactionRepo.getTransactionsByDay(start, end),
                transactionRepo.getCategoryBreakdownById(start, end)
            ) { expense, income, count, txs, breakdown ->
                DayPartial(expense, income, count, txs, breakdown)
            }.combine(
                transactionRepo.getDailyExpense(prevStart, prevEnd)
            ) { p, prev -> p to prev }
                .collect { (p, prev) ->
                    flow.update {
                        it.copy(
                            totalSpending = p.expense,
                            totalIncome = p.income,
                            transactionCount = p.count,
                            transactions = p.txs,
                            categoryBreakdown = p.breakdown,
                            previousDaySpending = prev,
                            insightText = if (it.insightText.isBlank()) localInsight(p.expense, prev) else it.insightText,
                            loading = false
                        )
                    }
                    requestAiInsight(offset, p, prev, flow)
                }
        }

        // `Budget.spent` adalah field TERSIMPAN dan praktis selalu 0 — nilainya
        // hanya dihitung ulang oleh `withSpent`. Home dan DailyCheckWorker
        // sebelumnya membaca field mentah itu, sehingga KPI budget-vs-aktual di
        // chart dan alert harian menampilkan angka basi sementara layar
        // management sudah benar. Sekarang Home memakai hitungan yang sama.
        val (monthStart, monthEnd) = currentMonthRange()
        viewModelScope.launch {
            combine(
                budgetRepo.getAllBudgets(),
                transactionRepo.getTransactionsInRange(monthStart, monthEnd),
                transactionRepo.getIncomeInRange(monthStart, monthEnd)
            ) { budgets, txs, income ->
                budgets.withSpent(txs, income)
            }.collect { budgets ->
                flow.update { it.copy(budgets = budgets) }
            }
        }

        loadTwoWeek(flow)
    }

    /**
     * Chart 14 hari sebelumnya tidak pernah terisi: `DayUiState.twoWeekExpense`
     * hanya punya nilai default dan tidak ada satu pun tempat yang mengisinya,
     * sehingga slide itu selalu menampilkan "Belum ada data 14 hari" padahal
     * datanya ada.
     *
     * Pengelompokan memakai batas hari lokal yang sama dengan [dayRange] supaya
     * batarnya tidak bergeser sehari dari angka ringkasan, dan memakai interval
     * half-open [start, end) seperti query DAO.
     */
    private fun loadTwoWeek(flow: MutableStateFlow<DayUiState>) {
        val now = Calendar.getInstance()
        val firstDay = now.clone() as Calendar
        firstDay.add(Calendar.DAY_OF_YEAR, -13)
        val rangeStart = getStartOfDay(firstDay)
        val rangeEnd = getEndOfDay(now)

        viewModelScope.launch {
            transactionRepo.getTransactionsInRange(rangeStart, rangeEnd).collect { txs ->
                flow.update { it.copy(twoWeekExpense = buildTwoWeekExpense(txs, now)) }
            }
        }
    }


    private fun requestAiInsight(
        offset: Int,
        p: DayPartial,
        prev: Double,
        flow: MutableStateFlow<DayUiState>
    ) {
        val generator = insightGenerator ?: return
        if (p.expense == 0.0 && p.income == 0.0) return
        if (!insightRequested.add(offset)) return // sekali per hari, hindari spam API

        viewModelScope.launch {
            val topCat = p.breakdown.maxByOrNull { it.total }
            val data = AiInsightGenerator.InsightData(
                totalExpense = p.expense,
                yesterdayExpense = prev,
                totalIncome = p.income,
                transactionCount = p.count,
                topCategory = topCat?.name,
                topCategoryAmount = topCat?.total ?: 0.0
            )
            generator.generate(data).onSuccess { insight ->
                flow.update { it.copy(insightText = insight.insight, insightSaran = insight.saran) }
            }
        }
    }

    private fun localInsight(today: Double, yesterday: Double): String = when {
        today == 0.0 -> "Belum ada transaksi hari ini."
        yesterday > 0.0 -> {
            val diff = ((today - yesterday) / yesterday * 100).toInt()
            if (diff > 0) "Pengeluaran naik $diff% dibanding kemarin."
            else "Pengeluaran turun ${kotlin.math.abs(diff)}% dibanding kemarin. Hemat!"
        }
        else -> "Total pengeluaran ${RupiahFormatter.format(today)}."
    }

    fun deleteTransaction(tx: Transaction) {
        viewModelScope.launch { transactionRepo.deleteTransaction(tx) }
    }

    fun updateTransaction(tx: Transaction) {
        viewModelScope.launch { transactionRepo.updateTransaction(tx) }
    }

    private suspend fun saveChatMessage(dayOffset: Int, role: String, content: String) {
        conversationRepository?.let { repo ->
            val sessionId = dayOffset.toString()
            repo.addMessage(ConversationMessage(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                role = role,
                content = content,
                turnIndex = repo.getNextTurnIndex(sessionId),
                createdAt = System.currentTimeMillis()
            ))
        }
    }

    // ── Chat functions (per-day) ──
    fun sendChatMessage(dayOffset: Int, message: String) {
        val userMsg = ChatMessageItem("user", message)
        _dayChatHistories.update { map ->
            map + (dayOffset to ((map[dayOffset] ?: emptyList()) + userMsg))
        }
        viewModelScope.launch {
            _isChatLoading.value = true
            try {
                // ponytail: sebelumnya di luar try — kegagalan persist diam-diam membuat
                // pesan user tetap tampil tapi balasan tidak pernah keluar.
                saveChatMessage(dayOffset, "user", message)
                val dayHistory = _dayChatHistories.value[dayOffset] ?: emptyList()
                if (chatbotRAGManager != null) {
                    _dayChatHistories.update { map ->
                        map + (dayOffset to ((map[dayOffset] ?: emptyList()) + ChatMessageItem("assistant", "")))
                    }
                    var finalAnswer = ""
                    chatbotRAGManager.processQueryStream(message, dayHistory.dropLast(1))
                        .collect { delta ->
                            finalAnswer += delta
                            _dayChatHistories.update { map ->
                                val current = map[dayOffset]?.toMutableList() ?: mutableListOf()
                                if (current.isNotEmpty()) {
                                    current[current.size - 1] = ChatMessageItem("assistant", finalAnswer)
                                }
                                map + (dayOffset to current.toList())
                            }
                        }
                    // Validasi anti-halusinasi numerik setelah stream selesai.
                    // Stream menampilkan token mentah; baris terakhir di-refresh
                    // dengan jawaban tervalidasi.
                    finalAnswer = chatbotRAGManager.validateFinalAnswer(finalAnswer)
                    _dayChatHistories.update { map ->
                        val current = map[dayOffset]?.toMutableList() ?: mutableListOf()
                        if (current.isNotEmpty()) {
                            current[current.size - 1] = ChatMessageItem("assistant", finalAnswer)
                        }
                        map + (dayOffset to current.toList())
                    }
                    saveChatMessage(dayOffset, "assistant", finalAnswer)
                } else {
                    val answer = localRuleBasedChat?.processQuery(message, dayHistory.dropLast(1))
                        ?: "Maaf, AI tidak tersedia."
                    _dayChatHistories.update { map ->
                        map + (dayOffset to ((map[dayOffset] ?: emptyList()) + ChatMessageItem("assistant", answer)))
                    }
                    saveChatMessage(dayOffset, "assistant", answer)
                }
            } catch (e: Exception) {
                ChatLogger.e("HomeVM", "sendChatMessage", e)
                val errorMsg = "Maaf, terjadi kesalahan. Coba lagi."
                _dayChatHistories.update { map ->
                    val current = map[dayOffset]?.toMutableList() ?: mutableListOf()
                    val last = current.lastOrNull()
                    if (last != null && last.role == "assistant" && last.content.isEmpty()) {
                        current[current.size - 1] = ChatMessageItem("assistant", errorMsg)
                    } else {
                        current.add(ChatMessageItem("assistant", errorMsg))
                    }
                    map + (dayOffset to current.toList())
                }
                saveChatMessage(dayOffset, "assistant", errorMsg)
            } finally {
                _isChatLoading.value = false
            }
        }
    }

    fun rateMessage(dayOffset: Int, messageIndex: Int, rating: Int) {
        val feedbackId = _feedbackIdMap["$dayOffset:$messageIndex"] ?: return
        viewModelScope.launch {
            feedbackCollector?.rate(feedbackId, rating)
        }
    }

    /** Seed mock sekali kalau DB kosong, disebar -2..+1 supaya swipe X-axis kaya. */
    fun ensureMockData() {
        if (!BuildConfig.DEBUG || seeded) return
        seeded = true
        // ponytail: seeder yang sama dengan TransactionSeeder — versi lokal di sini
        // lupa mengisi categoryId, jadi breakdown Home jatuh ke "Uncategorized".
        viewModelScope.launch { TransactionSeeder.seedIfEmpty(transactionRepo) }
    }

    companion object {
        fun factory(context: android.content.Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val container = AppContainer.getInstance(context)
                    return HomeViewModel(
                        container.transactionRepository,
                        container.budgetRepository,
                        container.categoryRepository,
                        container.insightGenerator,
                        container.chatbotRAGManager,
                        container.localRuleBasedChat,
                        container.conversationRepository,
                        container.feedbackCollector,
                        container.agentProactiveEngine,
                    ) as T
                }
            }

        /**
         * Mengelompokkan transaksi menjadi 14 ember harian (hari-13 s.d. hari-0).
         * Interval half-open [start, end) mengikuti query DAO, dan batas hari
         * memakai zona lokal yang sama dengan ringkasan supaya batang chart
         * tidak bergeser sehari dari angka yang dilihat pengguna.
         */
        internal fun buildTwoWeekExpense(txs: List<Transaction>, now: Calendar): TwoWeekExpense {
            val dayStarts = (-13..0).map { offset ->
                val c = now.clone() as Calendar
                c.add(Calendar.DAY_OF_YEAR, offset)
                getStartOfDay(c)
            }
            val expenses = txs.filter { it.type == TransactionType.EXPENSE }
            val daily = dayStarts.mapIndexed { idx, dayStart ->
                val endExclusive = dayStarts.getOrNull(idx + 1) ?: getEndOfDay(now)
                val total = expenses
                    .filter { it.timestamp >= dayStart && it.timestamp < endExclusive }
                    .sumOf { it.amount }
                DailyExpense(dayStart, total)
            }
            val prevWeekTotal = daily.take(7).sumOf { it.total }
            val thisWeekTotal = daily.drop(7).sumOf { it.total }
            val deltaPct =
                if (prevWeekTotal == 0.0) null else (thisWeekTotal - prevWeekTotal) / prevWeekTotal * 100.0
            return TwoWeekExpense(
                daily = daily,
                thisWeekTotal = thisWeekTotal,
                prevWeekTotal = prevWeekTotal,
                deltaPct = deltaPct,
                avgDaily = thisWeekTotal / 7.0
            )
        }
    }
}

private fun dayRange(offset: Int): Pair<Long, Long> {
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, offset)
    return getStartOfDay(cal) to getEndOfDay(cal)
}

private fun getStartOfDay(cal: Calendar): Long {
    val c = cal.clone() as Calendar
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun getEndOfDay(cal: Calendar): Long {
    val c = cal.clone() as Calendar
    c.set(Calendar.HOUR_OF_DAY, 23)
    c.set(Calendar.MINUTE, 59)
    c.set(Calendar.SECOND, 59)
    c.set(Calendar.MILLISECOND, 999)
    return c.timeInMillis
}

fun currentMonthRange(): Pair<Long, Long> {
    val cal = Calendar.getInstance()
    cal.set(Calendar.DAY_OF_MONTH, 1)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val start = cal.timeInMillis
    cal.add(Calendar.MONTH, 1)
    val end = cal.timeInMillis
    return start to end
}

/**
 * Menghitung ulang `spent` — dan `amount` untuk budget berbasis persen — dari
 * transaksi nyata.
 *
 * `Budget.spent` adalah field TERSIMPAN dan praktis selalu 0; fungsi inilah satu-
 *-satunya tempat angka itu dihitung. Semua tampilan budget dan alert harian
 * harus lewat sini, bukan membaca field mentah.
 *
 * `period` dihormati: budget `weekly` hanya menghitung transaksi 7 hari
 * terakhir dan memakai income jendela yang sama sebagai basis persen.
 * Sebelumnya `period` disimpan tapi tidak pernah dipakai, sehingga budget
 * mingguan menampilkan pengeluaran dan target berbasis bulanan.
 */
fun List<Budget>.withSpent(
    transactions: List<Transaction>,
    monthlyIncome: Double,
    now: Long = System.currentTimeMillis()
): List<Budget> {
    val windowStart = now - 7L * 24 * 60 * 60 * 1000
    val budgets = this

    // Setiap transaksi hanya boleh dihitung untuk SATU budget. Fallback
    // kategori sebelumnya dijalankan per budget, jadi transaksi yang
    // kategorinya dipakai dua budget terhitung dua kali, dan transaksi yang
    // sudah punya budgetId eksplisit tetap jatuh ke fallback budget lain.
    // Hasilnya total budget-vs-aktual lebih besar dari pengeluaran nyata.
    //
    // Atribusi: budgetId eksplisit menang; kalau tidak ada, kategori memetakan
    // ke budget PERTAMA yang mengklaimnya.
    val categoryOwner = HashMap<String, Int>()
    for (b in budgets) {
        for (cid in b.parseCategoryIds()) {
            categoryOwner.putIfAbsent(cid, b.id)
        }
    }

    return map { budget ->
        val weekly = budget.period.equals("weekly", ignoreCase = true)
        val inWindow = transactions.filter { !weekly || it.timestamp >= windowStart }
        val expenses = inWindow.filter { it.type == TransactionType.EXPENSE }

        val spentByBudgetId = HashMap<Int, Double>()
        for (tx in expenses) {
            val owner = tx.budgetId ?: tx.categoryId?.let { categoryOwner[it] }
            if (owner != null) {
                spentByBudgetId[owner] = (spentByBudgetId[owner] ?: 0.0) + tx.amount
            }
        }

        val newSpent = spentByBudgetId[budget.id] ?: 0.0
        val baseIncome = if (weekly) {
            inWindow.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        } else {
            monthlyIncome
        }
        val newAmount = if (budget.percent > 0) baseIncome * budget.percent / 100 else budget.amount
        budget.copy(spent = newSpent, amount = newAmount)
    }
}
