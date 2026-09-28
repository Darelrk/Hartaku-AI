package com.example.data

import android.content.Context
import com.example.ai.AiInsightGenerator
import com.example.ai.AudioRecorder
import com.example.ai.ChatbotRAGManager
import com.example.ai.LocalRuleBasedChat
import com.example.ai.NimApiClient
import com.example.ai.NimConfig
import com.example.ai.NimEmbeddingClient
import com.example.ai.ReceiptParser
import com.example.ai.TransactionAiParser
import com.example.ai.FeedbackCollector
import com.example.ai.AgentProfileProvider
import com.example.ai.AgentProactiveEngine
import com.example.work.DailyCheckWorker
import com.example.work.NotificationHelper
import com.example.ai.WhisperApiClient
import com.example.data.vector.TransactionVectorBox
import com.example.data.vector.TransactionVectorSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File

class AppContainer(context: Context) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database = AppDatabase.getDatabase(context)
    private val transactionDao = database.transactionDao()
    private val budgetDao = database.budgetDao()
    private val categoryDao = database.categoryDao()
    private val billDao = database.billDao()
    private val conversationDao = database.conversationDao()
    private val agentFeedbackDao = database.agentFeedbackDao()

    val aiMode: AiMode = AiMode.load(context)

    // API Keys
    private val nimApiKey: String? = NimConfig.getApiKey()
    private val groqApiKey: String? = NimConfig.getGroqApiKey()

    // Build the embedding client up-front. Null when no API key.
    private val nimEmbeddingClient: NimEmbeddingClient? = nimApiKey?.let {
        NimEmbeddingClient(it)
    }

    // Build the repository first (no hooks yet); the sync hooks are wired in init.
    val transactionRepository: TransactionRepository = TransactionRepository(transactionDao)

    val budgetRepository = BudgetRepository(budgetDao)
    val categoryRepository = CategoryRepository(categoryDao)
    val billRepository = BillRepository(billDao)

    // Vector sync — wraps hooks around the existing repository.
    private val transactionVectorSync: TransactionVectorSync = TransactionVectorSync(
        transactionRepo = transactionRepository,
        embeddingClient = nimEmbeddingClient,
        vectorBox = TransactionVectorBox
    )

    init {
        // ObjectBox must be initialized before any box operation.
        NotificationHelper.createChannel(context)
        // Schedule daily agent checks (safe in test env — WorkManager not init'd)
        try { DailyCheckWorker.schedule(context) } catch (_: Exception) {}
        TransactionVectorBox.init(context)

        // Re-wire repository hooks (overrides the no-op defaults from the constructor).
        transactionRepository.setHooks(
            onInserted = { tx -> transactionVectorSync.onTransactionUpserted(tx) },
            onUpdated = { tx -> transactionVectorSync.onTransactionUpserted(tx) },
            onSoftDeleted = { id -> transactionVectorSync.onTransactionSoftDeleted(id) }
        )

        // First-launch seeding: only default categories (master data).
        applicationScope.launch {
            CategorySeeder.seedIfEmpty(categoryRepository)
        }
        // Vector sync: backfill existing transactions then mirror future changes.
        applicationScope.launch {
            transactionVectorSync.backfillAll()
            transactionVectorSync.observeAndSync()
        }
    }

    // AI Services — NIM LLM (Transaction Parsing)
    val nimApiClient: NimApiClient? = nimApiKey?.let { NimApiClient(it) }
    val transactionAiParser: TransactionAiParser? = nimApiClient?.let { client ->
        TransactionAiParser(client, database.aiCacheDao())
    }

    // AI Services — Receipt Scanning (model vision NIM)
    val receiptParser: ReceiptParser? = nimApiKey?.let { ReceiptParser(it) }

    // AI Services — Insight Generation
    val insightGenerator: AiInsightGenerator? = nimApiClient?.let { AiInsightGenerator(it) }
    val conversationRepository = ConversationRepository(conversationDao)
    val feedbackCollector = FeedbackCollector(agentFeedbackDao)
    val agentProfileProvider = AgentProfileProvider(agentFeedbackDao)
    val agentProactiveEngine = AgentProactiveEngine(transactionRepository, budgetRepository, billRepository)

    val chatbotRAGManager: ChatbotRAGManager? = nimApiClient?.let {
        ChatbotRAGManager(it, transactionRepository, nimEmbeddingClient, conversationRepository, agentProfileProvider)
    }
    val localRuleBasedChat: LocalRuleBasedChat =
        LocalRuleBasedChat(transactionRepository)
    val whisperApiClient: WhisperApiClient? = groqApiKey?.let { WhisperApiClient(it) }
    val audioRecorder: AudioRecorder = AudioRecorder(context, File(context.cacheDir, "audio_recordings"))
    companion object {
        @Volatile
        private var instance: AppContainer? = null

        fun getInstance(context: Context): AppContainer {
            return instance ?: synchronized(this) {
                AppContainer(context.applicationContext).also { instance = it }
            }
        }

        fun resetInstance() {
            instance?.applicationScope?.cancel()
            instance = null
        }
    }
}
