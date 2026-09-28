package com.example.data
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
@Database(
    entities = [Transaction::class, Budget::class, Category::class, AiCache::class, Bill::class, AgentFeedback::class,
        ConversationSession::class, ConversationMessage::class],
    version = 28,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun categoryDao(): CategoryDao
    abstract fun aiCacheDao(): AiCacheDao
    abstract fun billDao(): BillDao
    abstract fun agentFeedbackDao(): AgentFeedbackDao
    abstract fun conversationDao(): ConversationDao
    companion object {
        @Volatile
        private var Instance: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return Instance ?: synchronized(this) {
            System.loadLibrary("sqlcipher")

            val factory = SupportOpenHelperFactory(DatabaseKeyProvider.getOrCreateKey(context))
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "hartaku_database")
                .openHelperFactory(factory)
                .addMigrations(
                    MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
                    MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
                    MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17,
                    MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20,
                    MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23,
                    MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26,
                    MIGRATION_26_27, MIGRATION_27_28
                )
                .build()
                .also { Instance = it }
        }
    }
    }
}
