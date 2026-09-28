package com.example.ai

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WhisperApiClientTest {

    private lateinit var tempDir: File
    private lateinit var audioFile: File
    private var lastRequest: Request? = null
    private var lastRequestBody: String? = null

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("whisper-test").toFile()
        audioFile = File(tempDir, "test.wav").apply {
            writeBytes(ByteArray(16) { it.toByte() })
        }
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun clientReturning(
        status: Int,
        body: String,
    ): OkHttpClient {
        val interceptor = Interceptor { chain ->
            val original = chain.request()
            lastRequest = original
            // Multipart body is one-shot; read it BEFORE handing to next chain.
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

    @Test
    fun transcribe_validJson_returnsSuccess() = runTest {
        val body = """{"text":"makan bakso","language":"id","duration":2.5}"""
        val client = WhisperApiClient("dummy-key", clientReturning(200, body))

        val result = client.transcribe(audioFile)

        assertTrue(result.isSuccess)
        val r = result.getOrNull()!!
        assertEquals("makan bakso", r.text)
        assertEquals("id", r.language)
        assertEquals(2.5, r.duration!!, 0.001)
    }

    @Test
    fun transcribe_emptyText_returnsFailure() = runTest {
        val body = """{"text":""}"""
        val client = WhisperApiClient("dummy-key", clientReturning(200, body))

        val result = client.transcribe(audioFile)

        assertTrue(result.isFailure)
        assertTrue(
            "expected 'Empty transcription' in ${result.exceptionOrNull()?.message}",
            result.exceptionOrNull()?.message?.contains("Empty transcription") == true
        )
    }

    @Test
    fun transcribe_httpError_returnsFailure() = runTest {
        val body = """{"error":{"message":"Invalid API Key"}}"""
        val client = WhisperApiClient("dummy-key", clientReturning(401, body))

        val result = client.transcribe(audioFile)

        assertTrue(result.isFailure)
        assertTrue(
            "expected 'Whisper API error 401' in ${result.exceptionOrNull()?.message}",
            result.exceptionOrNull()?.message?.contains("Whisper API error 401") == true
        )
    }

    @Test
    fun transcribe_missingFile_returnsFailure() = runTest {
        val missing = File(tempDir, "nope.wav")
        val client = WhisperApiClient("dummy-key", clientReturning(200, "{}"))

        val result = client.transcribe(missing)

        assertTrue(result.isFailure)
        assertTrue(
            "expected 'Audio file not found' in ${result.exceptionOrNull()?.message}",
            result.exceptionOrNull()?.message?.contains("Audio file not found") == true
        )
    }

    @Test
    fun transcribe_requestShape_isCorrect() = runTest {
        val body = """{"text":"halo"}"""
        val client = WhisperApiClient("dummy-key", clientReturning(200, body))

        client.transcribe(audioFile)

        val req = lastRequest
        assertNotNull(req)
        assertEquals("/openai/v1/audio/transcriptions", req!!.url.encodedPath)
        assertEquals("Bearer dummy-key", req.header("Authorization"))

        val multipart = lastRequestBody!!
        assertTrue("missing model=whisper-large-v3-turbo in $multipart",
            multipart.contains("name=\"model\"") &&
            multipart.contains("whisper-large-v3-turbo"))
        assertTrue("missing language=id in $multipart",
            multipart.contains("name=\"language\"") && multipart.contains("id"))
        assertTrue("missing response_format=verbose_json in $multipart",
            multipart.contains("name=\"response_format\"") &&
            multipart.contains("verbose_json"))
    }
}
