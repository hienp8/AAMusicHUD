plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.hud.aamusic"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.hud.aamusic"
        minSdk = 26
        targetSdk = 34
        // Luôn tăng theo số lần build trên GitHub để cập nhật đè được
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1) + 1000
        versionName = "3.0"
    }
    // Khoá ký cố định: mọi bản build đều cùng chữ ký nên cài đè không bị báo "không tương thích"
    signingConfigs {
        create("shared") {
            storeFile = file("aamusic.keystore")
            storePassword = "aamusic123"
            keyAlias = "aamusic"
            keyPassword = "aamusic123"
        }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("shared") }
        getByName("release") {
            signingConfig = signingConfigs.getByName("shared")
            isMinifyEnabled = false
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("androidx.media:media:1.7.0")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    // Đọc / ghi tag ID3, FLAC, M4A (bản dành cho Android)
    implementation("com.github.AdrienPoupa:jaudiotagger:2.2.3")
}
