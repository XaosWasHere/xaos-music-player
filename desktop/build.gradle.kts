import org.jetbrains.compose.desktop.application.dsl.TargetFormat

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

compose.desktop {
    application {
        mainClass = "xaos.desktop.MainKt"

        nativeDistributions {
            // jdk.unsupported serve a JNA (vlcj), java.logging a jaudiotagger.
            modules("java.instrument", "jdk.unsupported", "java.logging")
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Xaos"
            packageVersion = "1.0.0"
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

