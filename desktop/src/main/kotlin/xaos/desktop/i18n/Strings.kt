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

    // La qualità dello streaming di Xaos Beam.
    "QUALITÀ DELLO STREAMING" to Tr("STREAMING QUALITY", "ストリーミング音質"),
    "ORIGINALE" to Tr("ORIGINAL", "オリジナル"),
    "ALTA" to Tr("HIGH", "高音質"),
    "LEGGERA" to Tr("LIGHT", "軽量"),
    "I file così come sono: un FLAC hi-res arriva a 3–5 Mbit/s e può riempire la rete." to Tr(
        "Files as they are: a hi-res FLAC needs 3–5 Mbit/s and can fill up the network.",
        "ファイルをそのまま送ります。ハイレゾFLACは3〜5 Mbpsになり、ネットワークを圧迫することがあります。",
    ),
    "La copia MP3 se c'è, altrimenti AAC a 256 kbit/s: a orecchio quasi uguale, fino a 20 volte più leggero. MP3 e AAC restano come sono." to Tr(
        "The MP3 copy if there is one, otherwise AAC at 256 kbit/s: nearly identical to the ear, up to 20 times lighter. MP3 and AAC stay as they are.",
        "MP3のコピーがあればそれを、なければ256 kbpsのAACで送ります。聴感上ほぼ同じで、最大20分の1の軽さです。MP3とAACはそのまま送ります。",
    ),
    // I temi come file .xtheme.
    "TEMA «%s» AGGIUNTO AI PRESET E APPLICATO" to Tr("THEME «%s» ADDED TO PRESETS AND APPLIED", "テーマ「%s」をプリセットに追加して適用しました"),
    "NUOVO TEMA" to Tr("NEW THEME", "NEW THEME"),
    "Rilascia per aggiungerlo ai preset e applicarlo." to Tr("Drop to add it to your presets and apply it.", "ドロップするとプリセットに追加して適用します。"),
    "Trascina qui un file .xtheme per aggiungere il tema." to Tr("Drag an .xtheme file here to add the theme.", ".xthemeファイルをここにドラッグするとテーマを追加できます。"),
    "Esporta come file .xtheme" to Tr("Export as .xtheme file", ".xthemeファイルとして書き出す"),
    "IMPORTA DA FILE" to Tr("IMPORT FROM FILE", "ファイルから読み込む"),
    "I temi si passano come file .xtheme: esportane uno, o trascinane uno in qualunque punto di Xaos per aggiungerlo." to Tr(
        "Themes travel as .xtheme files: export one, or drag one anywhere onto Xaos to add it.",
        "テーマは.xthemeファイルで共有できます。書き出すか、ファイルをXaosのどこかにドラッグすると追加できます。",
    ),
    "Esporta il tema" to Tr("Export theme", "テーマを書き出す"),
    "Importa un tema" to Tr("Import a theme", "テーマを読み込む"),

    "AAC a 128 kbit/s per tutto ciò che pesa di più: per le reti deboli." to Tr(
        "AAC at 128 kbit/s for anything heavier: for weak networks.",
        "それより重いものはすべて128 kbpsのAACで送ります。電波の弱いネットワーク向けです。",
    ),
)
