package com.example.data

import android.content.Context

/**
 * AI operation mode. Currently only CLOUD is active.
 * HYBRID placeholder removed (local inference not implemented).
 */
enum class AiMode(val label: String) {
    CLOUD("Cloud");

    companion object {
        private const val PREFS_NAME = "hartaku_settings"
        private const val KEY_MODE = "ai_mode"

        fun load(context: Context): AiMode {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val name = prefs.getString(KEY_MODE, CLOUD.name) ?: CLOUD.name
            return try { valueOf(name) } catch (_: Exception) { CLOUD }
        }

        fun save(context: Context, mode: AiMode) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_MODE, mode.name).apply()
        }
    }
}