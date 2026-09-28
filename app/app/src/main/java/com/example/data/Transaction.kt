package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    foreignKeys = [ForeignKey(
        entity = Category::class,
        parentColumns = ["id"],
        childColumns = ["categoryId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("categoryId"), Index("budgetId"), Index("timestamp"), Index("deletedAt")]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,
    val description: String,
    val category: String = "",            // legacy: NIM masih return string category
    val categoryId: String? = null,       // FK ke categories.id (set via MIGRATION_14_15 backfill)
    val budgetId: Int? = null,            // manual expense → budget attribution
    val timestamp: Long,
    val type: TransactionType,
    val deletedAt: Long? = null           // soft-delete marker (mirrors Budget pattern)
)

enum class TransactionType {
    INCOME, EXPENSE
}
