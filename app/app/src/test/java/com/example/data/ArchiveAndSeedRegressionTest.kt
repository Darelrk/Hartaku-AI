package com.example.data

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test untuk cacat arsip hasil audit.
 *
 * Semuanya lolos build dan test lama karena tidak punya cakupan: tidak ada test
 * yang mengarsipkan semua kategori, dan tidak ada yang memanggil getById pada
 * budget terarsip.
 */
class ArchiveAndSeedRegressionTest {

    private fun budget(id: Int) = Budget(
        id = id,
        categoryId = CategorySeeder.ID_MAKANAN,
        amount = 500_000.0
    )

    // ---- CategorySeeder: arsip semua kategori tidak boleh memicu seed ulang ----

    @Test
    fun seedIfEmpty_semuaKategoriDiarsipkan_tidakSeedUlang() = runTest {
        val repo = FakeCategoryRepository()
        repo.insertAll(CategorySeeder.DEFAULT_CATEGORIES)
        repo.categories.forEach { repo.softDelete(it.id) }

        // Semua kategori aktif sudah habis.
        assertEquals(0, repo.count())

        CategorySeeder.seedIfEmpty(repo)

        // Inti bug: count() == 0 membuat seeder jalan lagi, dan insertAll memakai
        // REPLACE sehingga kategori default yang sudah dikustomisasi tertimpa.
        // Yang tersisa di arsip harus utuh, tanpa duplikat.
        assertEquals(CategorySeeder.DEFAULT_CATEGORIES.size, repo.categories.size)
        assertEquals(0, repo.count())
    }

    @Test
    fun seedIfEmpty_repoKosong_tetapSeed() = runTest {
        val repo = FakeCategoryRepository()
        CategorySeeder.seedIfEmpty(repo)
        assertEquals(CategorySeeder.DEFAULT_CATEGORIES.size, repo.count())
    }

    // ---- BudgetDao.getById: budget terarsip harus tersembunyi ----

    @Test
    fun getById_budgetTerarsip_null() = runTest {
        val repo = FakeBudgetRepository()
        repo.insertBudget(budget(7))
        repo.softDelete(7, 1_700_000_000_000L)

        // Query SQL-nya sudah difilter `deletedAt IS NULL`; fake harus
        // mencerminkan perilaku yang sama supaya test ini berarti.
        assertNull(repo.getById(7))
        assertTrue(repo.getAllDeleted().first().any { it.id == 7 })
    }

    @Test
    fun getById_budgetAktif_tetapDitemukan() = runTest {
        val repo = FakeBudgetRepository()
        repo.insertBudget(budget(7))
        assertNotNull(repo.getById(7))
    }

    // ---- CategoryRepository.getBySlug: yang terarsip tidak boleh ikut dicocokkan ----

    @Test
    fun getBySlug_kategoriTerarsip_null() = runTest {
        val repo = FakeCategoryRepository()
        val makanan = CategorySeeder.DEFAULT_CATEGORIES.first { it.id == CategorySeeder.ID_MAKANAN }
        repo.insert(makanan)
        repo.softDelete(CategorySeeder.ID_MAKANAN)

        assertNull(repo.getBySlug(makanan.slug))
        // Tapi masih bisa dipulihkan lewat arsip.
        assertTrue(repo.getAllDeleted().any { it.id == CategorySeeder.ID_MAKANAN })
    }
}
