package com.example.data

import androidx.room.*

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories WHERE deletedAt IS NULL ORDER BY sortOrder ASC, name ASC")
    suspend fun getAllActive(): List<Category>

    @Query("SELECT * FROM categories WHERE deletedAt IS NULL AND parentId IS NULL ORDER BY sortOrder ASC")
    suspend fun getRootCategories(): List<Category>

    @Query("SELECT * FROM categories WHERE deletedAt IS NULL AND parentId = :parentId ORDER BY sortOrder ASC")
    suspend fun getChildren(parentId: String): List<Category>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): Category?

    @Query("SELECT * FROM categories WHERE slug = :slug")
    suspend fun getBySlug(slug: String): Category?

    @Query("SELECT * FROM categories WHERE deletedAt IS NULL AND name LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<Category>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: Category)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<Category>)

    @Update
    suspend fun update(category: Category)

    @Query("UPDATE categories SET deletedAt = :timestamp, updatedAt = :timestamp WHERE id = :id")
    suspend fun softDelete(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE categories SET deletedAt = NULL, updatedAt = :timestamp WHERE id = :id")
    suspend fun restore(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM categories WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    suspend fun getAllDeleted(): List<Category>

    @Query("UPDATE categories SET name = :name, slug = :slug, updatedAt = :timestamp WHERE id = :id")
    suspend fun rename(id: String, name: String, slug: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM categories WHERE deletedAt IS NULL AND typeClass = :typeClass ORDER BY sortOrder ASC")
    suspend fun getByTypeClass(typeClass: String): List<Category>

    @Query("SELECT COUNT(*) FROM categories WHERE deletedAt IS NULL")
    suspend fun count(): Int

    /** Termasuk yang sudah di-soft-delete — dipakai CategorySeeder agar arsip tidak memicu seed ulang. */
    @Query("SELECT COUNT(*) FROM categories")
    suspend fun countIncludingDeleted(): Int
}