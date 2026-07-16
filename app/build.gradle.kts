// app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.totoro.assistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.totoro.assistant"
        // Picovoice AccessKey. Читається з local.properties (НЕ комітимо!).
        val picovoiceKey: String = gradle.localProperties("PICOVOICE_KEY") ?: ""
        buildConfigField("String", "PICOVOICE_KEY", "\"$picovoiceKey\"")
        minSdk = 24           // Android 7.0+ (більшість сучасних планшетів)
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // Picovoice Porcupine вимагає цих ABI
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Для простого personal-use — НЕ підписуємо release. Вихід - debug-signed APK.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging {
        resources.excludes += setOf("META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-service:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Networking — Hermes / HA / Telegram / Obsidian
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // TTS
    implementation("androidx.compose.material:material-icons-extended")

    // ─── Wake-word (offline, on-device) ───────────────────────
    //  Безкоштовно для особистого використання: console.picovoice.ai
    //  AccessKey вшивається у додаток. Якщо його не вказано —
    //  fallback на SpeechRecognizer (українською) всередині додатку.
    implementation("ai.picovoice:porcupine-android:3.0.2")
    // STT — вбудований Android-розпізнавач + fallback Web Speech API

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
