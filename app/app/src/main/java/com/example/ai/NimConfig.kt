package com.example.ai

import com.example.BuildConfig

/**
 * Konfigurasi untuk NVIDIA NIM API.
 * API key diambil dari process environment saat konfigurasi build debug melalui BuildConfig.
 */
object NimConfig {
    private const val TAG = "NimConfig"

    // openai/gpt-oss-20b tercantum di /v1/models tapi tidak pernah merespons:
    // nol byte, tanpa status line HTTP, sampai 150 detik — bahkan untuk payload
    // 4-token tanpa tools. Jadi masalahnya di sisi model, bukan harness.
    // Ganti ke nvidia/nemotron-3.5-lightning-30b-a3b: terukur merespons ~1,9s
    // dengan tools maupun tanpa tools, per 2026-09-29.
    const val LLM_MODEL = "nvidia/nemotron-3.5-lightning-30b-a3b"
    const val BASE_URL = "https://integrate.api.nvidia.com/v1"

    // System prompts pindah ke masing-masing parser (dinamis dari DB)
    // Insight prompt — belum dipake, buat Batch 4

    fun getApiKey(): String? = try { BuildConfig.NVIDIA_API_KEY.takeIf { it.isNotEmpty() } } catch (_: Exception) { null }

    fun getGroqApiKey(): String? = try { BuildConfig.GROQ_API_KEY.takeIf { it.isNotEmpty() } } catch (_: Exception) { null }

}