package com.example.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import javax.crypto.KeyGenerator

object DatabaseKeyProvider {
    private const val PREFS_NAME = "db_key_prefs"
    private const val KEY_ALIAS = "hartaku_db_key"
    private const val KEY_SIZE = 256

    fun getOrCreateKey(context: Context): ByteArray {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val prefs = EncryptedSharedPreferences.create(
            context, PREFS_NAME, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        val existing = prefs.getString(KEY_ALIAS, null)
        if (existing != null) return existing.decodeHex()

        val key = KeyGenerator.getInstance("AES").apply { init(KEY_SIZE) }.generateKey().encoded
        prefs.edit().putString(KEY_ALIAS, key.encodeHex()).apply()
        return key
    }

    // ponytail: dev fallback — ganti dengan biometric di production
    private fun String.decodeHex(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun ByteArray.encodeHex(): String =
        joinToString("") { "%02x".format(it) }
}
