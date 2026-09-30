import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.net.URI
import java.util.zip.ZipFile

/** La versione dell'app desktop: da qui la prendono l'installer e la schermata Informazioni. */
val appVersion = "1.5.2"

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

/*
 * Gli strumenti per scaricare musica, perché l'utente non debba installare
 * nulla: ffmpeg (build LGPL "shared" di BtbN: si può ridistribuire), deno (il
 * motore JavaScript che yt-dlp usa per YouTube) e una prima copia di yt-dlp,
 * che poi Xaos tiene aggiornata da solo. Si scaricano una volta e restano in
 * cache nella cartella di Gradle.
 */
val ffmpegZipUrl = "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-n9.0-latest-win64-lgpl-shared-9.0.zip"
val denoZipUrl = "https://github.com/denoland/deno/releases/download/v2.9.7/deno-x86_64-pc-windows-msvc.zip"
val ytdlpUrl = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe"
val toolsCache = File(gradle.gradleUserHomeDir, "caches/xaos-tools")

fun cached(url: String, name: String, maxAgeDays: Long = 3650): File {
    val file = File(toolsCache, name)
    val fresh = file.isFile && System.currentTimeMillis() - file.lastModified() < maxAgeDays * 86_400_000L
    if (!fresh) {
        toolsCache.mkdirs()
        val tmp = File(file.path + ".part")
        URI(url).toURL().openStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
        tmp.copyTo(file, overwrite = true)
        tmp.delete()
    }
    return file
}

val prepareTools by tasks.registering {
    val out = bundledResources.map { it.dir("common/tools") }
    outputs.dir(out)
    inputs.property("urls", listOf(ffmpegZipUrl, denoZipUrl))
    doLast {
        val dir = out.get().asFile.apply { deleteRecursively(); mkdirs() }
        // ffmpeg: solo gli eseguibili che servono e le loro librerie.
        val ffmpegDir = File(dir, "ffmpeg").apply { mkdirs() }
        ZipFile(cached(ffmpegZipUrl, "ffmpeg-n9.0-lgpl-shared.zip")).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.contains("/bin/") }
                .filter { e -> e.name.substringAfterLast('/').let { it.endsWith(".dll") || it == "ffmpeg.exe" || it == "ffprobe.exe" } }
                .forEach { e -> zip.getInputStream(e).use { i -> File(ffmpegDir, e.name.substringAfterLast('/')).outputStream().use { i.copyTo(it) } } }
        }
        ZipFile(cached(denoZipUrl, "deno-2.9.7.zip")).use { zip ->
            val entry = zip.getEntry("deno.exe")
            zip.getInputStream(entry).use { i -> File(dir, "deno.exe").outputStream().use { i.copyTo(it) } }
        }
        // yt-dlp si riprende se la copia in cache ha più di una settimana.
        cached(ytdlpUrl, "yt-dlp.exe", maxAgeDays = 7).copyTo(File(dir, "yt-dlp.exe"), overwrite = true)
    }
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

            FFmpeg 9.0 (LGPL shared build by BtbN)
              Copyright (C) the FFmpeg developers
              License: GNU Lesser General Public License, version 2.1 or later
              Source code: https://ffmpeg.org/download.html and https://github.com/BtbN/FFmpeg-Builds

            Deno
              Copyright (C) the Deno authors
              License: MIT
              Source code: https://github.com/denoland/deno

            yt-dlp
              License: The Unlicense (public domain)
              Source code: https://github.com/yt-dlp/yt-dlp
            """.trimIndent() + "\n"
        )
    }
}

tasks.matching { it.name == "prepareAppResources" }.configureEach {
    dependsOn(prepareAndroidApk, prepareVlc, prepareAdb, prepareLicenses, buildMediaBridge, prepareTools)
}

/*
 * L'installer vero, con Inno Setup (installer/xaos.iss): aspetto coerente con
 * l'app, tema chiaro o scuro come Windows, "Avvia Xaos" alla fine. Prende la
 * cartella dell'app prodotta da createDistributable, VLC e adb compresi.
 * Inno Setup si installa con: winget install JRSoftware.InnoSetup
 */
val packageInstaller by tasks.registering(Exec::class) {
    group = "compose desktop"
    description = "Costruisce l'installer Inno Setup di Xaos"
    dependsOn("createDistributable")
    val iscc = providers.gradleProperty("iscc").orNull?.let(::File)
        ?: listOf(
            File(System.getenv("LOCALAPPDATA") ?: "", "Programs/Inno Setup 6/ISCC.exe"),
            File("C:/Program Files (x86)/Inno Setup 6/ISCC.exe"),
            File("C:/Program Files/Inno Setup 6/ISCC.exe"),
        ).firstOrNull { it.isFile }
    val appImage = layout.buildDirectory.dir("compose/binaries/main/app/Xaos")
    val output = layout.buildDirectory.dir("installer")
    onlyIf { iscc != null }
    workingDir = file("installer")
    executable = iscc?.path ?: "ISCC.exe"
    args(
        "/Q",
        "/DAppVersion=$appVersion",
        "/DSourceDir=" + appImage.get().asFile.path,
        "/DOutputDir=" + output.get().asFile.path,
        "xaos.iss",
    )
    doLast { logger.lifecycle("Installer: " + output.get().file("Xaos-$appVersion.exe").asFile.path) }
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



