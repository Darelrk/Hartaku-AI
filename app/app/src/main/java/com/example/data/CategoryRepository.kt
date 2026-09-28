package com.example.data

import kotlinx.coroutines.flow.first

open class CategoryRepository(
    private val dao: CategoryDao
) {
    open suspend fun getAllActive(): List<Category> = dao.getAllActive()
    open suspend fun getRootCategories(): List<Category> = dao.getRootCategories()
    open suspend fun getChildren(parentId: String): List<Category> = dao.getChildren(parentId)
    open suspend fun getById(id: String): Category? = dao.getById(id)
    open suspend fun getBySlug(slug: String): Category? = dao.getBySlug(slug)
    open suspend fun getByTypeClass(typeClass: String): List<Category> = dao.getByTypeClass(typeClass)
    open suspend fun search(query: String): List<Category> = dao.search(query)
    /**
     * Menolak slug yang sudah dimiliki kategori lain.
     *
     * `CategoryDao.insert` memakai `OnConflictStrategy.REPLACE` dan `slug`
     * punya unique index, jadi insert dengan slug bentrok tidak men gagal —
     * ia MENGHAPUS row lama. Karena FK ke `transactions.categoryId` dan
     * `budgets.categoryId` memakai `ON DELETE SET NULL`, seluruh riwayat
     * kategori lama ikut menjadi null tanpa jejak.
     */
    open suspend fun insert(category: Category) {
        val slugOwner = dao.getBySlug(category.slug)
        require(slugOwner == null || slugOwner.id == category.id) {
            "Kategori '${slugOwner?.name}' sudah memakai slug '${category.slug}'. " +
                "Gunakan nama lain."
        }
        dao.insert(category)
    }
    open suspend fun insertAll(categories: List<Category>) = dao.insertAll(categories)
    open suspend fun update(category: Category) = dao.update(category)
    open suspend fun softDelete(id: String) = dao.softDelete(id)
    open suspend fun rename(id: String, name: String, slug: String) = dao.rename(id, name, slug)
    open suspend fun count(): Int = dao.count()
    open suspend fun getAllDeleted(): List<Category> = dao.getAllDeleted()
    open suspend fun restore(id: String) = dao.restore(id)

    open suspend fun getOrCreateByName(name: String, typeClass: String): Category {
        val trimmedName = name.trim()
        val capitalizedName = trimmedName.lowercase().replaceFirstChar { it.uppercase() }
        val generatedSlug = capitalizedName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        
        // Try searching for an active category with the exact name (case-insensitive) or by slug.
        val existing = dao.getAllActive().firstOrNull { it.name.equals(capitalizedName, ignoreCase = true) || it.slug == generatedSlug }
        if (existing != null) {
            return existing
        }

        // Kategori dengan slug sama mungkin sudah di-soft-delete. Jangan buat
        // duplikat: REPLACE akan menghapus row lama dan memutus referensi
        // transaksi yang masih memakainya. Pulihkan saja.
        dao.getBySlug(generatedSlug)?.let { archived ->
            dao.restore(archived.id)
            return dao.getById(archived.id) ?: archived
        }
        
        // Generate UUID, create Category object, insert it and return it.
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
