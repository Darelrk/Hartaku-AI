package com.example.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * NIM LLM API client — panggil model dari [NimConfig.LLM_MODEL] via NIM cloud.
 * Non-streaming, timeout 15s.
 * Supports native OpenAI-style function calling (tools/tool_calls).
 */
/**
 * SSE stream event from NIM streaming API.
 * [Delta] = text token chunk, streamed to UI.
 * [ToolCalls] = tool call request, processed by orchestrator.
 * [Done] = stream complete without tool calls (final turn).
 */
sealed class ChatStreamEvent {
    data class Delta(val content: String) : ChatStreamEvent()
    data class ToolCalls(val toolCalls: List<ToolCall>) : ChatStreamEvent()
    data class Done(val content: String) : ChatStreamEvent()

    /**
     * Token pemakaian satu putaran LLM. Selalu datang SETELAH [Done] atau
     * [ToolCalls] karena chunk `usage` di protokol SSE dikirim paling akhir,
     * setelah `finish_reason`.
     */
    data class Usage(val promptTokens: Int, val completionTokens: Int) : ChatStreamEvent()
}

class NimApiClient(
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        // Hosted NIM dari jaringan ini sangat tidak stabil: pengukuran berulang
        // (2026-09-30) menunjukkan banyak request timeout 60-75 detik, sisanya
        // 26-63 detik. Dengan 60s x 3 percobaan, satu simpan tertahan 3 menit
        // tanpa umpan balik. Fallback regex sudah menangani kasus umum dan benar,
        // jadi lebih baik gagal cepat lalu turun ke sana.
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) : ChatClient {

    // Client kedua khusus jalur chat/stream. Hosted NIM dari jaringan ini lambat di
    // sisi time-to-header, bukan error: probe 2026-09-30 (6 request, tools+stream)
    // → 6/6 sukses, header 12-60 detik (median 31), token pertama 22-89 detik.
    // readTimeout 20 detik di [client] membuat gagal SEBELUM header dibaca, jadi
    // Tanya AI selalu "sedang bermasalah" padahal endpoint sehat. Client ini hanya
    // dipakai chat; parse transaksi tetap [client] supaya gagal cepat ke regex.
    private val chatClient: OkHttpClient = client.newBuilder()
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json".toMediaType()

    override suspend fun chat(
        model: String,
        systemPrompt: String,
        userMessage: String,
        maxTokens: Int,
        temperature: Double
    ): Result<ChatResponse> {
        var lastResult: Result<ChatResponse>? = null
        // 1 percobaan saja: lihat catatan readTimeout di atas. Percobaan ulang
        // hanya memperpanjang rasa "hang", bukan meningkatkan فرصة berhasil
        // pada provider yang sedang lambat.
        val maxAttempts = 1
        for (attempt in 1..maxAttempts) {
            val result = chatSingleAttempt(model, systemPrompt, userMessage, maxTokens, temperature)
            lastResult = result
            if (result.isSuccess) {
                val content = result.getOrThrow().content
                if (isValidJson(content)) {
                    return result
                } else {
                    lastResult = Result.failure(Exception("Invalid JSON structure in AI response: $content"))
                }
            } else {
                val errMsg = result.exceptionOrNull()?.message ?: ""
                if (errMsg.startsWith("NIM API error 4")) {
                    return result
                }
            }
            if (attempt < maxAttempts) {
                delay(500L * attempt)
            }
        }
        return lastResult ?: Result.failure(Exception("All attempts failed"))
    }

    /**
     * Chat with native OpenAI-style function calling.
     * Sends [tools] definitions to NIM API and parses [ToolCall] from the response.
     * Falls back to plain chat if [tools] is empty/null.
     */
    override suspend fun chatWithTools(
        systemPrompt: String,
        history: List<ChatMessage>,
        maxTokens: Int,
        temperature: Double
    ): Result<ChatResponse> {
        var lastResult: Result<ChatResponse>? = null
        val maxAttempts = 3
        val tools = ChatToolDefinition.allTools
        for (attempt in 1..maxAttempts) {
            val result = chatWithToolsAttempt(
                systemPrompt = systemPrompt,
                history = history,
                tools = tools,
                maxTokens = maxTokens,
                temperature = temperature
            )
            lastResult = result
            if (result.isSuccess) {
                return result
            } else {
                val errMsg = result.exceptionOrNull()?.message ?: ""
                if (errMsg.startsWith("NIM API error 4")) {
                    return result
                }
            }
            if (attempt < maxAttempts) {
                delay(500L * attempt)
            }
        }
        return lastResult ?: Result.failure(Exception("All attempts failed"))
    }

    override suspend fun chatStream(
        model: String,
        systemPrompt: String,
        userMessage: String,
        maxTokens: Int,
        temperature: Double
    ): Flow<String> = flow {
        val body = JSONObject().apply {
            put("model", model)
            put("temperature", temperature)
            put("max_tokens", maxTokens)
            put("stream", true)
            // Model ini menaruh fase thinking di dalam `content` (bukan
            // `reasoning_content`), jadi seluruh max_tokens habis dipakai berpikir dan
            // stream berakhir `finish_reason: length` tanpa jawaban. Diukur: 5.732 ms
            // + JSON tidak valid → 1.895 ms + JSON valid. Tanpa flag ini, `enable_thinking`
            // dan `extra_body` ditolak 400; hanya `chat_template_kwargs` yang diterima.
            put("chat_template_kwargs", JSONObject().put("enable_thinking", false))
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userMessage)
                })
            })
        }
        val requestBody = body.toString().toByteArray(Charsets.UTF_8).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("${NimConfig.BASE_URL}/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        val response = withContext(Dispatchers.IO) { chatClient.newCall(request).execute() }
        if (!response.isSuccessful) {
            val errBody = response.body?.string() ?: "unknown"
            throw Exception("NIM API error ${response.code}: ${errBody.take(200)}")
        }

        response.body?.byteStream()?.bufferedReader()?.use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ")
                    if (data == "[DONE]") break
                    try {
                        val json = JSONObject(data)
                        val choices = json.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val delta = choices.getJSONObject(0).optJSONObject("delta")
                            val content = delta?.optString("content", "") ?: ""
                            if (content.isNotEmpty()) emit(content)
                        }
                    } catch (_: Exception) { /* skip malformed SSE chunks */ }
                }
            }
        }
    }

    override suspend fun chatStreamWithTools(
        systemPrompt: String,
        history: List<ChatMessage>,
        maxTokens: Int,
        temperature: Double
    ): Flow<ChatStreamEvent> = flow {
        // Smart tool_choice:
        //   - last msg is tool result → "none" (model answers from tool data)
        //   - user says greeting → "none" (no need to call tool)
        //   - user asks financial query → "required" (force native tool call)
        val lastMsg = history.lastOrNull()
        val lastUserMsg = history.lastOrNull { it.role == ChatMessage.ROLE_USER }?.content?.lowercase() ?: ""
        val isGreeting = listOf("halo", "hai", "hi", "hello", "pagi", "siang", "sore", "malam", "test", "tes", "makasih", "terima kasih", "thanks", "ok", "oke").any { lastUserMsg.trim() == it || lastUserMsg.trim().startsWith("$it ") }
        val toolChoice = "auto"

        val body = JSONObject().apply {
            put("model", NimConfig.LLM_MODEL)
            put("temperature", temperature)
            put("max_tokens", maxTokens)
            put("stream", true)
            put("chat_template_kwargs", JSONObject().put("enable_thinking", false))
            // Tanpa ini server tidak mengirim chunk `usage`, sehingga kolom token
            // di Diagnostics selalu kosong pada jalur produksi yang streaming.
            put("stream_options", JSONObject().put("include_usage", true))
            put("tools", toJsonValue(ChatToolDefinition.allTools))
            put("tool_choice", toolChoice)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                history.forEach { msg ->
                    put(serializeChatMessageWithTools(msg))
                }
            })
        }
        val requestBody = body.toString().toByteArray(Charsets.UTF_8).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("${NimConfig.BASE_URL}/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        val response = withContext(Dispatchers.IO) { chatClient.newCall(request).execute() }
        if (!response.isSuccessful) {
            val errBody = response.body?.string() ?: "unknown"
            throw Exception("NIM API error ${response.code}: ${errBody.take(200)}")
        }

        val toolCallBuffers = mutableMapOf<Int, ToolCallBuilder>()
        var accumulatedContent = StringBuilder()
        var doneSent = false
        var promptTokens = 0
        var completionTokens = 0
        // Model reasoning (gpt-oss, nemotron reasoning) streamed di
        // `delta.reasoning_content`, bukan `delta.content`. Kalau tidak
        // dilacak, seluruh token thinking phase terpakai dari max_tokens
        // dan stream berakhir `finish_reason: length` tanpa satu pun jawaban.
        var reasoningChars = 0
        var truncatedByLength = false

        response.body?.byteStream()?.bufferedReader()?.use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ")
                    if (data == "[DONE]") break
                    try {
                        val json = JSONObject(data)
                        val choices = json.optJSONArray("choices")
                        if (choices == null || choices.length() == 0) {
                            // Chunk penutup saat include_usage: `choices` kosong
                            // dan angka pemakaian ada di `usage`.
                            json.optJSONObject("usage")?.let { usage ->
                                promptTokens = usage.optInt("prompt_tokens", 0)
                                completionTokens = usage.optInt("completion_tokens", 0)
                            }
                            continue
                        }
                        val choice = choices.getJSONObject(0)

                        // Parse delta FIRST (tool_calls content), THEN check finish_reason
                        val delta = choice.optJSONObject("delta")
                        if (delta != null) {
                            val content = if (!delta.isNull("content")) delta.optString("content", "") else ""
                            if (!delta.isNull("reasoning_content")) {
                                reasoningChars += delta.optString("reasoning_content", "").length
                            }
                            if (content.isNotEmpty()) {
                                accumulatedContent.append(content)
                                emit(ChatStreamEvent.Delta(content))
                            }

                            val tcArray = delta.optJSONArray("tool_calls")
                            if (tcArray != null) {
                                for (i in 0 until tcArray.length()) {
                                    val tcDelta = tcArray.getJSONObject(i)
                                    val index = tcDelta.optInt("index", 0)
                                    val builder = toolCallBuffers.getOrPut(index) { ToolCallBuilder() }
                                    val func = tcDelta.optJSONObject("function")
                                    if (func != null) {
                                        if (func.has("name")) builder.name = func.optString("name", "")
                                        if (func.has("arguments")) {
                                            builder.arguments.append(func.optString("arguments", ""))
                                        }
                                    }
                                    val tcId = tcDelta.optString("id", "")
                                    if (tcId.isNotEmpty()) builder.id = tcId
                                }
                            }
                        }

                        val finishReason = if (!choice.isNull("finish_reason")) choice.optString("finish_reason") else null
                        if (finishReason != null) {
                            if (finishReason == "length") truncatedByLength = true
                            when (finishReason) {
                                "stop" -> {
                                    emit(ChatStreamEvent.Done(accumulatedContent.toString()))
                                    doneSent = true
                                }
                                "tool_calls" -> {
                                    val toolCalls = toolCallBuffers.entries
                                        .sortedBy { it.key }
                                        .map { (_, builder) -> builder.build() }
                                    emit(ChatStreamEvent.ToolCalls(toolCalls))
                                    doneSent = true
                                }
                                else -> {
                                    emit(ChatStreamEvent.Done(accumulatedContent.toString()))
                                    doneSent = true
                                }
                            }
                            // Jangan `break` di sini: chunk `usage` menyusul dan
                            // kita masih membacanya. Loop berhenti di "[DONE]".
                            continue
                        }
                    } catch (_: Exception) { /* skip malformed SSE chunks */ }
                }
            }
        }
        if (promptTokens > 0 || completionTokens > 0) {
            emit(ChatStreamEvent.Usage(promptTokens, completionTokens))
        }
            // Stream ended without a finish_reason — emit Done so orchestrator stops looping
            if (!doneSent) {
                val text = accumulatedContent.toString()
                // Model reasoning habis token sebelum sempat menjawab. Tanpa ini UI
                // menampilkan balasan kosong tanpa penjelasan.
                emit(
                    ChatStreamEvent.Done(
                        if (text.isBlank() && truncatedByLength && reasoningChars > 0) {
                            "Model sudah berpikir panjang tapi kehabisan token sebelum menjawab. " +
                                "Coba pertanyaan yang lebih singkat."
                        } else {
                            text
                        }
                    )
                )
            }
    }.flowOn(Dispatchers.IO)

    /**
     * Single attempt at tool-aware chat. Sends [tools] in the request body
     * and parses [tool_calls] from the NIM response.
     */
    private suspend fun chatWithToolsAttempt(
        systemPrompt: String,
        history: List<ChatMessage>,
        tools: List<Map<String, Any?>>,
        maxTokens: Int,
        temperature: Double
    ): Result<ChatResponse> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("model", NimConfig.LLM_MODEL)
                put("temperature", temperature)
                put("max_tokens", maxTokens)
                put("stream", false)
                put("chat_template_kwargs", JSONObject().put("enable_thinking", false))
                put("tools", toJsonValue(tools))
                put("messages", JSONArray().apply {
                    // System prompt first
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    // Then full conversation history
                    history.forEach { msg ->
                        put(serializeChatMessageWithTools(msg))
                    }
                })
            }

            val requestBody = body.toString().toByteArray(Charsets.UTF_8).toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("${NimConfig.BASE_URL}/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = chatClient.newCall(request).await()
            val responseBody = response.body?.string()
                ?: return@withContext Result.failure(Exception("Empty response"))

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("NIM API error ${response.code}: ${responseBody.take(200)}")
                )
            }

            val json = JSONObject(responseBody)
            val choice = json.getJSONArray("choices").getJSONObject(0)
            val message = choice.getJSONObject("message")
            val content = message.optString("content", "")
            val finishReason = choice.optString("finish_reason", null)
            val usage = json.optJSONObject("usage")
            val promptTokens = usage?.optInt("prompt_tokens", 0) ?: 0
            val completionTokens = usage?.optInt("completion_tokens", 0) ?: 0

            // Parse native tool_calls from response
            val toolCalls = parseResponseToolCalls(message)

            Result.success(ChatResponse(content, finishReason, promptTokens, completionTokens, toolCalls))

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isValidJson(content: String): Boolean {
        val trimmed = content.trim()
        var text = trimmed
        if (text.startsWith("```")) {
            val lines = text.split("\n")
            val filteredLines = lines.filterIndexed { index, line ->
                !(index == 0 && line.trim().startsWith("```")) &&
                    !(index == lines.lastIndex && line.trim() == "```")
            }
            text = filteredLines.joinToString("\n").trim()
        }
        val jsonPattern = Regex("""(\{.*\}|\[.*\])""", RegexOption.DOT_MATCHES_ALL)
        val match = jsonPattern.find(text)
        if (match != null) {
            text = match.value.trim()
        }
        return try {
            if (text.startsWith("[")) {
                JSONArray(text)
                true
            } else if (text.startsWith("{")) {
                JSONObject(text)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun chatSingleAttempt(
        model: String,
        systemPrompt: String,
        userMessage: String,
        maxTokens: Int,
        temperature: Double
    ): Result<ChatResponse> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("model", model)
                put("temperature", temperature)
                put("max_tokens", maxTokens)
                put("stream", false)
                put("chat_template_kwargs", JSONObject().put("enable_thinking", false))
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userMessage)
                    })
                })
            }

            val requestBody = body.toString().toByteArray(Charsets.UTF_8).toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("${NimConfig.BASE_URL}/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = client.newCall(request).await()
            val responseBody = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("NIM API error ${response.code}: ${responseBody.take(200)}")
                )
            }

            val json = JSONObject(responseBody)
            val choice = json.getJSONArray("choices").getJSONObject(0)
            val message = choice.getJSONObject("message")
            val content = message.optString("content", "") ?: ""
            val finishReason = choice.optString("finish_reason", null)
            val usage = json.optJSONObject("usage")
            val promptTokens = usage?.optInt("prompt_tokens", 0) ?: 0
            val completionTokens = usage?.optInt("completion_tokens", 0) ?: 0

            Result.success(ChatResponse(content, finishReason, promptTokens, completionTokens))

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun okhttp3.Call.await(): okhttp3.Response {
        return withContext(Dispatchers.IO) { execute() }
    }

    /** Serialize a [ChatMessage] to JSON, including tool_calls (assistant) and tool_call_id (tool). */
    private fun serializeChatMessageWithTools(msg: ChatMessage): JSONObject = JSONObject().apply {
        put("role", msg.role)
        put("content", msg.content)
        if (msg.role == ChatMessage.ROLE_ASSISTANT && msg.toolCalls.isNotEmpty()) {
            put("tool_calls", JSONArray().apply {
                msg.toolCalls.forEach { tc ->
                    put(JSONObject().apply {
                        put("id", tc.id)
                        put("type", "function")
                        put("function", JSONObject().apply {
                            put("name", tc.name)
                            put("arguments", tc.argumentsJson)
                        })
                    })
                }
            })
        }
        if (msg.role == ChatMessage.ROLE_TOOL && msg.toolCallId != null) {
            put("tool_call_id", msg.toolCallId)
        }
    }

    /**
     * Parse [ToolCall] list from NIM response message.
     * Returns empty list if no tool_calls field present.
     */
    private fun parseResponseToolCalls(message: JSONObject): List<ToolCall> {
        val toolCallsArr = message.optJSONArray("tool_calls") ?: return emptyList()
        return (0 until toolCallsArr.length()).mapNotNull { i ->
            val tc = toolCallsArr.optJSONObject(i) ?: return@mapNotNull null
            val func = tc.optJSONObject("function") ?: return@mapNotNull null
            ToolCall(
                id = tc.optString("id", ""),
                name = func.optString("name", ""),
                argumentsJson = func.optString("arguments", "{}")
            )
        }
    }

    /** Recursively convert Kotlin values to org.json types. */
    private fun toJsonValue(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is Map<*, *> -> JSONObject().apply { value.forEach { (k, v) -> put(k.toString(), toJsonValue(v)) } }
        is List<*> -> JSONArray().apply { value.forEach { put(toJsonValue(it)) } }
        is Number, is Boolean, is String -> value
        else -> value.toString()
    }

    /** Buffer tool call arguments from fragmented SSE chunks. */
    private class ToolCallBuilder {
        var id: String = ""
        var name: String = ""
        val arguments = StringBuilder()

        fun build(): ToolCall = ToolCall(
            id = id,
            name = name,
            argumentsJson = arguments.toString()
        )
    }
}
