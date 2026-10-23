package com.example.xaosmusicplayer.i18n

/**
 * Tutti i dizionari, uno per zona dell'app. Ogni file nuovo in questa
 * cartella si aggiunge qui.
 */
internal val ALL_STRINGS: List<Map<String, Tr>> get() = listOf(
    COMMON_STRINGS,
    SETTINGS_STRINGS,
    LIBRARY_STRINGS,
    PLAYER_STRINGS,
)

/** Le frasi usate in tutta l'app. */
internal val COMMON_STRINGS: Map<String, Tr> = mapOf(
    // Le frasi con due significati: "frase@contesto" (vedi tc).
    "ALBUM@sezione" to Tr("ALBUMS", "アルバム"),
    "PLAYLIST@sezione" to Tr("PLAYLISTS", "プレイリスト"),
    "TESTO@colore" to Tr("TEXT", "テキスト"),
    "MediaStore ha rifiutato l'inserimento@immagine" to Tr("MediaStore rejected the new image", "MediaStoreが画像の追加を拒否しました"),

    // La cronologia degli ascolti in Home.
    "ASCOLTATI DI RECENTE" to Tr("RECENTLY PLAYED", "最近再生した曲"),
    "ORA" to Tr("NOW", "たった今"),
    "%d MIN FA" to Tr("%d MIN AGO", "%d分前"),
    "%d H FA" to Tr("%d H AGO", "%d時間前"),
    "IERI" to Tr("YESTERDAY", "昨日"),
    "%d GIORNI FA" to Tr("%d DAYS AGO", "%d日前"),
    "dd/MM" to Tr("MM/dd", "M/d"),

    // I temi come file .xtheme.
    "Tema «%s» aggiunto ai preset e applicato" to Tr("Theme «%s» added to presets and applied", "テーマ「%s」をプリセットに追加して適用しました"),
    "Questo file non è un tema di Xaos" to Tr("This file isn't a Xaos theme", "このファイルはXaosのテーマではありません"),
    "Condividi il tema" to Tr("Share theme", "テーマを共有"),
    "Tema di Xaos: %s" to Tr("Xaos theme: %s", "Xaosのテーマ：%s"),
    "IMPORTA DA FILE" to Tr("IMPORT FROM FILE", "ファイルから読み込む"),
    "I temi si passano come file .xtheme: condividine uno, o tocca un file .xtheme ricevuto per aggiungerlo." to Tr(
        "Themes travel as .xtheme files: share one, or tap an .xtheme file you received to add it.",
        "テーマは.xthemeファイルで共有できます。共有するか、受け取った.xthemeファイルをタップすると追加できます。",
    ),
)
