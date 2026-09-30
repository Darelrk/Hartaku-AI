package com.example.ai

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NimApiClientTest {

    /**
     * Kontrak: gagal cepat, tanpa percobaan ulang.
     *
     * Diukur 2026-09-30: hosted NIM dari jaringan ini gagal karena
     * SLOW (26-75 detik), bukan karena error transient. Dengan readTimeout 20s,
     * percobaan ulang hanya menambah waktu tunggu tanpa menambah peluang
     * berhasil. `maxAttempts = 1` supaya parser turun ke fallback regex
     * (yang sudah benar) alih-alih menahan tombol Simpan puluhan detik.
     */
    @Test
    fun testChat_NoRetryOnInvalidJson_FailsAfterSingleAttempt() = runTest {
        var attemptCount = 0
        val interceptor = Interceptor { chain ->
            attemptCount++
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("This is not valid JSON at all".toResponseBody("application/json".toMediaType()))
                .build()
        }

        val nimApiClient = NimApiClient(
            "dummy-key",
            OkHttpClient.Builder().addInterceptor(interceptor).build()
        )
        val result = nimApiClient.chat(systemPrompt = "system", userMessage = "user")

        assertFalse(result.isSuccess)
        assertEquals(1, attemptCount)
    }

    @Test
    fun testChat_NoRetryOnServerError_FailsAfterSingleAttempt() = runTest {
        var attemptCount = 0
        val interceptor = Interceptor { chain ->
            attemptCount++
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(500)
                .message("Internal Server Error")
                .body("Error".toResponseBody("text/plain".toMediaType()))
                .build()
        }

        val nimApiClient = NimApiClient(
            "dummy-key",
            OkHttpClient.Builder().addInterceptor(interceptor).build()
        )
        val result = nimApiClient.chat(systemPrompt = "system", userMessage = "user")

        assertFalse(result.isSuccess)
        assertEquals(1, attemptCount)
    }

    /** Jalur sukses langsung di percobaan pertama, tanpa retry. */
    @Test
    fun testChat_SucceedsOnFirstAttempt_WithoutRetrying() = runTest {
        var attemptCount = 0
        val interceptor = Interceptor { chain ->
            attemptCount++
            val body = """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "[{\"type\": \"income\", \"category\": \"Gaji\", \"amount\": 5000000, \"description\": \"Gaji bulan ini\"}]"
                      },
                      "finish_reason": "stop"
                    }
                  ],
                  "usage": { "prompt_tokens": 100, "completion_tokens": 30 }
                }
            """.trimIndent()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }

        val nimApiClient = NimApiClient(
            "dummy-key",
            OkHttpClient.Builder().addInterceptor(interceptor).build()
        )
        val result = nimApiClient.chat(systemPrompt = "system", userMessage = "user")

        assertTrue(result.isSuccess)
        assertEquals(1, attemptCount)
        assertTrue(result.getOrThrow().content.contains("Gaji"))
    }
}
