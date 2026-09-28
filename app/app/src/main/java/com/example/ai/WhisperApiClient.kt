package com.example.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Groq Whisper API client — Speech-to-Text via whisper-large-v3-turbo.
 * Endpoint: https://api.groq.com/openai/v1/audio/transcriptions
 * Model: whisper-large-v3-turbo (fast, 100+ languages, Indonesian support)
 */
class WhisperApiClient(
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {

    private val TAG = "WhisperApiClient"
    private val baseUrl = "https://api.groq.com/openai/v1"
    private val model = "whisper-large-v3-turbo"

    data class TranscriptionResult(
        val text: String,
        val language: String?,
        val duration: Double?,
        val source: String = "whisper"
    )

    /**
     * Transcribe audio file ke text.
     * @param audioFile File audio (WAV, MP3, M4A, WEBM, etc.)
     * @param language Kode bahasa (default: "id" untuk Indonesian)
     */
    suspend fun transcribe(audioFile: File, language: String = "id"): Result<TranscriptionResult> =
        withContext(Dispatchers.IO) {
            try {
                if (!audioFile.exists()) {
                    return@withContext Result.failure(Exception("Audio file not found: ${audioFile.path}"))
                }

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "file",
                        audioFile.name,
                        audioFile.asRequestBody(audioMimeType(audioFile).toMediaType())
                    )
                    .addFormDataPart("model", model)
                    .addFormDataPart("language", language)
                    .addFormDataPart("response_format", "verbose_json")
                    .addFormDataPart("temperature", "0")
                    .build()

                val request = Request.Builder()
                    .url("$baseUrl/audio/transcriptions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    Log.e(TAG, "Whisper API error ${response.code}: ${body.take(300)}")
                    return@withContext Result.failure(
                        Exception("Whisper API error ${response.code}: ${body.take(200)}")
                    )
                }

                val json = JSONObject(body)
                val text = json.optString("text", "").trim()
                val detectedLang = json.optString("language", null)
                val duration = json.optDouble("duration", 0.0)

                if (text.isBlank()) {
                    return@withContext Result.failure(Exception("Empty transcription result"))
                }

                Log.d(TAG, "Transcription: '$text' (lang=$detectedLang, duration=${duration}s)")
                Result.success(TranscriptionResult(text, detectedLang, duration))

            } catch (e: Exception) {
                Log.e(TAG, "Transcribe failed", e)
                Result.failure(e)
            }
    }

    private fun audioMimeType(file: File): String = when {
        file.name.endsWith(".mp3") -> "audio/mpeg"
        file.name.endsWith(".m4a") -> "audio/mp4"
        file.name.endsWith(".webm") -> "audio/webm"
        file.name.endsWith(".ogg") -> "audio/ogg"
        file.name.endsWith(".wav") -> "audio/wav"
        else -> "audio/wav"
    }
}