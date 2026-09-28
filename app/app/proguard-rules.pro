# ProGuard/R8 rules for HartaKu AI
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, Exceptions
-keepclasseswithmembernames class * { native <methods>; }

# Room — explicit keep for entities + migrations (Room AAR consumer rules cover generated code, but
# entities reference fields reflectively through _Impl classes at compile-time-generated names)
-keep @androidx.room.Entity class * { *; }
-keep class * extends androidx.room.migration.Migration { *; }

# ObjectBox runtime — its own AAR consumer rules cover entities
-keep class io.objectbox.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Compose — noises
-dontwarn androidx.compose.**

# OkHttp + Okio (NIM API client)
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# ObjectBox vector entities
-keep class com.example.data.vector.** { *; }

# SQLCipher
-keep class net.zetetic.database.sqlcipher.** { *; }

# Coroutines
-keep class kotlinx.coroutines.** { *; }

# Tink / security-crypto
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.errorprone.annotations.**

# ObjectBox native
-keepclasseswithmembernames class io.objectbox.** { native <methods>; }

# Compose runtime — keep reified compiler-plugin classes
-keepclassmembers class androidx.compose.** { *; }
