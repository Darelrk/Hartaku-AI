package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Receipt parser memakai model vision NIM yang masih ada di katalog.
 * Single model: image → structured JSON langsung.
 * Tidak perlu OCR terpisah — VL model bisa understand receipt context.
 */
class ReceiptParser(
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {

    private val TAG = "ReceiptParser"
    private val endpoint = "https://integrate.api.nvidia.com/v1/chat/completions"
    // nemotron-nano-12b-v2-vl sudah hilang dari katalog (410 Gone).
    private val model = "meta/llama-3.2-11b-vision-instruct"

    data class ReceiptData(
        val store: String,
        val items: List<ReceiptItem>,
        val total: Double,
        val date: String?,
        val rawResponse: String
    )

    data class ReceiptItem(
        val name: String,
        val price: Double,
        val quantity: Int = 1
    )

    companion object {
        private val SYSTEM_PROMPT = """
Extract receipt info from image. Return ONLY valid JSON, no other text.
Format: {"store":string, "items":[{"name":string, "price":number, "quantity":number}], "total":number, "date":string|null}
        """.trimIndent()
    }

    /**
     * Parse receipt image → structured JSON.
     * @param imageBitmap Bitmap dari kamera/gallery
     * @return ReceiptData atau error
     */
    suspend fun parse(imageBitmap: Bitmap): Result<ReceiptData> = withContext(Dispatchers.IO) {
        try {
            val base64 = bitmapToBase64(imageBitmap)
            if (base64.length > 180_000) {
                return@withContext Result.failure(Exception("Image too large (${base64.length} chars). Max ~130KB."))
            }

            val payload = JSONObject().apply {
                put("model", model)
                put("temperature", 0)
                put("max_tokens", 512)
                put("stream", false)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", SYSTEM_PROMPT)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", "image_url")
                                put("image_url", JSONObject().apply {
                                    put("url", "data:image/png;base64,$base64")
                                })
                            })
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", "Extract receipt info from this image.")
                            })
                        })
                    })
                })
            }

            val requestBody = payload.toString().toByteArray(Charsets.UTF_8).toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "API error ${response.code}: ${body.take(300)}")
                return@withContext Result.failure(
                    Exception("Receipt API error ${response.code}: ${body.take(200)}")
                )
            }

            val json = JSONObject(body)
            val content = json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()

            Log.d(TAG, "VL response: $content")

            // Parse JSON from response
            val receiptJson = try {
                JSONObject(content)
            } catch (e: Exception) {
                // Try to extract JSON from markdown
                val match = Regex("""(\{.*\})""", RegexOption.DOT_MATCHES_ALL).find(content)
                if (match != null) JSONObject(match.value)
                else return@withContext Result.failure(Exception("Invalid JSON response"))
            }

            val items = mutableListOf<ReceiptItem>()
            val itemsArray = receiptJson.optJSONArray("items")
            if (itemsArray != null) {
                for (i in 0 until itemsArray.length()) {
                    val item = itemsArray.getJSONObject(i)
                    items.add(ReceiptItem(
                        name = item.optString("name", ""),
                        price = item.optDouble("price", 0.0),
                        quantity = item.optInt("quantity", 1)
                    ))
                }
            }

            val total = receiptJson.optDouble("total", 0.0)
            if (total <= 0.0) {
                return@withContext Result.failure(Exception("Gagal membaca total dari struk. Coba foto ulang atau masukkan manual."))
            }
            Result.success(ReceiptData(
                store = receiptJson.optString("store", "Unknown"),
                items = items,
                total = total,
                date = receiptJson.optString("date", null),
                rawResponse = content
            ))

        } catch (e: Exception) {
            Log.e(TAG, "Parse failed", e)
            Result.failure(e)
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val scaled = scaleBitmap(bitmap, 1024)
        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.PNG, 90, stream)
        return Base64.getEncoder().encodeToString(stream.toByteArray())
    }

    private fun scaleBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxSize && height <= maxSize) return bitmap
        val ratio = maxSize.toFloat() / maxOf(width, height)
        return Bitmap.createScaledBitmap(bitmap, (width * ratio).toInt(), (height * ratio).toInt(), true)
    }
}