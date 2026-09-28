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

    @Test
    fun testChat_RetryOnInvalidJsonAndSuccessOnThirdAttempt() = runTest {
        var attemptCount = 0
        val interceptor = Interceptor { chain ->
            attemptCount++
            val responseBody = when (attemptCount) {
                1 -> "This is not valid JSON at all"
                2 -> "{\"choices\": [{\"message\": {\"content\": \"broken JSON: {\"}}]}" // Still invalid/broken
                else -> """
                    {
                      "choices": [
                        {
                          "message": {
                            "content": "[{\"type\": \"expense\", \"category\": \"Makanan\", \"amount\": 15000, \"description\": \"Kopi\"}]"
                          },
                          "finish_reason": "stop"
                        }
                      ],
                      "usage": {
                        "prompt_tokens": 100,
                        "completion_tokens": 30
                      }
                    }
                """.trimIndent()
            }
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(responseBody.toResponseBody("application/json".toMediaType()))
                .build()
        }

        val testClient = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        val nimApiClient = NimApiClient("dummy-key", testClient)
        val result = nimApiClient.chat(
            systemPrompt = "system",
            userMessage = "user"
        )

        assertTrue(result.isSuccess)
        assertEquals(3, attemptCount)
        val response = result.getOrThrow()
        assertTrue(response.content.contains("Kopi"))
    }

    @Test
    fun testChat_MaxRetriesExceededFailure() = runTest {
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

        val testClient = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        val nimApiClient = NimApiClient("dummy-key", testClient)
        val result = nimApiClient.chat(
            systemPrompt = "system",
            userMessage = "user"
        )

        assertFalse(result.isSuccess)
        assertEquals(3, attemptCount) // 1 initial + 2 retries
    }
}
