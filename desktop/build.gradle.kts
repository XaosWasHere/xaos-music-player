import org.jetbrains.compose.desktop.application.dsl.TargetFormat

/** La versione dell'app desktop: da qui la prendono l'installer e la schermata Informazioni. */
val appVersion = "1.3.0"

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
// Di norma l'APK sta nella build del progetto Android accanto; con
// -PandroidApkDir si indica un'altra cartella (per esempio se le build
// stanno fuori dal progetto).
val androidApkDir = providers.gradleProperty("androidApkDir").orElse("../app/build/outputs/apk/debug").get()
val androidApk = file("$androidApkDir/app-arm64-v8a-debug.apk")
val androidApkMetadata = file("$androidApkDir/output-metadata.json")
val bundledResources = layout.buildDirectory.dir("bundled-resources")
val prepareAndroidApk by tasks.registering(Copy::class) {
    from(androidApk) { rename { "xaos-android.apk" } }
    into(bundledResources.map { it.dir("common") })
    // Accanto all'APK, la sua versione: l'app desktop la confronta con quella
    // installata sul telefono per proporre l'aggiornamento, e leggerla dal
    // metadato di Gradle evita di dover aprire l'APK sul PC dell'utente.
    inputs.file(androidApkMetadata).optional()
    doLast {
        if (!androidApkMetadata.isFile) return@doLast
        @Suppress("UNCHECKED_CAST")
        val meta = groovy.json.JsonSlurper().parse(androidApkMetadata) as Map<String, Any?>
        val elements = meta["elements"] as List<Map<String, Any?>>
        val arm64 = elements.firstOrNull { e ->
            (e["filters"] as List<Map<String, Any?>>).any { it["value"] == "arm64-v8a" }
        } ?: elements.first()
        bundledResources.get().file("common/xaos-android.properties").asFile.writeText(
            "versionCode=${arm64["versionCode"]}\nversionName=${arm64["versionName"]}\n"
        )
    }
}
/*
 * VLC e adb viaggiano con l'app, così chi la installa non deve procurarseli.
 *
 * Di VLC si prende solo quello che serve all'audio da file locali: il motore,
 * l'accesso ai file, i demuxer, i decoder audio, i filtri (equalizzatore
 * compreso) e le uscite audio. Tutta la parte video resta fuori: sono oltre
 * cento megabyte che un player musicale non usa. La cache dei plugin si genera
 * qui, sul sottoinsieme incluso: senza, VLC riesamina ogni plugin a ogni avvio.
 */
val vlcDir = providers.gradleProperty("vlcDir").orElse("C:/Program Files/VideoLAN/VLC").get()
val platformToolsDir = providers.gradleProperty("platformToolsDir")
    .orElse((System.getenv("LOCALAPPDATA") ?: "") + "/Android/Sdk/platform-tools").get()
val vlcAudioPluginDirs = listOf("demux", "packetizer", "audio_filter", "audio_mixer", "audio_output", "stream_filter")
val vlcAudioCodecs = listOf(
    "avcodec", "mpg123", "flac", "vorbis", "opus", "araw", "lpcm", "adpcm", "faad", "a52", "dca", "speex", "mpeg_audio",
)

val prepareVlc by tasks.registering(Copy::class) {
    from(vlcDir) { include("libvlc.dll", "libvlccore.dll") }
    from("$vlcDir/plugins") {
        vlcAudioPluginDirs.forEach { include("$it/**") }
        include("access/libfilesystem_plugin.dll")
        // Per disegnare le onde del brano: VLC lo converte in un WAV leggero,
        // molto più in fretta del tempo reale.
        include(
            "stream_out/libstream_out_transcode_plugin.dll",
            "stream_out/libstream_out_standard_plugin.dll",
            "mux/libmux_wav_plugin.dll",
            "access_output/libaccess_output_file_plugin.dll",
        )
        vlcAudioCodecs.forEach { include("codec/lib${it}_plugin.dll") }
        exclude("**/plugins.dat")
        into("plugins")
    }
    into(bundledResources.map { it.dir("common/vlc") })
    doLast {
        val cacheGen = File(vlcDir, "vlc-cache-gen.exe")
        val plugins = bundledResources.get().dir("common/vlc/plugins").asFile
        if (cacheGen.isFile) {
            val code = ProcessBuilder(cacheGen.path, plugins.path).inheritIO().start().waitFor()
            if (code != 0) logger.warn("vlc-cache-gen ha restituito $code: VLC partirà senza cache dei plugin")
        }
    }
}

val prepareAdb by tasks.registering(Copy::class) {
    from(platformToolsDir) { include("adb.exe", "AdbWinApi.dll", "AdbWinUsbApi.dll", "libwinpthread-1.dll") }
    into(bundledResources.map { it.dir("common/adb") })
}

/*
 * Il ponte con i controlli multimediali di Windows (riquadro audio, tasti
 * multimediali): un programmino C# compilato con il csc di .NET Framework,
 * che c'è su ogni Windows. Su un PC senza, l'app si costruisce lo stesso e
 * semplicemente non ha quei controlli.
 */
val buildMediaBridge by tasks.registering(Exec::class) {
    val framework = File(System.getenv("WINDIR") ?: "C:/Windows", "Microsoft.NET/Framework64/v4.0.30319")
    val winmd = File(System.getenv("WINDIR") ?: "C:/Windows", "System32/WinMetadata")
    val source = file("src/native/XaosMedia.cs")
    val icon = file("src/main/resources/xaos.ico")
    val out = bundledResources.map { it.file("common/media/XaosMedia.exe") }
    inputs.files(source, icon)
    outputs.file(out)
    onlyIf { File(framework, "csc.exe").isFile }
    doFirst { out.get().asFile.parentFile.mkdirs() }
    executable = File(framework, "csc.exe").path
    args(
        "-nologo", "-target:winexe", "-optimize",
        "-out:" + out.get().asFile.path,
        "-win32icon:" + icon.path,
        "-r:" + File(winmd, "Windows.Media.winmd").path,
        "-r:" + File(winmd, "Windows.Storage.winmd").path,
        "-r:" + File(winmd, "Windows.Foundation.winmd").path,
        "-r:" + File(framework, "System.Runtime.dll").path,
        "-r:" + File(framework, "System.Runtime.InteropServices.WindowsRuntime.dll").path,
        source.path,
    )
}

val prepareLicenses by tasks.registering {
    val out = bundledResources.map { it.file("common/THIRD-PARTY-NOTICES.txt") }
    outputs.file(out)
    doLast {
        out.get().asFile.writeText(
            """
            Xaos includes the following third-party components.

            VLC media player libraries (libvlc, libvlccore and a subset of plugins)
              Copyright (C) VideoLAN and the VLC authors
              License: GNU Lesser General Public License, version 2.1 or later
              Source code: https://www.videolan.org/vlc/download-sources.html

            Android Debug Bridge (adb) from the Android SDK Platform-Tools
              Copyright (C) The Android Open Source Project
              License: Apache License, version 2.0
              Source code: https://android.googlesource.com/platform/packages/modules/adb/
            """.trimIndent() + "\n"
        )
    }
}

tasks.matching { it.name == "prepareAppResources" }.configureEach {
    dependsOn(prepareAndroidApk, prepareVlc, prepareAdb, prepareLicenses, buildMediaBridge)
}

compose.desktop {
    application {
        mainClass = "xaos.desktop.MainKt"
        jvmArgs += "-Dxaos.version=$appVersion"

        nativeDistributions {
            appResourcesRootDir.set(bundledResources)
            // jdk.unsupported serve a JNA (vlcj), java.logging a jaudiotagger,
            // java.net.http alle ricerche in rete (testi su LRCLIB, aggiornamenti).
            modules("java.instrument", "jdk.unsupported", "java.logging", "java.net.http")
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



