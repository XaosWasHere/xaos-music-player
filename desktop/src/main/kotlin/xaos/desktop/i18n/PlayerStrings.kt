package xaos.desktop.i18n

/** La cornice dell'app: barra laterale, barra del player, miniplayer, schermo intero, testi. */
internal val PLAYER_STRINGS: Map<String, Tr> = mapOf(
    // Barra laterale
    "BRANI" to Tr("SONGS", "曲"),
    "ARTISTI" to Tr("ARTISTS", "アーティスト"),
    "PREFERITI" to Tr("FAVORITES", "お気に入り"),
    "ASCOLTI" to Tr("PLAYS", "再生回数"),
    "TELEFONO" to Tr("PHONE", "スマホ"),
    "IMPOSTAZIONI" to Tr("SETTINGS", "設定"),
    "NUOVA VERSIONE %s · SCARICALA" to Tr("NEW VERSION %s · DOWNLOAD", "新しいバージョン %s · ダウンロード"),
    "MUSIC PLAYER · DESKTOP" to Tr("MUSIC PLAYER · DESKTOP", "ミュージックプレーヤー · デスクトップ"),
    "SCANSIONE %d/%d" to Tr("SCANNING %d/%d", "スキャン中 %d/%d"),
    "ERRORE DI LETTURA" to Tr("READ ERROR", "読み込みエラー"),
    "[%d] BRANI IN LIBRERIA" to Tr("[%d] SONGS IN LIBRARY", "ライブラリ内 [%d]曲"),
    "COLLEGATO" to Tr("CONNECTED", "接続済み"),
    "NESSUN TELEFONO" to Tr("NO PHONE", "スマホ未接続"),
    "COLLEGA VIA USB" to Tr("CONNECT VIA USB", "USBで接続"),
    "ADB NON TROVATO" to Tr("ADB NOT FOUND", "ADBが見つかりません"),
    "SERVE ANDROID PLATFORM-TOOLS" to Tr("REQUIRES ANDROID PLATFORM-TOOLS", "Android Platform-Toolsが必要です"),
    "Indietro" to Tr("Back", "戻る"),
    "Cerca brani, album, artisti" to Tr("Search songs, albums, artists", "曲、アルバム、アーティストを検索"),
    "Cancella" to Tr("Clear", "クリア"),

    // Barra del player e miniplayer
    "AVVIO DEL MOTORE AUDIO…" to Tr("STARTING AUDIO ENGINE…", "オーディオエンジンを起動中…"),
    "MOTORE AUDIO NON TROVATO" to Tr("AUDIO ENGINE NOT FOUND", "オーディオエンジンが見つかりません"),
    "Reinstalla Xaos, oppure installa VLC 3" to Tr("Reinstall Xaos, or install VLC 3", "Xaosを再インストールするか、VLC 3をインストールしてください"),
    "NIENTE IN RIPRODUZIONE" to Tr("NOTHING PLAYING", "再生中の曲はありません"),
    "Casuale" to Tr("Shuffle", "シャッフル"),
    "Precedente" to Tr("Previous", "前へ"),
    "Pausa" to Tr("Pause", "一時停止"),
    "Riproduci" to Tr("Play", "再生"),
    "Successivo" to Tr("Next", "次へ"),
    "Ripeti" to Tr("Repeat", "リピート"),
    "Volume" to Tr("Volume", "音量"),
    "Chiudi il testo" to Tr("Hide lyrics", "歌詞を閉じる"),
    "Testo" to Tr("Lyrics", "歌詞"),
    "Miniplayer" to Tr("Mini player", "ミニプレーヤー"),
    "Schermo intero" to Tr("Full screen", "全画面"),
    "SUL TELEFONO · %s" to Tr("ON PHONE · %s", "スマホで再生中 · %s"),
    "Mostra la copertina" to Tr("Show artwork", "アートワークを表示"),
    "Mostra il testo" to Tr("Show lyrics", "歌詞を表示"),
    "Torna a Xaos (anche con doppio clic)" to Tr("Back to Xaos (or double-click)", "Xaosに戻る（ダブルクリックでも可）"),
    "Togli dai preferiti" to Tr("Remove from favorites", "お気に入りから削除"),
    "Aggiungi ai preferiti" to Tr("Add to favorites", "お気に入りに追加"),

    // Schermo intero
    "Chiudi (Esc)" to Tr("Close (Esc)", "閉じる（Esc）"),
    "IN RIPRODUZIONE DA" to Tr("PLAYING FROM", "再生元"),
    "Sfondo: %s" to Tr("Background: %s", "背景：%s"),
    "ONDE" to Tr("WAVEFORM", "波形"),
    "COPERTINA" to Tr("ARTWORK", "アートワーク"),
    "SPENTA" to Tr("OFF", "オフ"),
    "Niente in riproduzione" to Tr("Nothing playing", "再生中の曲はありません"),
    "IN RIPRODUZIONE" to Tr("NOW PLAYING", "再生中"),
    "IN PAUSA" to Tr("PAUSED", "一時停止中"),
    "Pausa (spazio)" to Tr("Pause (Space)", "一時停止（スペース）"),
    "Riproduci (spazio)" to Tr("Play (Space)", "再生（スペース）"),
    "ESC O F11 CHIUDE · SPAZIO PLAY/PAUSA · ← → 10 SECONDI" to
        Tr("ESC OR F11 TO CLOSE · SPACE PLAY/PAUSE · ← → 10 SECONDS", "ESC/F11で閉じる · スペースで再生/一時停止 · ← → 10秒"),
    "A SEGUIRE" to Tr("UP NEXT", "次に再生"),

    // Da dove arriva la coda
    "Ricerca: %s" to Tr("Search: %s", "検索：%s"),
    "Preferiti" to Tr("Favorites", "お気に入り"),
    "Brani" to Tr("Songs", "曲"),
    "DAL TELEFONO" to Tr("FROM PHONE", "スマホから"),

    // Testi
    "NESSUN TESTO" to Tr("NO LYRICS", "歌詞がありません"),
    "Puoi cercarlo online o scriverlo tu: finisce nel file, e arriva anche sul telefono." to Tr(
        "Search for them online or type them in yourself: they're saved in the file and reach your phone too.",
        "オンラインで検索するか、自分で入力できます。歌詞はファイルに保存され、スマホにも反映されます。",
    ),
    "AGGIUNGI IL TESTO" to Tr("ADD LYRICS", "歌詞を追加"),
    "TESTO NON SINCRONIZZATO" to Tr("UNSYNCED LYRICS", "歌詞（同期なし）"),
    "TESTO" to Tr("LYRICS", "歌詞"),
    "Modifica il testo" to Tr("Edit lyrics", "歌詞を編集"),
    "Chiudi" to Tr("Close", "閉じる"),
)
