package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.json.JSONArray

@Entity(
    tableName = "budgets",
    foreignKeys = [ForeignKey(
        entity = Category::class,
        parentColumns = ["id"],
        childColumns = ["categoryId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [
        Index(value = ["deletedAt"]),
        Index(value = ["categoryId"])
    ]
)
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String = "",
    val categoryId: String? = null,
    val categoryIds: String? = null,
    val icon: String? = null,
    val amount: Double,
    val spent: Double = 0.0,
    val period: String = "monthly",
    val percent: Double = 0.0,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
) {
    fun parseCategoryIds(): List<String> {
        if (!categoryIds.isNullOrBlank()) {
            return try {
                JSONArray(categoryIds).let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                }
            } catch (_: Exception) { emptyList() }
        }
        if (categoryId != null) return listOf(categoryId)
        return emptyList()
    }
}

fun categoryIdsToJson(ids: List<String>): String = JSONArray(ids).toString()
