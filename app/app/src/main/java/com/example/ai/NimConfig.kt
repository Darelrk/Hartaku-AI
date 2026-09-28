package com.example.ai

import com.example.BuildConfig

/**
 * Konfigurasi untuk NVIDIA NIM API.
 * API key diambil dari process environment saat konfigurasi build debug melalui BuildConfig.
 */
object NimConfig {
    private const val TAG = "NimConfig"

    // nemotron-mini-4b-instruct sudah hilang dari katalog NIM (410 Gone). Diganti
    // gpt-oss-20b — live di katalog NIM per 2026-09-28. Belum ter-benchmark live:
    // key lokal belum punya scope "Public API Endpoints" (semua panggilan inference 403).
    const val LLM_MODEL = "openai/gpt-oss-20b"
    const val BASE_URL = "https://integrate.api.nvidia.com/v1"

    // System prompts pindah ke masing-masing parser (dinamis dari DB)
    // Insight prompt — belum dipake, buat Batch 4

    fun getApiKey(): String? = try { BuildConfig.NVIDIA_API_KEY.takeIf { it.isNotEmpty() } } catch (_: Exception) { null }

    fun getGroqApiKey(): String? = try { BuildConfig.GROQ_API_KEY.takeIf { it.isNotEmpty() } } catch (_: Exception) { null }

}