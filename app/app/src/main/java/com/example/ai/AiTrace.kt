package com.example.ai

/** Satu tahap yang diukur: `embed`, `tool:<nama>`, atau `llm`. */
data class AiTraceStage(val name: String, val ms: Long)

/**
 * Rekam jejak satu query AI. `validation` berisi salah satu dari
 * `OK`, `REGENERATED`, `CACHE`, atau `FALLBACK`.
 */
data class AiTrace(
    val query: String,
    val stages: List<AiTraceStage>,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val validation: String,
    val totalMs: Long
)

/**
 * Ring buffer 20 trace terakhir + satu baris terstruktur ke logcat per query.
 * Sengaja tidak persisten: trace hilang saat proses mati.
 */
object AiTraceLog {
    private const val TAG = "AiTrace"
    private const val MAX = 20
    private val ring = ArrayDeque<AiTrace>()

    fun record(trace: AiTrace) {
        synchronized(ring) {
            if (ring.size >= MAX) ring.removeFirst()
            ring.addLast(trace)
        }
        ChatLogger.d(
            TAG,
            buildString {
                append("q=\"").append(trace.query.take(60)).append("\" ")
                append("total=").append(trace.totalMs).append("ms ")
                trace.stages.forEach { append(it.name).append('=').append(it.ms).append("ms ") }
                append("tokens=").append(trace.promptTokens ?: -1)
                    .append('/').append(trace.completionTokens ?: -1)
                append(" validate=").append(trace.validation)
            }
        )
    }

    fun recent(): List<AiTrace> = synchronized(ring) { ring.toList() }

    fun clear() {
        synchronized(ring) { ring.clear() }
    }
}
