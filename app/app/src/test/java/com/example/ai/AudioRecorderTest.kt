package com.example.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
class AudioRecorderTest {

    private lateinit var context: Context
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        tempDir = Files.createTempDirectory("audio-recorder-test").toFile()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun init_createsOutputDir() {
        val nested = File(tempDir, "nested/sub")
        assertFalse("precondition: nested dir must not exist", nested.exists())

        AudioRecorder(context, nested)

        assertTrue("AudioRecorder init must create outputDir", nested.exists())
        assertTrue("outputDir must be a directory", nested.isDirectory)
    }

    @Test
    fun isActive_initiallyFalse() {
        val recorder = AudioRecorder(context, tempDir)

        assertFalse(recorder.isActive())
    }

    @Test
    fun cleanup_deletesAllFiles() {
        val recorder = AudioRecorder(context, tempDir)
        // Drop 2 fake recording files into the output dir
        File(tempDir, "rec-001.wav").writeBytes(ByteArray(8))
        File(tempDir, "rec-002.wav").writeBytes(ByteArray(8))
        assertEquals(2, tempDir.listFiles()!!.size)

        recorder.cleanup()

        assertEquals(0, tempDir.listFiles()!!.size)
    }
}
