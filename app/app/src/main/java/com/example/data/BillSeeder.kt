package com.example.data

import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * Bill seeder for the first launch.
 *
 * Seeds 12 Indonesian recurring/one-time bills covering utilities,
 * subscriptions, vehicle, electronics, and home maintenance. The mix
 * of FOREVER (8), CUSTOM_RANGE (2), and ONCE (2) recurrence modes
 * exercises every branch of the [com.example.data.BillDao] query.
 *
 * Idempotency: [BillRepository] has no `count()` method, so the
 * seeder uses `getActiveBillsForMonth(now).first().isEmpty()` as the
 * guard. The bills table starts empty, so the first call inserts; a
 * second call sees 12 rows and returns immediately.
 *
 * `Bill.categoryId` is a nullable String UUID FK to `categories.id`.
 * Each seeded bill is linked to the appropriate CategorySeeder UUID
 * so icon/color rendering in the bill management screen resolves
 * correctly. If the linked category is soft-deleted later, the FK
 * cascades to NULL (see [Bill] entity's ForeignKey).
 */
object BillSeeder {

    suspend fun seedIfEmpty(repository: BillRepository) {
        val existing = repository.getActiveBillsForMonth(startOfCurrentMonthMillis()).first()
        if (existing.isNotEmpty()) {
            return
        }
        for (bill in buildBills()) {
            repository.insertBill(bill)
        }
    }

    private fun buildBills(): List<Bill> {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dayOfMonth = today.get(Calendar.DAY_OF_MONTH)

        // Bills paid this month if dueDate >= today; unpaid otherwise.
        fun paidIfNotOverdue(dueDate: Int): Boolean = dueDate >= dayOfMonth

        return listOf(
            // FOREVER bills (8) — utilities, insurance, subscriptions, fitness, KPR
            Bill(
                name = "Listrik PLN",
                amount = 300_000.0,
                dueDate = 25,
                categoryId = CategorySeeder.ID_TAGIHAN,
                isPaidThisMonth = paidIfNotOverdue(25),
                recurrenceMode = RecurrenceMode.FOREVER
            ),
            Bill(
                name = "Internet Indihome",
                amount = 350_000.0,
                dueDate = 25,
                categoryId = CategorySeeder.ID_TAGIHAN,
                isPaidThisMonth = paidIfNotOverdue(25),
                recurrenceMode = RecurrenceMode.FOREVER
            ),
            Bill(
                name = "Air PDAM",
                amount = 100_000.0,
                dueDate = 25,
                categoryId = CategorySeeder.ID_TAGIHAN,
                isPaidThisMonth = paidIfNotOverdue(25),
                recurrenceMode = RecurrenceMode.FOREVER
            ),
            Bill(
                name = "BPJS Kesehatan",
                amount = 150_000.0,
                dueDate = 10,
                categoryId = CategorySeeder.ID_ASURANSI,
                isPaidThisMonth = paidIfNotOverdue(10),
                recurrenceMode = RecurrenceMode.FOREVER
            ),
            Bill(
                name = "Spotify Premium",
                amount = 55_000.0,
                dueDate = 5,
                categoryId = CategorySeeder.ID_HIBURAN,
                isPaidThisMonth = paidIfNotOverdue(5),
                recurrenceMode = RecurrenceMode.FOREVER
            ),
            Bill(
                name = "Netflix",
                amount = 186_000.0,
                dueDate = 12,
                categoryId = CategorySeeder.ID_HIBURAN,
                isPaidThisMonth = paidIfNotOverdue(12),
                recurrenceMode = RecurrenceMode.FOREVER
            ),
            Bill(
                name = "Gym Membership",
                amount = 300_000.0,
                dueDate = 1,
                categoryId = CategorySeeder.ID_OLAHRAGA,
                isPaidThisMonth = paidIfNotOverdue(1),
                recurrenceMode = RecurrenceMode.FOREVER
            ),
            Bill(
                name = "KPR Apartemen",
                amount = 3_500_000.0,
                dueDate = 1,
                categoryId = CategorySeeder.ID_TAGIHAN,
                isPaidThisMonth = paidIfNotOverdue(1),
                recurrenceMode = RecurrenceMode.FOREVER
            ),
            // CUSTOM_RANGE bills (2) — vehicle, electronics installments.
            // Use 2027 end dates so the bills remain active in 2026.
            Bill(
                name = "Kredit Motor",
                amount = 850_000.0,
                dueDate = 15,
                categoryId = CategorySeeder.ID_TRANSPORT,
                isPaidThisMonth = paidIfNotOverdue(15),
                recurrenceMode = RecurrenceMode.CUSTOM_RANGE,
                rangeEndMonthMillis = startOfMonthMillis(2027, Calendar.DECEMBER)
            ),
            Bill(
                name = "Kredit Laptop",
                amount = 450_000.0,
                dueDate = 20,
                categoryId = CategorySeeder.ID_BELANJA,
                isPaidThisMonth = paidIfNotOverdue(20),
                recurrenceMode = RecurrenceMode.CUSTOM_RANGE,
                rangeEndMonthMillis = startOfMonthMillis(2027, Calendar.SEPTEMBER)
            ),
            // ONCE bills (2) — home maintenance, vehicle tax
            Bill(
                name = "Service AC",
                amount = 350_000.0,
                dueDate = 28,
                categoryId = CategorySeeder.ID_PERBAIKAN,
                isPaidThisMonth = paidIfNotOverdue(28),
                recurrenceMode = RecurrenceMode.ONCE
            ),
            Bill(
                name = "Pajak Kendaraan",
                amount = 1_200_000.0,
                dueDate = 15,
                categoryId = CategorySeeder.ID_TRANSPORT,
                isPaidThisMonth = paidIfNotOverdue(15),
                recurrenceMode = RecurrenceMode.ONCE
            )
        )
    }

    private fun startOfMonthMillis(year: Int, month: Int): Long {
        return Calendar.getInstance().apply {
            set(year, month, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun startOfCurrentMonthMillis(): Long {
        val now = Calendar.getInstance()
        return startOfMonthMillis(now.get(Calendar.YEAR), now.get(Calendar.MONTH))
    }
}
