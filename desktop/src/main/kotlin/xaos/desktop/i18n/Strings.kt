package xaos.desktop.i18n

/**
 * Tutti i dizionari, uno per zona dell'app. Ogni file nuovo in questa
 * cartella si aggiunge qui.
 */
internal val ALL_STRINGS: List<Map<String, Tr>> get() = listOf(
    COMMON_STRINGS,
    PLAYER_STRINGS,
    LIBRARY_STRINGS,
    PHONE_STRINGS,
    SETTINGS_STRINGS,
)

/** Le frasi usate in tutta l'app. */
internal val COMMON_STRINGS: Map<String, Tr> = mapOf(
    // Le frasi con due significati: "frase@contesto" (vedi tc).
    "ALBUM@sezione" to Tr("ALBUMS", "アルバム"),
    "PLAYLIST@sezione" to Tr("PLAYLISTS", "プレイリスト"),
    "TESTO@colore" to Tr("TEXT", "テキスト"),
    "ASCOLTI@sezione" to Tr("STATS", "統計"),

    // La cronologia degli ascolti.
    "ASCOLTATI DI RECENTE" to Tr("RECENTLY PLAYED", "最近再生した曲"),
    "Ascoltati di recente" to Tr("Recently played", "最近再生した曲"),
    "ORA" to Tr("NOW", "たった今"),
    "%d MIN FA" to Tr("%d MIN AGO", "%d分前"),
    "%d H FA" to Tr("%d H AGO", "%d時間前"),
    "IERI" to Tr("YESTERDAY", "昨日"),
    "%d GIORNI FA" to Tr("%d DAYS AGO", "%d日前"),
    "dd/MM" to Tr("MM/dd", "M/d"),
)
