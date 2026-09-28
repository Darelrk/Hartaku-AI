package com.example.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * NIM embedding client — panggil `nvidia/nemotron-3-embed-1b` (2048-dim) via NIM
 * OpenAI-compatible /v1/embeddings endpoint. Output dimensions couple with
 * [com.example.data.vector.TransactionVectorEntity.embedding]'s
 * `@HnswIndex(dimensions = 2048)` — change both together if switching models.
 */
class NimEmbeddingClient(
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    private val TAG = "NimEmbeddingClient"
    // embed-qa-4 terdaftar di katalog tapi 404 "Not found for account". Yang
    // ter-provision hanya 2048-dim, dan parameter `dimensions` menolak nilai lain.
    private val model = "nvidia/nemotron-3-embed-1b"
    private val endpoint = "${NimConfig.BASE_URL}/embeddings"
    private val expectedDim = 2048

    private fun parseResponse(body: String): FloatArray {
        val arr = JSONObject(body).getJSONArray("data")
        if (arr.length() == 0) {
            throw IllegalStateException("Empty embeddings array")
        }
        val embedding = arr.getJSONObject(0).getJSONArray("embedding")
        if (embedding.length() != expectedDim) {
            throw IllegalStateException(
                "Unexpected embedding dim: ${embedding.length()}, expected $expectedDim"
            )
        }
        return FloatArray(expectedDim) { i -> embedding.getDouble(i).toFloat() }
    }

    private fun parseResponseBatch(body: String, reqLen: Int): List<FloatArray> {
        val arr = JSONObject(body).getJSONArray("data")
        Log.d(TAG, "batch requested=$reqLen responseData=${arr.length()} bodyLen=${body.length}")
        return List(arr.length()) { i ->
            val embedding = arr.getJSONObject(i).getJSONArray("embedding")
            if (embedding.length() != expectedDim) {
                throw IllegalStateException(
                    "Unexpected embedding dim: ${embedding.length()}, expected $expectedDim"
                )
            }
            FloatArray(expectedDim) { j -> embedding.getDouble(j).toFloat() }
        }
    }

    private fun postRequest(req: JSONObject) = Request.Builder()
        .url(endpoint)
        .addHeader("Authorization", "Bearer $apiKey")
        .addHeader("Content-Type", "application/json")
        .post(req.toString().toRequestBody(JSON))
        .build()

    private fun executeOrThrow(request: okhttp3.Request): String {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string()
                ?: throw IllegalStateException("Empty response body")
            if (!response.isSuccessful) {
                throw IllegalStateException("NIM API error ${response.code}: $body")
            }
            return body
        }
    }

    suspend fun embed(text: String, isQuery: Boolean = false): Result<FloatArray> = withContext(Dispatchers.IO) {
        runCatching {
            val req = JSONObject().apply {
                put("model", model)
                put("input", JSONArray(listOf(text)))
                put("input_type", if (isQuery) "query" else "passage")
            }
            parseResponse(executeOrThrow(postRequest(req)))
        }.onFailure { Log.w(TAG, "embed() failed: ${it.message}") }
    }

    suspend fun embedBatch(texts: List<String>, isQuery: Boolean = false): Result<List<FloatArray>> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext Result.success(emptyList())
        runCatching {
            val req = JSONObject().apply {
                put("model", model)
                put("input", JSONArray(texts))
                put("input_type", if (isQuery) "query" else "passage")
            }
            parseResponseBatch(executeOrThrow(postRequest(req)), texts.size)
        }.onFailure { Log.w(TAG, "embedBatch() failed: ${it.message}") }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
