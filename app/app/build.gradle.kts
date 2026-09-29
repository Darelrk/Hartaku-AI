plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.kotlin.kapt)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.objectbox)
}

val localEnv = rootProject.file(".env")
    .takeIf { it.exists() }
    ?.readLines()
    ?.mapNotNull { line ->
        val t = line.trim()
        if (t.isEmpty() || t.startsWith("#") || "=" !in t) return@mapNotNull null
        t.substringBefore("=").trim() to t.substringAfter("=").trim().trim('"').trim('\'')
    }?.toMap()
    .orEmpty()

// `.env` is the source of truth; a process env var only fills gaps it doesn't cover.
val nvidiaApiKey = localEnv["NVIDIA_API_KEY"]
    ?: providers.environmentVariable("NVIDIA_API_KEY").orNull ?: ""
val groqApiKey = localEnv["GROQ_API_KEY"]
    ?: providers.environmentVariable("GROQ_API_KEY").orNull ?: ""

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }
  sourceSets {
    getByName("androidTest").assets.srcDir("$projectDir/schemas")
  }

  defaultConfig {
    applicationId = "com.hartakuai.app"
    minSdk = 24
    targetSdk = 36
    versionCode = 3
    versionName = "1.0.3"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // Room schema export — let Room dump the schema JSON to app/schemas/ for
    // migration testing and validation. See AppDatabase @Database(exportSchema = true).
    ksp {
      arg("room.schemaLocation", "$projectDir/schemas")
      arg("room.incremental", "true")
    }
  }

  signingConfigs {
    create("release") {
      // Load dari environment (CI/production), atau file lokal (dev machine).
      // Jangan commit .jks / password ke git.
      val ksPropsFile = rootProject.file("keystore.properties")
      fun envOrProp(name: String, propKey: String): String? =
        System.getenv(name)?.takeIf { it.isNotBlank() }
          ?: ksPropsFile.takeIf { it.exists() }?.readLines()
            ?.firstOrNull { it.startsWith("$propKey=") }
            ?.substringAfter("=")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
      
      storeFile = file(envOrProp("KEYSTORE_PATH", "storeFile") ?: "${rootDir}/my-upload-key.jks")
      storePassword = envOrProp("STORE_PASSWORD", "storePassword")
      keyAlias = envOrProp("KEY_ALIAS", "keyAlias") ?: "upload"
      keyPassword = envOrProp("KEY_PASSWORD", "keyPassword")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      isCrunchPngs = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
      signingConfig = signingConfigs.getByName("release")
      buildConfigField("String", "NVIDIA_API_KEY", "\"$nvidiaApiKey\"")
      buildConfigField("String", "GROQ_API_KEY", "\"$groqApiKey\"")
    }
    debug {
      enableUnitTestCoverage = true
      signingConfig = signingConfigs.getByName("debugConfig")
      buildConfigField("String", "NVIDIA_API_KEY", "\"$nvidiaApiKey\"")
      buildConfigField("String", "GROQ_API_KEY", "\"$groqApiKey\"")
    }
  }
  compileOptions {
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      all { test ->
        // Runner online memanggil NIM sungguhan — opt-in lewat -PevalOnline.
        if (!project.hasProperty("evalOnline")) {
          test.filter.excludeTestsMatching("com.example.eval.OnlineEvalTest")
          test.filter.excludeTestsMatching("com.example.eval.StreamUsageOnlineTest")
        }
      }
    }
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.okhttp)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  androidTestImplementation(libs.androidx.room.testing)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  coreLibraryDesugaring(libs.desugar.jdk.libs)
  implementation(libs.objectbox.android)
  implementation(libs.objectbox.kotlin)
  implementation(libs.androidx.work.runtime.ktx)
  testImplementation(libs.androidx.work.testing)
  implementation(libs.sqlcipher)
  implementation(libs.androidx.sqlite.ktx)
  implementation(libs.androidx.sqlite)
  implementation(libs.androidx.security.crypto)

}
