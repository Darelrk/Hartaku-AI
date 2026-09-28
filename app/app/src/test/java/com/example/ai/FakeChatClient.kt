package com.example.ai

class FakeChatClient : ChatClient {
    var responseToReturn: Result<ChatResponse>? = null
    var lastSystemPrompt: String? = null
    var lastUserMessage: String? = null
    var callCount = 0
    val responsesToReturnList = mutableListOf<Result<ChatResponse>>()
    var responseMapper: ((String) -> Result<ChatResponse>)? = null

    // For tool call testing
    var toolCallToReturn: ToolCall? = null
    var toolAnswerToReturn: String? = null
    var toolCallCount = 0

    // Multi-turn support: per-turn responses
    var turnContent: Map<Int, String> = emptyMap()
    var turnToolCalls: Map<Int, List<ToolCall>> = emptyMap()
    var lastMessages: List<ChatMessage>? = null
    var lastHistory: List<ChatMessage>? = null
    private var turnCounter = 0
    private var hasReturnedToolCall = false

    fun resetTurns() {
        turnCounter = 0
        lastMessages = null
        lastHistory = null
        hasReturnedToolCall = false
    }

    override suspend fun chat(
        model: String,
        systemPrompt: String,
        userMessage: String,
        maxTokens: Int,
        temperature: Double
    ): Result<ChatResponse> {
        callCount++
        lastSystemPrompt = systemPrompt
        lastUserMessage = userMessage
        
        val mapperRes = responseMapper?.invoke(userMessage)
        if (mapperRes != null) return mapperRes
        
        if (responsesToReturnList.isNotEmpty()) {
            return responsesToReturnList.removeAt(0)
        }
        
        return responseToReturn ?: Result.failure(Exception("No fake response set"))
    }

    override suspend fun chatWithTools(
        systemPrompt: String,
        history: List<ChatMessage>,
        maxTokens: Int,
        temperature: Double
    ): Result<ChatResponse> {
        toolCallCount++
        lastSystemPrompt = systemPrompt
        lastHistory = history
        lastMessages = history

        // If turn-specific configs are set, use the per-turn approach
        if (turnToolCalls.isNotEmpty() || turnContent.isNotEmpty()) {
            val currentTurn = turnCounter
            turnCounter++

            val content = turnContent[currentTurn] ?: toolAnswerToReturn ?: ""
            val toolCalls = turnToolCalls[currentTurn] ?: emptyList()

            return Result.success(
                ChatResponse(
                    content = content,
                    finishReason = if (toolCalls.isNotEmpty()) "tool_calls" else "stop",
                    promptTokens = 10,
                    completionTokens = 10,
                    toolCalls = toolCalls
                )
            )
        }

        // Legacy mode: first call returns tool call, subsequent calls return answer
        return if (!hasReturnedToolCall && toolCallToReturn != null) {
            hasReturnedToolCall = true
            Result.success(
                ChatResponse(
                    content = "",
                    finishReason = "tool_calls",
                    promptTokens = 10,
                    completionTokens = 10,
                    toolCalls = listOf(toolCallToReturn!!)
                )
            )
        } else {
            Result.success(
                ChatResponse(
                    content = toolAnswerToReturn ?: "Default fake answer",
                    finishReason = "stop",
                    promptTokens = 10,
                    completionTokens = 10,
                    toolCalls = emptyList()
                )
            )
        }
    }
}

