package com.example.eval

import com.example.ai.AnswerValidator
import com.example.MainDispatcherRule
import com.example.ai.ChatbotRAGManager
import com.example.ai.FakeChatClient
import com.example.ai.ToolCall
import com.example.data.FakeTransactionRepository
import com.example.data.Transaction
import com.example.data.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
/**
 * Eval harness offline. Menjalankan 30 kasus [EvalCorpus] terhadap pipeline
 * produksi penuh: `ChatbotRAGManager` → `MultiTurnOrchestrator` →
 * `ToolCallExecutor` → `FakeTransactionRepository` → `AnswerValidator`.
 *
 * `FakeChatClient` hanya meniru keputusan routing LLM (tool mana yang dipilih
 * dan isi jawabannya). Semua yang sesudah itu — resolusi rentang tanggal,
 * query repository, regenerasi konteks, validasi angka — adalah kode produksi
 * yang diuji di sini. Network dan API key tidak dipakai.
 *
 * Pola JUnit 4 murni: satu `@Test` per kelompok tool, pesan kegagalan selalu
 * diawali `[id kasus]` supaya titik gagalnya langsung terlihat.
 */
@RunWith(RobolectricTestRunner::class)
class EvalHarnessTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dayMs = 24L * 60 * 60 * 1000

    private data class Row(
        val amount: Double,
        val description: String,
        val category: String,
        val type: TransactionType,
        val daysAgo: Int
    )

    private fun seedTransactions(repo: FakeTransactionRepository) = runBlocking {
        // Majukan seed 1 detik ke masa lalu. Rentang relatif di
        // resolveDateRangeMillis berakhir di `now` secara half-open
        // (`timestamp < end`), jadi baris hari-0 yang bertimestamp persis sama
        // dengan `now` saat query akan terbuang. Dua pembacaan
        // System.currentTimeMillis() sering kali jatuh di milidetik yang sama,
        // sehingga tanpa offset ini angka korpus berubah-ubah antar jalan.
        val now = System.currentTimeMillis() - 1_000
        listOf(
            Row(85_000.0, "Makanan", "Makanan", TransactionType.EXPENSE, 0),
            Row(45_000.0, "Gojek ke kantor", "Transport", TransactionType.EXPENSE, 0),
            Row(20_000.0, "Kopi", "Makanan", TransactionType.EXPENSE, 1),
            Row(1_500_000.0, "Gaji", "Gaji", TransactionType.INCOME, 0),
            Row(12_000.0, "Belanja", "Belanja", TransactionType.EXPENSE, 2)
        ).forEach { r ->
            repo.insertTransaction(
                Transaction(
                    amount = r.amount,
                    description = r.description,
                    category = r.category,
                    type = r.type,
                    timestamp = now - r.daysAgo * dayMs
                )
            )
        }
    }

    /**
     * Jawaban yang diberikan LLM pada putaran kedua.
     *
     * PENTING: string ini disusun dari [EvalCase.expectedAnswerNumbers] dengan
     * sengaja, supaya ia TIDAK bergantung pada isi tool result. Kalau ia
     * disusun dari tool result, assertion "angka muncul di jawaban" jadi
     * tautologi dan tidak bisa menangkap regresi sama sekali.
     */
    private fun fakeFinalAnswer(c: EvalCase): String =
        if (c.expectNoData) "Tidak ada transaksi yang cocok."
        else c.expectedAnswerNumbers.joinToString(separator = ", ", prefix = "Total: Rp ", postfix = ".")

    private fun runCase(c: EvalCase): String {
        val repo = FakeTransactionRepository()
        seedTransactions(repo)

        val client = FakeChatClient().apply {
            turnToolCalls = mapOf(0 to listOf(ToolCall("call_1", c.toolName, c.toolArgsJson)))
            turnContent = mapOf(1 to fakeFinalAnswer(c))
        }
        val manager = ChatbotRAGManager(client, repo)
        val answer = runBlocking { manager.processQuery(c.query) }

        // 1. Tool dieksekusi terhadap argumen LLM, rentang tanggal ter-resolve benar.
        val toolResult = manager.lastToolResultJson
        assertTrue("[${c.id}] tool tidak menghasilkan JSON, orchestration gagal", toolResult != null)
        if (c.toolResultMustContain.isNotEmpty()) {
            assertTrue(
                "[${c.id}] hasil tool tidak memuat ${c.toolResultMustContain}, dapat: $toolResult",
                toolResult!!.contains(c.toolResultMustContain)
            )
        }

        // 2. Jalur format produksi: teks yang regenerated dari tool result nyata
        //    harus memuat angka harapan. Ini assertion yang punya gigi — kalau
        //    executor atau AnswerValidator berubah, teks ini ikut berubah.
        val regenerated = AnswerValidator.regenerateFromContext(toolResult!!, toolResult!!)
        c.expectedAnswerNumbers.forEach { n ->
            assertTrue(
                "[${c.id}] konteks hasil tool tidak memuat angka $n. Regenerate: $regenerated",
                regenerated.contains(n)
            )
        }

        // 3. Pipeline harus meneruskan konteks itu ke jawaban akhir.
        c.expectedAnswerNumbers.forEach { n ->
            assertTrue("[${c.id}] jawaban tidak memuat angka $n. Jawaban: $answer", answer.contains(n))
        }

        // 4. Kasus tanpa data: jawaban tidak boleh mengarang nominal Rupiah.
        //    Produksi merender "Total: Rp 0" dan fake merender "Tidak ada
        //    transaksi yang cocok." — keduanya harus lolos, sedangkan jawaban
        //    berangka seperti "Total: Rp 130.000" harus gagal.
        if (c.expectNoData) {
            assertTrue(
                "[${c.id}] jawaban mengarang nominal. Jawaban: $answer",
                EvalCorpus.answerShowsNoFabricatedAmount(answer)
            )
        }

        // 4. System prompt benar-benar mendeklarasikan tool ke LLM.
        assertTrue(
            "[${c.id}] system prompt tidak mendeklarasikan tool ${c.toolName}",
            client.lastSystemPrompt!!.contains(c.toolName)
        )

        return answer
    }

    @Test
    fun corpusHasThirtyUniqueCases() {
        assertEquals(31, EvalCorpus.cases.size)
        assertEquals(
            "id kasus harus unik",
            EvalCorpus.cases.size,
            EvalCorpus.cases.map { it.id }.toSet().size
        )
    }

    @Test
    fun getExpensesCases() = runGroup("get_expenses")

    @Test
    fun getIncomeCases() = runGroup("get_income")

    @Test
    fun getBalanceCases() = runGroup("get_balance")

    @Test
    fun queryTransactionsCases() = runGroup("query_transactions")

    private fun runGroup(tool: String) {
        EvalCorpus.cases.filter { it.toolName == tool }.forEach { runCase(it) }
    }
}
