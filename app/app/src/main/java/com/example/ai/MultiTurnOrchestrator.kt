package com.example.ai

import org.json.JSONObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect

/**
 * Multi-turn tool-use orchestrator.
 *
 * Loop controller: calls chatWithTools, executes any tool_calls returned,
 * appends results as role="tool" messages, and repeats until the LLM
 * returns content or maxTurns is reached.
 *
 * Ponytail: single-purpose loop, no abstractions.
 */
class MultiTurnOrchestrator(
    private val chatClient: ChatClient,
    private val toolExecutor: ToolCallExecutor,
    private val maxTurns: Int = 5
) {
    /** Last successful tool result JSON — for post-validation. */
    var lastToolResultJson: String? = null
        private set

    /** Trace AiTrace terakhir — dibaca ChatbotRAGManager setelah query selesai. */
    internal var lastLlmStages: List<AiTraceStage> = emptyList()
        private set
    internal var lastPromptTokens: Int? = null
        private set
    internal var lastCompletionTokens: Int? = null
        private set
    internal val lastToolStages: List<AiTraceStage>
        get() = toolExecutor.lastToolStages

    private fun resetTrace() {
        lastLlmStages = emptyList()
        lastPromptTokens = null
        lastCompletionTokens = null
        toolExecutor.resetTrace()
    }

    suspend fun processQuery(
        userQuery: String,
        systemPrompt: String = "",
        history: List<ChatMessageItem> = emptyList()
    ): String {
        // Clear stale tool result from previous query — jangan sampai validator
        // memakai ground truth milik query sebelumnya.
        lastToolResultJson = null
        resetTrace()
        // Build initial message list from history + user query
        val messages = toChatMessages(history, userQuery)

        repeat(maxTurns) { turn ->
            ChatLogger.d("HartaKu/MTOrch", "Turn ${turn + 1}/$maxTurns messages=${messages.size}")

            val llmStartedAt = System.currentTimeMillis()
            val response = chatClient.chatWithTools(
                systemPrompt = systemPrompt,
                history = messages
            ).getOrElse { err ->
                lastLlmStages = lastLlmStages + AiTraceStage("llm", System.currentTimeMillis() - llmStartedAt)
                ChatLogger.e("HartaKu/MTOrch", "chatWithTools failed turn ${turn + 1}", err)
                return "Maaf, saya sedang bermasalah. Coba lagi ya."
            }
            lastLlmStages = lastLlmStages + AiTraceStage("llm", System.currentTimeMillis() - llmStartedAt)
            if (lastPromptTokens == null) {
                lastPromptTokens = response.promptTokens
                lastCompletionTokens = response.completionTokens
            }

            when (response.finishReason) {
                "stop" -> return response.content.ifBlank {
                    "Maaf, saya tidak bisa menemukan data yang relevan."
                }
                "tool_calls" -> {
                    if (response.toolCalls.isEmpty()) {
                        // No tool calls despite finish_reason — treat as stop
                        return response.content.ifBlank {
                            "Maaf, saya tidak bisa menemukan data yang relevan."
                        }
                    }
                    // Guard: if messages already contain a tool result, model is looping.
                    // Return the current tool result as the final answer instead of looping again.
                    val alreadyHasTool = messages.any { it.role == ChatMessage.ROLE_TOOL }
                    if (alreadyHasTool) {
                        val lastToolMsg = messages.lastOrNull { it.role == ChatMessage.ROLE_TOOL }
                        return lastToolMsg?.content?.takeIf { it.isNotBlank() }
                            ?: "Maaf, saya tidak bisa menemukan data yang relevan."
                    }
                    for (toolCall in response.toolCalls) {
                        val result = toolExecutor.execute(toolCall)
                        lastToolResultJson = result.content
                        val formatted = AnswerValidator.regenerateFromContext(result.content, result.content)
                        messages.add(
                            ChatMessage.tool(toolCall.id, formatted)
                        )
                    }
                }
                else -> {
                    if (response.content.isNotBlank()) return response.content
                }
            }
        }

        return "Saya sudah mencoba beberapa kali tapi belum bisa menjawab dengan pasti. " +
               "Coba pertanyaan yang lebih spesifik."
    }

    suspend fun processQueryStream(
        userQuery: String,
        systemPrompt: String = "",
        history: List<ChatMessageItem> = emptyList()
    ): Flow<String> = channelFlow {
        lastToolResultJson = null
        resetTrace()
        val messages = toChatMessages(history, userQuery)
        var done = false

        repeat(maxTurns) { turn ->
            if (done) return@repeat
            val streamStartedAt = System.currentTimeMillis()
            chatClient.chatStreamWithTools(
                systemPrompt = systemPrompt,
                history = messages
            ).catch { err ->
                ChatLogger.e("HartaKu/MTOrch", "chatStreamWithTools failed turn ${turn + 1}", err)
                if (!isClosedForSend) send("Maaf, saya sedang bermasalah. Coba lagi ya.")
                if (!isClosedForSend) close(err)
            }.collect { event ->
                when (event) {
                    is ChatStreamEvent.Delta -> {
                        if (!isClosedForSend) send(event.content)
                    }
                    is ChatStreamEvent.ToolCalls -> {
                        ChatLogger.d("HartaKu/MTOrch", "ToolCalls event: ${event.toolCalls.size} tools, hasTool=${messages.any { it.role == ChatMessage.ROLE_TOOL }}")

                        if (event.toolCalls.isEmpty()) {
                            if (!isClosedForSend) send("Maaf, saya tidak bisa menemukan data yang relevan.")
                            done = true
                            close()
                            return@collect
                        }
                        // Guard: if already has tool result, model is looping — return formatted data directly.
                        val alreadyHasTool = messages.any { it.role == ChatMessage.ROLE_TOOL }
                        if (alreadyHasTool) {
                            val lastTool = messages.lastOrNull { it.role == ChatMessage.ROLE_TOOL }
                            val answer = lastTool?.content?.takeIf { it.isNotBlank() }
                                ?: "Maaf, saya tidak bisa menemukan data yang relevan."
                            if (!isClosedForSend) send(answer)
                            done = true
                            close()
                            return@collect
                        }

                        for (toolCall in event.toolCalls) {
                            val result = toolExecutor.execute(toolCall)
                            lastToolResultJson = result.content
                            val formatted = AnswerValidator.regenerateFromContext(result.content, result.content)
                            messages.add(ChatMessage.tool(toolCall.id, formatted))
                        }
                    }
                    is ChatStreamEvent.Done -> {
                        done = true
                        close()
                        return@collect
                    }
                }
            }
            lastLlmStages = lastLlmStages + AiTraceStage("llm", System.currentTimeMillis() - streamStartedAt)
        }

        if (!isClosedForSend) {
            send("Saya sudah mencoba beberapa kali tapi belum bisa menjawab dengan pasti. " +
                 "Coba pertanyaan yang lebih spesifik.")
        }
        if (!isClosedForSend) close()
    }

    private fun toChatMessages(
        history: List<ChatMessageItem>,
        userQuery: String
    ): MutableList<ChatMessage> {
        val msgs = ArrayList<ChatMessage>(history.size + 1)
        for (h in history) {
            msgs.add(ChatMessage(h.role, h.content))
        }
        msgs.add(ChatMessage.user(userQuery))
        return msgs
    }
}