package com.example.eval

import com.example.ai.ChatbotRAGManager
import com.example.ai.NimApiClient
import com.example.ai.NimConfig
import com.example.data.FakeTransactionRepository
import com.example.data.Transaction
import com.example.data.TransactionType
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Runner eval online — memanggil NVIDIA NIM sungguhan untuk korpus yang sama
 * dengan [EvalHarnessTest], lalu memisahkan dua lapis penilaian:
 *
 *  - `orchestrationOk`: tool yang benar terpanggil dan menghasilkan JSON yang
 *    memuat nilai harapan. Ini mengukur kode kita.
 *  - `modelOk`: angka harapan muncul di jawaban mentah LLM. Ini mengukur
 *    model + prompt, dan boleh rendah tanpa berarti harness rusak.
 *
 * Hanya jalan bila flag `-PevalOnline` diberikan; tanpa flag, Gradle mengecualikan
 * kelas ini dari `testDebugUnitTest`.
 */
@RunWith(RobolectricTestRunner::class)
class OnlineEvalTest {

    private data class CaseResult(
        val id: String,
        val toolName: String,
        val gotTool: String,
        val gotArgs: String,
        val orchestrationOk: Boolean,
        val modelOk: Boolean,
        val answer: String,
        val error: String?
    )

    private val dayMs = 24L * 60 * 60 * 1000

    private fun seedTransactions(repo: FakeTransactionRepository) = runBlocking {
        // Sama dengan EvalHarnessTest: mundur 1 detik agar baris hari-0 tidak
        // jatuh persis di batas half-open `end` yang diambil saat query.
        val now = System.currentTimeMillis() - 1_000
        data class Row(
            val amount: Double,
            val description: String,
            val category: String,
            val type: TransactionType,
            val daysAgo: Int
        )
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

    @Test
    fun runOnlineEval() {
        val apiKey = NimConfig.getApiKey()
        assertTrue(
            "NVIDIA_API_KEY kosong — jalankan dengan -PevalOnline dari build yang membaca .env",
            !apiKey.isNullOrEmpty()
        )
        val key = apiKey ?: error("NVIDIA_API_KEY kosong")

        val results = mutableListOf<CaseResult>()
        for (c in EvalCorpus.cases) {
            val repo = FakeTransactionRepository()
            seedTransactions(repo)
            var orchestrationOk = false
            var modelOk = false
            var answer = ""
            var gotTool = ""
            var gotArgs = ""
            var error: String? = null
            try {
                val manager = ChatbotRAGManager(NimApiClient(key), repo)
                answer = runBlocking { manager.processQuery(c.query) }
                val toolResult = manager.lastToolResultJson
                if (toolResult != null) {
                    // Jejak tool yang benar-benar dijalankan, dibaca dari JSON
                    // hasil tool — bukan dari `c.toolName` yang hanya berisi
                    // ekspektasi. `dateRange` tidak diulang query_transactions,
                    // jadi gotArgs kosong untuk tool itu.
                    val json = JSONObject(toolResult)
                    gotTool = json.optString("tool", "")
                    gotArgs = json.optString("dateRange", "")
                }
                orchestrationOk = toolResult != null &&
                    (c.toolResultMustContain.isEmpty() || toolResult.contains(c.toolResultMustContain))
                modelOk = if (c.expectNoData) {
                    EvalCorpus.answerShowsNoFabricatedAmount(answer)
                } else {
                    c.expectedAnswerNumbers.all { answer.contains(it) }
                }
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            }
            results += CaseResult(
                c.id, c.toolName, gotTool, gotArgs, orchestrationOk, modelOk, answer, error
            )
        }

        val report = JSONObject().apply {
            put("total", results.size)
            put("orchestrationOk", results.count { it.orchestrationOk })
            put("modelOk", results.count { it.modelOk })
            put("cases", JSONArray().apply {
                results.forEach { r ->
                    put(
                        JSONObject().apply {
                            put("id", r.id)
                            put("tool", r.toolName)
                            put("gotTool", r.gotTool)
                            put("gotArgs", r.gotArgs)
                            put("orchestrationOk", r.orchestrationOk)
                            put("modelOk", r.modelOk)
                            put("answer", r.answer)
                            put("error", r.error ?: JSONObject.NULL)
                        }
                    )
                }
            })
        }

        val out = File("eval-reports/online-report.json")
        out.parentFile?.mkdirs()
        out.writeText(report.toString(2))
        println("AiEval report: ${out.absolutePath}")
        println(
            "AiEval ringkasan: total=${results.size} " +
                "orchestrationOk=${results.count { it.orchestrationOk }} " +
                "modelOk=${results.count { it.modelOk }}"
        )
    }
}
