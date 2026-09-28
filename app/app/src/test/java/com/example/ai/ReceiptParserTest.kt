package com.example.ai

import android.graphics.Bitmap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReceiptParserTest {

    private lateinit var bitmap: Bitmap
    private var lastRequestBody: String? = null

    @Before
    fun setUp() {
        bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
    }

    private fun clientReturning(
        status: Int,
        body: String,
    ): OkHttpClient {
        val interceptor = Interceptor { chain ->
            val original = chain.request()
            val sink = Buffer()
            original.body!!.writeTo(sink)
            lastRequestBody = sink.readUtf8()
            Response.Builder()
                .request(original)
                .protocol(Protocol.HTTP_1_1)
                .code(status)
                .message("OK")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
        return OkHttpClient.Builder().addInterceptor(interceptor).build()
    }

    private fun okBody(content: String): String {
        // Escape the inner content for valid JSON: \" and newlines
        val escaped = content
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
        return """{"choices":[{"message":{"content":"$escaped"}}]}"""
    }

    @Test
    fun parse_validResponse_returnsReceiptData() = runTest {
        val json = """{"store":"Warkop","items":[{"name":"Kopi","price":18000,"quantity":1}],"total":18000,"date":"2026-05-28"}"""
        val client = ReceiptParser("dummy-key", clientReturning(200, okBody(json)))

        val result = client.parse(bitmap)

        assertTrue(result.isSuccess)
        val r = result.getOrNull()!!
        assertEquals("Warkop", r.store)
        assertEquals(1, r.items.size)
        assertEquals("Kopi", r.items[0].name)
        assertEquals(18000.0, r.items[0].price, 0.001)
        assertEquals(1, r.items[0].quantity)
        assertEquals(18000.0, r.total, 0.001)
        assertEquals("2026-05-28", r.date)
    }

    @Test
    fun parse_invalidJsonContent_returnsFailure() = runTest {
        val client = ReceiptParser("dummy-key", clientReturning(200, okBody("not json at all")))

        val result = client.parse(bitmap)

        assertTrue(result.isFailure)
    }

    @Test
    fun parse_emptyContent_returnsFailure() = runTest {
        val client = ReceiptParser("dummy-key", clientReturning(200, okBody("")))

        val result = client.parse(bitmap)

        assertTrue(result.isFailure)
    }

    @Test
    fun parse_httpError_returnsFailure() = runTest {
        val client = ReceiptParser("dummy-key", clientReturning(500, """{"error":"server"}"""))

        val result = client.parse(bitmap)

        assertTrue(result.isFailure)
        assertTrue(
            "expected 'Receipt API error 500' in ${result.exceptionOrNull()?.message}",
            result.exceptionOrNull()?.message?.contains("Receipt API error 500") == true
        )
    }

    @Test
    fun parse_requestContainsBase64Image() = runTest {
        val json = """{"store":"X","items":[],"total":0.0,"date":null}"""
        val client = ReceiptParser("dummy-key", clientReturning(200, okBody(json)))

        client.parse(bitmap)

        val body = lastRequestBody!!
        assertTrue("missing image_url in request body",
            body.contains("image_url"))
        // base64 PNG of 100x100 solid color is non-trivial
        assertTrue("missing data URI prefix in request body",
            body.contains("data:image") && body.contains("base64,"))
    }
}
