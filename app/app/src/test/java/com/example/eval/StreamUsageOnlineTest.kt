package com.example.eval

import com.example.ai.ChatMessage
import com.example.ai.ChatStreamEvent
import com.example.ai.NimApiClient
import com.example.ai.NimConfig
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Bukti bahwa jalur SSE produksi benar-benar melaporkan token.
 *
 * `OnlineEvalTest` memakai `processQuery` (non-stream), jadi tidak menyentuh
 * `chatStreamWithTools` sama sekali. Padahal itulah jalur yang dipakai HomeFeed
 * saat pengguna bertanya lewat TANYA AI, dan sebelum `stream_options.include_usage`
 * dikirim, chunk `usage` tidak pernah sampai sehingga kolom token di Diagnostics
 * selalu kosong.
 *
 * Hanya jalan dengan `-PevalOnline`.
 */
@RunWith(RobolectricTestRunner::class)
class StreamUsageOnlineTest {

    @Test
    fun realSseStreamReportsTokenUsage() = runBlocking {
        val key = NimConfig.getApiKey()
        assertTrue(
            "NVIDIA_API_KEY kosong — jalankan dengan -PevalOnline dari build yang membaca .env",
            !key.isNullOrEmpty()
        )

        val events = NimApiClient(key!!).chatStreamWithTools(
            systemPrompt = "Kamu asisten keuangan pribadi. Jawab singkat dan langsung.",
            // Pertanyaan non-keuangan supaya model tidak memanggil tool, dan
            // responsnya jelas SSE biasa.
            history = listOf(ChatMessage(ChatMessage.ROLE_USER, "Sebutkan satu kota di Indonesia."))
        ).toList()

        assertTrue(
            "stream harus menghasilkan Delta atau ToolCalls. Events: ${events.map { it::class.simpleName }}",
            events.any { it is ChatStreamEvent.Done || it is ChatStreamEvent.ToolCalls }
        )

        val usage = events.filterIsInstance<ChatStreamEvent.Usage>().firstOrNull()
        assertTrue(
            "Tidak ada event Usage — stream_options.include_usage tidak dikirim atau " +
                "chunk usage tidak terbaca. Events: ${events.map { it::class.simpleName }}",
            usage != null
        )
        assertTrue(
            "promptTokens harus > 0, dapat ${usage!!.promptTokens}",
            usage!!.promptTokens > 0
        )
        println(
            "StreamUsageOnlineTest: promptTokens=${usage!!.promptTokens} " +
                "completionTokens=${usage!!.completionTokens} events=${events.size}"
        )
    }
}
