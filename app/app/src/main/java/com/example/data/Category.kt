package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
    foreignKeys = [ForeignKey(
        entity = Category::class,
        parentColumns = ["id"],
        childColumns = ["parentId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [
        Index(value = ["slug"], unique = true),
        Index(value = ["parentId"]),
        Index(value = ["deletedAt"])
    ]
)
data class Category(
    @PrimaryKey val id: String,
    val parentId: String? = null,
    val name: String,
    val slug: String,
    val typeClass: String,
    val aliases: String = "[]",
    val icon: String? = null,
    val color: String? = null,
    val sortOrder: Int = 0,
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
