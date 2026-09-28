package com.example.data

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/** Rilis terbaru HartaKu AI di GitHub, hasil parse `GET /releases/latest`. */
data class UpdateInfo(
    val version: String,
    val releaseUrl: String,
    val releaseName: String
)

/**
 * Cek update lewat GitHub Releases API tanpa token. Endpoint `latest` sudah
 * mengecualikan draft dan prerelease, jadi tidak ada filter tambahan di sini.
 *
 * Tanpa token GitHub membatasi 60 request per jam per IP; karena pengecekan
 * dipicu manual dari UI, itu cukup untuk pemakaian normal.
 */
object UpdateChecker {
    const val LATEST_RELEASE_URL = "https://api.github.com/repos/Darelrk/Hartaku-AI/releases/latest"
    const val RELEASES_PAGE = "https://github.com/Darelrk/Hartaku-AI/releases"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Pesan kegagalan sengaja berbahasa Indonesia dan siap tampil apa adanya di
     * dialog — lihat pemetaannya di [fetch].
     */
    suspend fun check(): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        try {
            Result.success(fetch())
        } catch (e: UnknownHostException) {
            Result.failure(IllegalStateException("Tidak ada koneksi internet."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetch(): UpdateInfo {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "HartaKu-AI/${BuildConfig.VERSION_NAME}")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val message = when (response.code) {
                    403 -> "GitHub membatasi jumlah request. Coba lagi beberapa saat lagi."
                    else -> "GitHub merespons ${response.code}."
                }
                throw IllegalStateException(message)
            }

            val body = response.body?.string()
                ?: throw IllegalStateException("Respons GitHub tidak lengkap.")
            val json = JSONObject(body)
            val tag = json.optString("tag_name")
            if (tag.isBlank()) {
                throw IllegalStateException("Respons GitHub tidak lengkap.")
            }

            return UpdateInfo(
                version = tag.removePrefix("v"),
                releaseUrl = json.optString("html_url", RELEASES_PAGE),
                releaseName = json.optString("name", tag)
            )
        }
    }

    /**
     * True bila [latest] lebih baru daripada [current]. Prefiks "v" dari tag
     * GitHub dan sufiks non-numerik `versionName` ("1.0.0-internal") diabaikan;
     * daftar versi dengan panjang berbeda diperlakukan sebagai padding nol.
     */
    fun isNewer(latest: String, current: String): Boolean {
        fun parts(v: String) = v.removePrefix("v").split(".").map {
            it.takeWhile { c -> c.isDigit() }.toIntOrNull() ?: 0
        }
        val l = parts(latest)
        val c = parts(current)
        for (i in 0 until maxOf(l.size, c.size)) {
            val diff = (l.getOrNull(i) ?: 0).compareTo(c.getOrNull(i) ?: 0)
            if (diff != 0) return diff > 0
        }
        return false
    }
}
