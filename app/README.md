# HartaKu AI

Aplikasi Android untuk mencatat transaksi pribadi dan memantau anggaran. Antarmuka dibangun dengan Kotlin dan Jetpack Compose.

## Fitur

- Tambah transaksi secara manual, lewat suara, atau dengan memindai struk.
- Kelola kategori dan anggaran; lihat ringkasan serta grafik pengeluaran.
- Gunakan NVIDIA NIM untuk parsing transaksi, analisis struk, dan embedding; Groq Whisper untuk transkripsi suara.

## Teknologi

- Kotlin, Jetpack Compose, ViewModel, repository, dan dependency container manual.
- Room dengan SQLCipher untuk database lokal terenkripsi; kunci database dikelola melalui Android Keystore dan EncryptedSharedPreferences.
- ObjectBox HNSW untuk indeks embedding transaksi.
- OkHttp untuk panggilan langsung dari aplikasi ke API NVIDIA NIM dan Groq. Repository ini tidak menyertakan backend.

## Persyaratan

- Android Studio dengan Android SDK Platform 36 (extension 1).
- JDK yang kompatibel dengan Gradle Wrapper 9.3.1.
- Perangkat atau emulator Android 7.0 (API 24) atau lebih baru.

## Menyiapkan API key

Fitur AI yang memakai layanan cloud memerlukan API key. Salin berkas contoh lalu isi nilainya di komputer lokal:

```powershell
Copy-Item .env.example .env
```

Pada macOS/Linux:

```sh
cp .env.example .env
```

Isi nilai berikut di `.env`:

- `NVIDIA_API_KEY` — NVIDIA NIM.
- `GROQ_API_KEY` — Groq Whisper.

`.env` dan berkas signing lokal diabaikan oleh Git. Jangan commit API key, keystore, atau password signing.

Urutan resolusi key saat konfigurasi build: `.env` menang lebih dulu, lalu environment variable sebagai fallback, lalu string kosong (fitur AI nonaktif). File `.env` hanya dibaca sekali saat konfigurasi Gradle, tidak pernah di.runtime aplikasi.
API key dipakai dari aplikasi Android; jangan menganggap key yang dimasukkan ke APK sebagai rahasia yang tidak dapat diekstrak.

Saat fitur AI digunakan, data yang diperlukan—misalnya teks transaksi, gambar struk, atau audio—dikirim langsung ke penyedia API terkait.

## Build dan jalankan

Windows PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:installDebug
```

macOS/Linux:

```sh
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

APK debug berada di `app/build/outputs/apk/debug/app-debug.apk`.

## Pengujian

Unit tests:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Instrumented tests memerlukan emulator atau perangkat terhubung:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Di macOS/Linux, gunakan `./gradlew` sebagai pengganti `.\gradlew.bat`.

## Struktur utama

- `app/src/main/java/com/example/ui/` — layar Jetpack Compose.
- `app/src/main/java/com/example/data/` — Room, repository, dan model data.
- `app/src/main/java/com/example/ai/` — klien API dan pemrosesan AI.
- `app/src/test/` — unit tests.
