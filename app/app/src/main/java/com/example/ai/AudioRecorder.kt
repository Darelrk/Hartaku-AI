package com.example.ai

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Audio recorder — rekam dari mikrofon, simpan ke file WAV/3GP.
 * Digunakan untuk Whisper API (server-side STT).
 */
class AudioRecorder(private val context: Context, private val outputDir: File) {

    private val TAG = "AudioRecorder"
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var isRecording = false

    init {
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
    }

    /**
     * Mulai rekam audio. Output: WAV file di outputDir.
     * @return File yang sedang direkam
     */
    fun startRecording(): File {
        val timestamp = System.currentTimeMillis()
        val file = File(outputDir, "recording_$timestamp.wav")
        outputFile = file

        recorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.DEFAULT)
            setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT)
            setAudioSamplingRate(16000)  // Whisper optimal: 16kHz
            setAudioEncodingBitRate(64000)
            setOutputFile(file.absolutePath)

            try {
                prepare()
                start()
                isRecording = true
                Log.d(TAG, "Recording started: ${file.absolutePath}")
            } catch (e: IOException) {
                Log.e(TAG, "Failed to start recording", e)
                throw e
            }
        }

        return file
    }

    /**
     * Stop rekam dan return file audio.
     */
    fun stopRecording(): File? {
        if (!isRecording) {
            Log.w(TAG, "Not recording")
            return outputFile
        }

        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recorder", e)
        }

        recorder = null
        isRecording = false

        val file = outputFile
        Log.d(TAG, "Recording stopped: ${file?.absolutePath} (${file?.length()} bytes)")
        return file
    }

    /**
     * Cancel recording dan hapus file.
     */
    fun cancel() {
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {}

        recorder = null
        isRecording = false
        outputFile?.delete()
        outputFile = null
    }

    fun isActive(): Boolean = isRecording

    /**
     * Cleanup — hapus semua file rekaman.
     */
    fun cleanup() {
        outputDir.listFiles()?.forEach { it.delete() }
    }
}