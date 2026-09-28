package com.example.ai

/**
 * Data classes for tool-use / function-calling support.
 *
 * Wire format mengikuti standar OpenAI / NIM cloud:
 *  - assistant message bisa berisi `tool_calls[]` (saat LLM meminta panggil tool)
 *  - tool result dikirim balik sebagai message dengan `role = "tool"` dan
 *    `tool_call_id` yang cocok dengan id tool_call yang memicu
 */

data class ToolCall(
    val id: String,
    val name: String,
    /** JSON-encoded arguments string, misal `{"days":3,"dateRange":"last_n_days"}` */
    val argumentsJson: String
)

data class ToolResult(
    val callId: String,
    /** String content yang dikembalikan ke LLM (biasanya JSON string dari executor) */
    val content: String
)

/**
 * Satu chat message dengan dukungan opsional untuk tool call.
 *
 * - `toolCalls` terisi saat `role == "assistant"` dan LLM meminta eksekusi tool.
 * - `toolCallId` terisi saat `role == "tool"` (hasil eksekusi), dan harus cocok
 *   dengan `ToolCall.id` yang memicu.
 */
data class ChatMessage(
    val role: String,
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null
) {
    companion object {
        const val ROLE_SYSTEM = "system"
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_TOOL = "tool"

        fun system(content: String) = ChatMessage(ROLE_SYSTEM, content)
        fun user(content: String) = ChatMessage(ROLE_USER, content)
        fun assistant(content: String, toolCalls: List<ToolCall> = emptyList()) =
            ChatMessage(ROLE_ASSISTANT, content, toolCalls)
        fun tool(callId: String, content: String) =
            ChatMessage(ROLE_TOOL, content, toolCallId = callId)
    }
}
