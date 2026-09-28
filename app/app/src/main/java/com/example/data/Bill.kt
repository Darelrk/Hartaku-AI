package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class RecurrenceMode {
    ONCE,
    FOREVER,
    CUSTOM_RANGE
}

@Entity(
    tableName = "bills",
    foreignKeys = [ForeignKey(
        entity = Category::class,
        parentColumns = ["id"],
        childColumns = ["categoryId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("categoryId")]
)
data class Bill(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val amount: Double,
    val dueDate: Int, // 1-31
    val categoryId: String? = null, // UUID FK ke categories.id (nullable)
    val isPaidThisMonth: Boolean = false,
    val notifyBeforeDays: Int = 1,
    val recurrenceMode: RecurrenceMode = RecurrenceMode.ONCE,
    val rangeEndMonthMillis: Long? = null
)