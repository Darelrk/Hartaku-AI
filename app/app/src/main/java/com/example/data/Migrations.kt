package com.example.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 8 → 9
 *
 * Adds soft-delete support to `budgets` (mirrors the existing pattern on `categories`).
 */
val MIGRATION_8_9: Migration = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE budgets ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE budgets ADD COLUMN deletedAt INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_budgets_deletedAt ON budgets (deletedAt)")
    }
}

/** Migration 9 → 10 — adds optional `note` column to `budgets`. */
val MIGRATION_9_10: Migration = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE budgets ADD COLUMN note TEXT")
    }
}

/** Migration 10 → 11 — creates the `bills` table. */
val MIGRATION_10_11: Migration = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `bills` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`amount` REAL NOT NULL, " +
                "`dueDate` INTEGER NOT NULL, " +
                "`categoryId` INTEGER NOT NULL, " +
                "`isPaidThisMonth` INTEGER NOT NULL DEFAULT 0, " +
                "`notifyBeforeDays` INTEGER NOT NULL DEFAULT 1, " +
                "`recurrenceMode` TEXT NOT NULL DEFAULT 'ONCE', " +
                "`rangeEndMonthMillis` INTEGER" +
            ")"
        )
    }
}

/** Migration 11 → 12 — no-op placeholder. */
val MIGRATION_11_12: Migration = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Intentionally empty: v12 schema identical to v11.
    }
}

/**
 * Migration 12 → 13
 *
 * Changes `bills.categoryId` from `INTEGER` to `TEXT` (nullable UUID FK).
 * Backfills by name → UUID mapping.
 */
val MIGRATION_12_13: Migration = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `bills_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`amount` REAL NOT NULL, " +
                "`dueDate` INTEGER NOT NULL, " +
                "`categoryId` TEXT, " +
                "`isPaidThisMonth` INTEGER NOT NULL DEFAULT 0, " +
                "`notifyBeforeDays` INTEGER NOT NULL DEFAULT 1, " +
                "`recurrenceMode` TEXT NOT NULL DEFAULT 'ONCE', " +
                "`rangeEndMonthMillis` INTEGER, " +
                "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL" +
            ")"
        )
        db.execSQL(
            "INSERT INTO `bills_new` (id, name, amount, dueDate, categoryId, isPaidThisMonth, notifyBeforeDays, recurrenceMode, rangeEndMonthMillis) " +
                "SELECT id, name, amount, dueDate, " +
                "CASE name " +
                "  WHEN 'Listrik PLN' THEN 'cat_tagihan' " +
                "  WHEN 'Internet Indihome' THEN 'cat_tagihan' " +
                "  WHEN 'Air PDAM' THEN 'cat_tagihan' " +
                "  WHEN 'BPJS Kesehatan' THEN 'cat_asuransi' " +
                "  WHEN 'Spotify Premium' THEN 'cat_hiburan' " +
                "  WHEN 'Netflix' THEN 'cat_hiburan' " +
                "  WHEN 'Gym Membership' THEN 'cat_olahraga' " +
                "  WHEN 'KPR Apartemen' THEN 'cat_tagihan' " +
                "  WHEN 'Kredit Motor' THEN 'cat_transport' " +
                "  WHEN 'Kredit Laptop' THEN 'cat_belanja' " +
                "  WHEN 'Service AC' THEN 'cat_perbaikan' " +
                "  WHEN 'Pajak Kendaraan' THEN 'cat_transport' " +
                "  ELSE NULL " +
                "END, " +
                "isPaidThisMonth, notifyBeforeDays, recurrenceMode, rangeEndMonthMillis " +
                "FROM `bills`"
        )
        db.execSQL("DROP TABLE `bills`")
        db.execSQL("ALTER TABLE `bills_new` RENAME TO `bills`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bills_categoryId` ON `bills`(`categoryId`)")
    }
}

/** Migration 13 → 14 — no-op (only enables exportSchema). */
val MIGRATION_13_14: Migration = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Intentionally empty: v14 schema identical to v13.
    }
}

/**
 * Migration 14 → 15
 *
 * Backfill `transactions.categoryId` from the legacy `category` string column.
 * Alias-to-canonical resolution: short synthetic-data names ("Perawatan") are
 * intentionally mapped to canonical UUIDs ("cat_perawatan" = "Perawatan Diri").
 */
val MIGRATION_14_15: Migration = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "UPDATE `transactions` " +
            "SET `categoryId` = CASE `category` " +
            "  WHEN 'Makanan' THEN 'cat_makanan' " +
            "  WHEN 'Transport' THEN 'cat_transport' " +
            "  WHEN 'Belanja' THEN 'cat_belanja' " +
            "  WHEN 'Hiburan' THEN 'cat_hiburan' " +
            "  WHEN 'Tagihan' THEN 'cat_tagihan' " +
            "  WHEN 'Investasi' THEN 'cat_investasi' " +
            "  WHEN 'Kesehatan' THEN 'cat_kesehatan' " +
            "  WHEN 'Pendidikan' THEN 'cat_pendidikan' " +
            "  WHEN 'Perawatan' THEN 'cat_perawatan' " +
            "  WHEN 'Olahraga' THEN 'cat_olahraga' " +
            "  WHEN 'Donasi' THEN 'cat_donasi' " +
            "  WHEN 'Asuransi' THEN 'cat_asuransi' " +
            "  WHEN 'Perbaikan' THEN 'cat_perbaikan' " +
            "  WHEN 'Peliharaan' THEN 'cat_peliharaan' " +
            "  WHEN 'Lainnya' THEN 'cat_lainnya' " +
            "  WHEN 'Gaji' THEN 'cat_gaji' " +
            "  WHEN 'Penjualan' THEN 'cat_penjualan' " +
            "  WHEN 'Hadiah' THEN 'cat_hadiah' " +
            "  ELSE (" +
            "    IFNULL((" +
            "      SELECT `id` FROM `categories` " +
            "      WHERE LOWER(`categories`.`name`) = LOWER(`transactions`.`category`)" +
            "    ), 'cat_lainnya') " +
            "END " +
            "WHERE `categoryId` IS NULL " +
            "  AND `category` != ''"
        )
    }
}

/**
 * Migration 15 → 16
 *
 * Drops the legacy `budgets.category` string column. After Gap 2 the
 * `categoryId` UUID FK is the single source of truth for which category
 * a budget targets. SQLite 3.35+ supports `ALTER TABLE DROP COLUMN`.
 */
val MIGRATION_15_16: Migration = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `budgets` DROP COLUMN `category`")
    }
}

/**
 * Migration 16 → 17
 *
 * Adds a `FOREIGN KEY(categoryId) REFERENCES categories(id) ON DELETE SET NULL`
 * constraint to the `transactions` table. SQLite cannot add a FK to an
 * existing table in place, so we recreate it.
 */
val MIGRATION_16_17: Migration = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `transactions_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`amount` REAL NOT NULL, " +
                "`description` TEXT NOT NULL, " +
                "`category` TEXT NOT NULL, " +
                "`categoryId` TEXT, " +
                "`budgetId` INTEGER, " +
                "`type` TEXT NOT NULL, " +
                "`source` TEXT NOT NULL, " +
                "`rawInput` TEXT NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, " +
                "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL" +
            ")"
        )
        db.execSQL(
            "INSERT INTO `transactions_new` (id, amount, description, category, categoryId, budgetId, type, source, rawInput, timestamp) " +
                "SELECT id, amount, description, category, categoryId, budgetId, type, source, rawInput, timestamp FROM `transactions`"
        )
        db.execSQL("DROP TABLE `transactions`")
        db.execSQL("ALTER TABLE `transactions_new` RENAME TO `transactions`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_categoryId` ON `transactions`(`categoryId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_budgetId` ON `transactions`(`budgetId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_timestamp` ON `transactions`(`timestamp`)")
    }
}

/**
 * Migration 17 → 18
 *
 * Adds `deletedAt` column + index to `transactions` to mirror the
 * soft-delete pattern on `budgets` and `categories`. SQLite supports
 * `ALTER TABLE ADD COLUMN` natively — no table recreate needed.
 * Existing rows get `deletedAt = NULL` (active).
 */
val MIGRATION_17_18: Migration = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `deletedAt` INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_deletedAt` ON `transactions`(`deletedAt`)")
    }
}

/**
 * Migration 18 → 19
 *
 * Creates the `recurring_transactions` table — templates that get
 * materialized into [Transaction] rows on a specific day-of-month
 * (e.g. Gaji on the 1st, Listrik on the 25th). Replaces the
 * SyntheticDataSeeder pattern of injecting the same recurring rows
 * every seed run.
 */
val MIGRATION_18_19: Migration = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recurring_transactions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`amount` REAL NOT NULL, " +
                "`categoryId` TEXT, " +
                "`type` TEXT NOT NULL, " +
                "`dayOfMonth` INTEGER NOT NULL, " +
                "`recurrenceMode` TEXT NOT NULL DEFAULT 'FOREVER', " +
                "`rangeEndMonthMillis` INTEGER, " +
                "`isActive` INTEGER NOT NULL DEFAULT 1, " +
                "`note` TEXT, " +
                "`lastGeneratedMonth` INTEGER, " +
                "`createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL" +
            ")"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_transactions_categoryId` ON `recurring_transactions`(`categoryId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_transactions_dayOfMonth` ON `recurring_transactions`(`dayOfMonth`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_transactions_isActive` ON `recurring_transactions`(`isActive`)")
    }
}

/**
 * Migration 19 → 20
 *
 * Creates the `notification_schedule` table — persistent record of
 * upcoming notifications (bill reminders, budget warnings, etc.). The
 * actual firing is handled by [com.example.work.NotificationScheduler]
 * which reads this table via AlarmManager. We don't store actual
 * alarm IDs here — those are registered on app start / on-insert
 * and re-registered after boot via [com.example.work.BootReceiver].
 */
val MIGRATION_19_20: Migration = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `notification_schedule` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`body` TEXT NOT NULL, " +
                "`channelId` TEXT NOT NULL DEFAULT 'hartaku_reminders', " +
                "`triggerAtMillis` INTEGER NOT NULL, " +
                "`tag` TEXT NOT NULL, " +
                "`sourceId` INTEGER, " +
                "`isDelivered` INTEGER NOT NULL DEFAULT 0, " +
                "`createdAt` INTEGER NOT NULL" +
            ")"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_schedule_triggerAtMillis` ON `notification_schedule`(`triggerAtMillis`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_schedule_isDelivered` ON `notification_schedule`(`isDelivered`)")
    }
}

/**
 * Migration 20 → 21
 *
 * Creates the `audit_log` table — append-only forensic log. Scaffolding
 * only; no interceptor or writer is wired in this gap. A future
 * `AuditLogger` will wrap repository insert/update/delete methods to
 * populate this table.
 */
val MIGRATION_20_21: Migration = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `audit_log` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, " +
                "`entityType` TEXT NOT NULL, " +
                "`entityId` INTEGER, " +
                "`action` TEXT NOT NULL, " +
                "`description` TEXT, " +
                "`afterJson` TEXT, " +
                "`beforeJson` TEXT" +
            ")"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_log_entityType` ON `audit_log`(`entityType`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_log_timestamp` ON `audit_log`(`timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_log_action` ON `audit_log`(`action`)")
    }
}

/**
 * Migration 21 → 22
 *
 * Creates the `user_profile` table — single-user settings. A default
 * row with id=1 is inserted so first-launch reads return a usable
 * profile (currency=IDR, locale=id-ID, monthlyIncome=5,000,000).
 * Existing seeded budgets keep their initial amounts; future
 * re-seeding (manual user action) would honor this monthlyIncome.
 */
val MIGRATION_21_22: Migration = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `user_profile` (" +
                "`id` INTEGER NOT NULL, " +
                "`displayName` TEXT NOT NULL, " +
                "`currency` TEXT NOT NULL, " +
                "`locale` TEXT NOT NULL, " +
                "`monthlyIncome` REAL NOT NULL, " +
                "`onboardingComplete` INTEGER NOT NULL DEFAULT 0, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`)" +
            ")"
        )
        // Insert default profile row (id=1, default values).
        val now = System.currentTimeMillis()
        db.execSQL(
            "INSERT OR IGNORE INTO `user_profile` (id, displayName, currency, locale, monthlyIncome, onboardingComplete, createdAt, updatedAt) " +
                "VALUES (1, '', 'IDR', 'id-ID', 5000000.0, 0, $now, $now)"
        )
    }
}

/**
 * Migration 22 → 23
 *
 * Adds FOREIGN KEY constraint on budgets.categoryId → categories(id) ON DELETE SET NULL.
 * SQLite cannot add a FK to an existing table, so we recreate it.
 */
val MIGRATION_22_23: Migration = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys=OFF")
        // Clean up orphaned categoryId references before creating FK
        db.execSQL("UPDATE budgets SET categoryId = NULL WHERE categoryId IS NOT NULL AND categoryId NOT IN (SELECT id FROM categories)")
        db.execSQL("CREATE TABLE IF NOT EXISTS budgets_new (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "name TEXT NOT NULL DEFAULT '', " +
            "categoryId TEXT, " +
            "icon TEXT, " +
            "amount REAL NOT NULL, " +
            "spent REAL NOT NULL DEFAULT 0.0, " +
            "period TEXT NOT NULL DEFAULT 'monthly', " +
            "percent REAL NOT NULL DEFAULT 0.0, " +
            "note TEXT, " +
            "createdAt INTEGER NOT NULL DEFAULT 0, " +
            "deletedAt INTEGER, " +
            "FOREIGN KEY(categoryId) REFERENCES categories(id) ON DELETE SET NULL" +
        ")")
        db.execSQL("INSERT INTO budgets_new SELECT * FROM budgets")
        db.execSQL("DROP TABLE budgets")
        db.execSQL("ALTER TABLE budgets_new RENAME TO budgets")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_budgets_deletedAt ON budgets(deletedAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_budgets_categoryId ON budgets(categoryId)")
        db.execSQL("PRAGMA foreign_keys=ON")
    }
}

/**
 * Migration 23 → 24
 *
 * Adds self-referencing FOREIGN KEY constraint on categories.parentId → categories(id)
 * ON DELETE SET NULL. SQLite cannot add a FK to an existing table, so we recreate it.
 */
val MIGRATION_23_24: Migration = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys=OFF")
        // Clean up orphaned parentId before creating self-referencing FK
        db.execSQL("UPDATE categories SET parentId = NULL WHERE parentId IS NOT NULL AND parentId NOT IN (SELECT id FROM categories)")
        db.execSQL("CREATE TABLE IF NOT EXISTS categories_new (" +
            "id TEXT NOT NULL PRIMARY KEY, " +
            "parentId TEXT, " +
            "name TEXT NOT NULL, " +
            "slug TEXT NOT NULL, " +
            "typeClass TEXT NOT NULL, " +
            "aliases TEXT NOT NULL DEFAULT '[]', " +
            "icon TEXT, " +
            "color TEXT, " +
            "sortOrder INTEGER NOT NULL DEFAULT 0, " +
            "deletedAt INTEGER, " +
            "createdAt INTEGER NOT NULL DEFAULT 0, " +
            "updatedAt INTEGER NOT NULL DEFAULT 0, " +
            "FOREIGN KEY(parentId) REFERENCES categories(id) ON DELETE SET NULL" +
        ")")
        db.execSQL("INSERT INTO categories_new SELECT * FROM categories")
        db.execSQL("DROP TABLE categories")
        db.execSQL("ALTER TABLE categories_new RENAME TO categories")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_categories_slug ON categories(slug)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_categories_parentId ON categories(parentId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_categories_deletedAt ON categories(deletedAt)")
        db.execSQL("PRAGMA foreign_keys=ON")
    }
}

/**
 * Migration 24 → 25
 *
 * Adds `categoryIds` TEXT column to `budgets` for multi-category support.
 * Stores JSON array of category UUIDs (e.g. '["uuid1","uuid2"]').
 * Old budgets keep `categoryId` (single UUID) — backward compat.
 */
val MIGRATION_24_25: Migration = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE budgets ADD COLUMN categoryIds TEXT DEFAULT '[]'")
    }
}

val MIGRATION_25_26: Migration = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `agent_tasks` (
                `id` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `executeAt` INTEGER NOT NULL,
                `intervalDays` INTEGER NOT NULL DEFAULT 0,
                `params` TEXT NOT NULL DEFAULT '{}',
                `lastExecuted` INTEGER,
                PRIMARY KEY(`id`)
            )
        """)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `agent_feedback` (
                `id` TEXT NOT NULL,
                `session_id` TEXT NOT NULL,
                `query_text` TEXT NOT NULL,
                `response_text` TEXT NOT NULL,
                `tool_calls_json` TEXT NOT NULL DEFAULT '[]',
                `rating` INTEGER,
                `implicit_signal` TEXT,
                `category` TEXT,
                `created_at` INTEGER NOT NULL,
                `processed_at` INTEGER,
                PRIMARY KEY(`id`)
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_agent_feedback_category ON agent_feedback(`category`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_agent_feedback_rating ON agent_feedback(`rating`)")
    }
}

/** Migration 26 → 27 — creates conversation_sessions and conversation_messages tables. */
val MIGRATION_26_27: Migration = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `conversation_sessions` (
                `id` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `messageCount` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`id`)
            )
        """)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `conversation_messages` (
                `id` TEXT NOT NULL,
                `sessionId` TEXT NOT NULL,
                `role` TEXT NOT NULL,
                `content` TEXT NOT NULL,
                `turnIndex` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_conv_messages_sessionId ON conversation_messages(`sessionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_conv_messages_createdAt ON conversation_messages(`createdAt`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_conv_messages_session_turn ON conversation_messages(`sessionId`, `turnIndex`)")
    }
}

/** Migration 27 → 28 — drops dead agent_tasks table + orphan Transaction columns. */
val MIGRATION_27_28: Migration = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `agent_tasks`")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `transactions_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`amount` REAL NOT NULL, " +
                "`description` TEXT NOT NULL, " +
                "`category` TEXT NOT NULL, " +
                "`categoryId` TEXT, " +
                "`budgetId` INTEGER, " +
                "`type` TEXT NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, " +
                "`deletedAt` INTEGER, " +
                "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL" +
            ")"
        )
        db.execSQL(
            "INSERT INTO `transactions_new` " +
                "(id, amount, description, category, categoryId, budgetId, type, timestamp, deletedAt) " +
                "SELECT id, amount, description, category, categoryId, budgetId, type, timestamp, deletedAt FROM `transactions`"
        )
        db.execSQL("DROP TABLE `transactions`")
        db.execSQL("ALTER TABLE `transactions_new` RENAME TO `transactions`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_categoryId` ON `transactions`(`categoryId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_budgetId` ON `transactions`(`budgetId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_timestamp` ON `transactions`(`timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_deletedAt` ON `transactions`(`deletedAt`)")
    }
}
