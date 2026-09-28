import org.jetbrains.compose.desktop.application.dsl.TargetFormat

/** La versione dell'app desktop: da qui la prendono l'installer e la schermata Informazioni. */
val appVersion = "1.1.0"

plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.compose") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    id("org.jetbrains.compose") version "1.12.1"
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    // Riproduzione: il motore è il VLC installato sul PC, che legge FLAC, MP3,
    // M4A e tutto il resto. vlcj 4 è la linea per VLC 3.
    implementation("uk.co.caprica:vlcj:4.12.1")
    // Tag e copertine incorporate di MP3, FLAC, M4A, OGG.
    implementation("net.jthink:jaudiotagger:3.0.1")
}

/*
 * L'APK dell'app Android viaggia dentro l'app desktop, fra le risorse del
 * pacchetto: collegando un telefono senza Xaos, lo si può installare da qui.
 * Si prende l'ultimo APK arm64 compilato del progetto Android; se non c'è,
 * l'app desktop funziona lo stesso e semplicemente non propone l'installazione.
 */
val androidApk = file("../app/build/outputs/apk/debug/app-arm64-v8a-debug.apk")
val bundledResources = layout.buildDirectory.dir("bundled-resources")
val prepareAndroidApk by tasks.registering(Copy::class) {
    from(androidApk) { rename { "xaos-android.apk" } }
    into(bundledResources.map { it.dir("common") })
}
tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(prepareAndroidApk) }

compose.desktop {
    application {
        mainClass = "xaos.desktop.MainKt"
        jvmArgs += "-Dxaos.version=$appVersion"

        nativeDistributions {
            appResourcesRootDir.set(bundledResources)
            // jdk.unsupported serve a JNA (vlcj), java.logging a jaudiotagger.
            modules("java.instrument", "jdk.unsupported", "java.logging")
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Xaos"
            packageVersion = appVersion
            description = "Xaos Music Player per desktop"
            windows {
                menuGroup = "Xaos"
                // La stessa icona dell'app Android, ridisegnata dal suo vettore.
                iconFile.set(project.file("src/main/resources/xaos.ico"))
                shortcut = true
                dirChooser = true
                // Fisso per sempre: è ciò che fa riconoscere a Windows una
                // versione nuova come aggiornamento di quella installata.
                upgradeUuid = "64f0ed55-26a1-43a7-8814-f7f5064c6442"
            }
        }
    }
}


