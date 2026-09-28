package com.example.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class ChatResponse(
    val content: String,
    val finishReason: String?,
    val promptTokens: Int,
    val completionTokens: Int,
    val toolCalls: List<ToolCall> = emptyList()
)

interface ChatClient {
    suspend fun chat(
        model: String = NimConfig.LLM_MODEL,
        systemPrompt: String,
        userMessage: String,
        maxTokens: Int = 256,
        temperature: Double = 0.0
    ): Result<ChatResponse>

    /**
     * Chat with tool-use support. Returns ChatResponse with toolCalls populated
     * when LLM requests tool execution. NIM path uses pseudo-tool-call parsing
     * in [ChatbotRAGManager], so toolCalls is usually empty.
     */
    suspend fun chatWithTools(
        systemPrompt: String,
        history: List<ChatMessage>,
        maxTokens: Int = 1024,
        temperature: Double = 0.0
    ): Result<ChatResponse>
    /**
     * Streaming chat — emits content delta chunks as they arrive from the API.
     * Default wraps [chat] as single-element flow (safe for non-SSE clients).
     */
    suspend fun chatStream(
        model: String = NimConfig.LLM_MODEL,
        systemPrompt: String,
        userMessage: String,
        maxTokens: Int = 256,
        temperature: Double = 0.0
    ): Flow<String> = flow {
        val response = chat(model, systemPrompt, userMessage, maxTokens, temperature)
        response.onSuccess { emit(it.content) }
    }

    /**
     * Streaming chat with tool support — sends [stream] and [tools] in one request.
     * Emits [Delta] for text tokens, [ToolCalls] when model requests tools, [Done] when stream ends.
     * Default fallback wraps [chatWithTools] as single-emit flow (safe for non-SSE clients).
     */
    suspend fun chatStreamWithTools(
        systemPrompt: String,
        history: List<ChatMessage>,
        maxTokens: Int = 1024,
        temperature: Double = 0.0
    ): Flow<ChatStreamEvent> = flow {
        val response = chatWithTools(systemPrompt, history, maxTokens, temperature)
        response.onSuccess { r ->
            if (r.toolCalls.isNotEmpty()) {
                emit(ChatStreamEvent.ToolCalls(r.toolCalls))
            } else {
                emit(ChatStreamEvent.Delta(r.content))
                emit(ChatStreamEvent.Done(r.content))
            }
            // Client non-SSE tetap dilaporkan tokennya supaya Diagnostics
            // terisi juga untuk fake dan fallback apa pun.
            if (r.promptTokens > 0 || r.completionTokens > 0) {
                emit(ChatStreamEvent.Usage(r.promptTokens, r.completionTokens))
            }
        }
    }
}
