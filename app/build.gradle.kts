plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.xaosmusicplayer"
    // core-ktx 1.19 e lifecycle 2.11 pretendono di essere compilati contro la 37.
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.xaosmusicplayer"
        minSdk = 24
        targetSdk = 36
        versionCode = 7
        versionName = "1.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // yt-dlp e ffmpeg arrivano come binari nativi: senza filtro finirebbero
        // nell'APK tutte le architetture. x86 a 32 bit non serve più a nessuno.
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
        }
    }

    // I binari devono stare su filesystem per poter essere eseguiti: compressi
    // dentro l'APK non sarebbero avviabili.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    // Un APK per architettura invece di uno solo che le contiene tutte.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)
    implementation(libs.coil.compose)
    // Senza questo Coil carica solo URI locali: le miniature dei risultati
    // online arrivano via https e resterebbero al segnaposto.
    implementation(libs.coil.network.okhttp)
    implementation(libs.youtubedl.library)
    implementation(libs.youtubedl.ffmpeg)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}