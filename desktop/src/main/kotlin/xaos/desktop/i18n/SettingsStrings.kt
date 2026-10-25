package xaos.desktop.i18n

/** Impostazioni, editor del tema, dispositivi Beam, servizi in rete e controlli di Windows. */
internal val SETTINGS_STRINGS: Map<String, Tr> = mapOf(
    // Impostazioni
    "IMPOSTAZIONI" to Tr("SETTINGS", "設定"),
    "OGNI MODIFICA VALE SUBITO" to Tr("CHANGES APPLY INSTANTLY", "変更はすぐに反映されます"),
    "LIBRERIA" to Tr("LIBRARY", "ライブラリ"),
    "Le cartelle da cui Xaos legge la musica. Le sottocartelle sono comprese." to Tr(
        "The folders Xaos reads music from. Subfolders are included.",
        "Xaosが音楽を読み込むフォルダです。サブフォルダも含まれます。",
    ),
    "AGGIUNGI CARTELLA" to Tr("ADD FOLDER", "フォルダを追加"),
    "RISCANSIONA" to Tr("RESCAN", "再スキャン"),
    "SCANSIONE %d/%d" to Tr("SCANNING %d/%d", "スキャン中 %d/%d"),
    "ERRORE: %s" to Tr("ERROR: %s", "エラー：%s"),
    "[%d] BRANI · [%d] ALBUM" to Tr("[%d] SONGS · [%d] ALBUMS", "[%d]曲 · [%d]アルバム"),
    "[%d] TESTI IN FILE .LRC" to Tr("[%d] LYRICS IN .LRC FILES", ".LRCファイルの歌詞 [%d]件"),
    "Il telefono legge solo i testi dentro i file: incorporali per vederli anche lì." to Tr(
        "Your phone only reads lyrics embedded in the files: embed them to see them there too.",
        "スマホはファイルに埋め込まれた歌詞だけを読み込みます。スマホでも表示するには埋め込んでください。",
    ),
    "INCORPORA I TESTI" to Tr("EMBED LYRICS", "歌詞を埋め込む"),
    // Deve cominciare con "[" in ogni lingua.
    "[%d] AGGIORNATI" to Tr("[%d] UPDATED", "[%d]件を更新"),
    "DOWNLOAD" to Tr("DOWNLOADS", "ダウンロード"),
    "I brani scaricati dalla ricerca in rete, in MP3 con copertina e metadati." to Tr(
        "Songs downloaded from online search, as MP3s with artwork and metadata.",
        "オンライン検索からダウンロードした曲です。アートワークとメタデータ付きのMP3で保存されます。",
    ),
    "CAMBIA CARTELLA" to Tr("CHANGE FOLDER", "フォルダを変更"),
    "PREDEFINITA" to Tr("DEFAULT", "デフォルト"),
    "ricerca in corso…" to Tr("searching…", "検索中…"),
    "SCHERMO INTERO" to Tr("FULL SCREEN", "全画面"),
    "Lo sfondo del player a schermo intero. Si cambia anche da lì, con il bottone in alto." to Tr(
        "The background of the full-screen player. You can also change it there, with the button at the top.",
        "全画面プレーヤーの背景です。全画面表示中も、上部のボタンで変更できます。",
    ),
    "ASPETTO" to Tr("APPEARANCE", "外観"),
    "TEMA" to Tr("THEME", "テーマ"),
    "SCURO" to Tr("DARK", "ダーク"),
    "CHIARO" to Tr("LIGHT", "ライト"),
    "LINGUA" to Tr("LANGUAGE", "言語"),
    "AUTOMATICA" to Tr("AUTOMATIC", "自動"),
    "La scelta automatica segue la lingua di Windows." to Tr(
        "Automatic follows the Windows language.",
        "「自動」ではWindowsの言語に合わせます。",
    ),
    "DIMENSIONE INTERFACCIA" to Tr("INTERFACE SIZE", "表示サイズ"),
    "FINESTRA" to Tr("WINDOW", "ウィンドウ"),
    "RIDUCI A ICONA" to Tr("MINIMIZE", "最小化"),
    "NELLA BARRA DELLE APPLICAZIONI" to Tr("TO THE TASKBAR", "タスクバーへ"),
    "DIVENTA MINIPLAYER" to Tr("SWITCH TO MINI PLAYER", "ミニプレーヤーにする"),
    "Riducendo Xaos a icona, la finestra diventa il miniplayer: resta in primo piano con copertina o testo e i comandi. Un doppio clic lo riporta a finestra intera." to Tr(
        "When you minimize Xaos, the window turns into the mini player: it stays on top with the artwork or lyrics and the controls. Double-click it to go back to the full window.",
        "Xaosを最小化すると、ウィンドウがミニプレーヤーになります。アートワークまたは歌詞と操作ボタンを表示したまま最前面に表示され、ダブルクリックで元のウィンドウに戻ります。",
    ),
    "Riducendo Xaos a icona, finisce nella barra delle applicazioni come ogni programma. Il miniplayer si apre dal suo pulsante nella barra del player." to Tr(
        "When you minimize Xaos, it goes to the taskbar like any other program. The mini player opens from its button in the player bar.",
        "Xaosを最小化すると、ほかのアプリと同じようにタスクバーに入ります。ミニプレーヤーはプレーヤーバーのボタンから開けます。",
    ),
    "INFORMAZIONI" to Tr("ABOUT", "情報"),
    "VERSIONE" to Tr("VERSION", "バージョン"),
    "in sviluppo" to Tr("development build", "開発版"),
    "non ancora caricato" to Tr("not loaded yet", "まだ読み込まれていません"),
    "non trovato — serve per la sincronizzazione" to Tr("not found — needed for syncing", "見つかりません — 同期に必要です"),
    "DATI" to Tr("DATA", "データ"),
    "PROGETTO" to Tr("PROJECT", "プロジェクト"),

    // Testi da LRCLIB
    "TESTI" to Tr("LYRICS", "歌詞"),
    "All'avvio, i brani senza testo lo cercano su LRCLIB. Si aggiunge al file solo se la durata coincide: è il modo di essere certi che sia la stessa incisione. Se non coincide, non si tocca niente." to Tr(
        "At startup, songs without lyrics look them up on LRCLIB. Lyrics are added to the file only if the duration matches: that's how we know it's the same recording. If it doesn't match, nothing is changed.",
        "起動時に、歌詞のない曲をLRCLIBで検索します。ファイルに追加するのは曲の長さが一致した場合だけです。同じ音源であることを確かめるためで、一致しなければ何も変更しません。",
    ),
    "LRCLIB OCCUPATO: RIPRENDO TRA POCO · [%d/%d] · [%d] AGGIUNTI" to Tr(
        "LRCLIB IS BUSY: RESUMING SHORTLY · [%d/%d] · [%d] ADDED",
        "LRCLIBが混雑中：まもなく再開します · [%d/%d] · [%d]件追加",
    ),
    "RICERCA [%d/%d] · [%d] AGGIUNTI" to Tr("SEARCHING [%d/%d] · [%d] ADDED", "検索中 [%d/%d] · [%d]件追加"),
    "[%d] BRANI CERCATI · [%d] TESTI AGGIUNTI" to Tr("[%d] SONGS SEARCHED · [%d] LYRICS ADDED", "[%d]曲を検索 · 歌詞[%d]件を追加"),

    // Doppioni
    "Spostare %d file nel cestino?" to Tr("Move %d file(s) to the Recycle Bin?", "%d個のファイルをごみ箱に移動しますか？"),
    "Sono copie identiche per formato di brani che restano in libreria (%s in tutto). Finiscono nel Cestino di Windows, da cui puoi recuperarli. Preferiti e playlist passano da soli alla copia che resta." to Tr(
        "These are same-format copies of songs that stay in your library (%s in total). They go to the Windows Recycle Bin, where you can restore them. Favorites and playlists switch to the remaining copy on their own.",
        "ライブラリに残る曲の、同じ形式のコピーです（合計%s）。Windowsのごみ箱に移動するので、あとから元に戻せます。お気に入りとプレイリストは、残るほうのコピーに自動で切り替わります。",
    ),
    "SPOSTA NEL CESTINO" to Tr("MOVE TO RECYCLE BIN", "ごみ箱に移動"),
    "[%d] SPOSTATI NEL CESTINO · [%d] NON RIUSCITI" to Tr("[%d] MOVED TO RECYCLE BIN · [%d] FAILED", "[%d]件をごみ箱に移動 · [%d]件失敗"),
    "[%d] SPOSTATI NEL CESTINO" to Tr("[%d] MOVED TO RECYCLE BIN", "[%d]件をごみ箱に移動"),
    "ANNULLA" to Tr("CANCEL", "キャンセル"),
    "DOPPIONI" to Tr("DUPLICATES", "重複"),
    "Lo stesso brano dello stesso album, nello stesso formato, in più file: di solito copie tornate dal telefono. Le versioni in formati diversi (FLAC, MP3, M4A) non sono doppioni e qui non compaiono. In libreria il brano si vede una volta sola; le copie restano sul disco finché non decidi tu." to Tr(
        "The same song from the same album, in the same format, in more than one file: usually copies that came back from the phone. Versions in different formats (FLAC, MP3, M4A) aren't duplicates and don't show up here. The library shows the song only once; the copies stay on disk until you decide.",
        "同じアルバムの同じ曲が、同じ形式で複数のファイルにあるものです。たいていはスマホから戻ってきたコピーです。形式の異なるバージョン（FLAC、MP3、M4A）は重複ではないため、ここには表示されません。ライブラリには1曲として表示され、コピーは削除を決めるまでディスクに残ります。",
    ),
    "NASCONDI ELENCO" to Tr("HIDE LIST", "リストを隠す"),
    "MOSTRA ELENCO" to Tr("SHOW LIST", "リストを表示"),
    "SPOSTA LE COPIE NEL CESTINO" to Tr("MOVE COPIES TO RECYCLE BIN", "コピーをごみ箱に移動"),
    "[%d] BRANI · [%d] FILE IN PIÙ · %s" to Tr("[%d] SONGS · [%d] EXTRA FILES · %s", "[%d]曲 · 余分なファイル[%d]件 · %s"),
    "TIENE  %s" to Tr("KEEPS  %s", "残す  %s"),
    "COPIA  %s" to Tr("COPY  %s", "コピー  %s"),

    // Cartelle, uscita audio, equalizzatore
    "%s — NON TROVATA" to Tr("%s — NOT FOUND", "%s — 見つかりません"),
    "Togli cartella" to Tr("Remove folder", "フォルダを外す"),
    "USCITA AUDIO" to Tr("AUDIO OUTPUT", "オーディオ出力"),
    "Dove suona Xaos. \"Predefinita\" segue l'uscita scelta in Windows." to Tr(
        "Where Xaos plays. \"Windows default\" follows the output selected in Windows.",
        "Xaosの再生先です。「Windowsの既定」は、Windowsで選んだ出力に従います。",
    ),
    "Predefinita di Windows" to Tr("Windows default", "Windowsの既定"),
    "AGGIORNA ELENCO" to Tr("REFRESH LIST", "リストを更新"),
    "EQUALIZZATORE" to Tr("EQUALIZER", "イコライザー"),
    "ATTIVO" to Tr("ON", "オン"),
    "SPENTO" to Tr("OFF", "オフ"),
    "PREAMP" to Tr("PREAMP", "プリアンプ"),
    "AZZERA" to Tr("RESET", "リセット"),

    // Telefono
    "TELEFONO" to Tr("PHONE", "スマホ"),
    "CARTELLA DI DESTINAZIONE" to Tr("DESTINATION FOLDER", "転送先フォルダ"),
    "APPLICA" to Tr("APPLY", "適用"),
    "È dove Xaos per Android salva anche i download: così i brani del PC finiscono nella sua libreria." to Tr(
        "It's also where Xaos for Android saves its downloads, so songs from the PC end up in its library.",
        "Android版Xaosがダウンロードを保存する場所でもあります。PCの曲がそのままスマホのライブラリに入ります。",
    ),
    "IMPORTATI DAL TELEFONO, SUL PC IN" to Tr("IMPORTED FROM PHONE, SAVED ON PC IN", "スマホから取り込んだ曲のPC上の保存先"),
    "CAMBIA" to Tr("CHANGE", "変更"),
    "VERSIONE DA INVIARE" to Tr("VERSION TO SEND", "転送するバージョン"),
    "ORIGINALE" to Tr("ORIGINAL", "オリジナル"),
    "Se un brano ha anche la copia MP3, va quella: pesa circa un quinto del FLAC." to Tr(
        "If a song also has an MP3 copy, that one goes: it's about a fifth the size of the FLAC.",
        "曲にMP3のコピーもある場合は、そちらを転送します。サイズはFLACの約5分の1です。",
    ),
    "Sempre il file originale: qualità piena, molto più spazio." to Tr(
        "Always the original file: full quality, much more space.",
        "常にオリジナルのファイルを転送します。音質はそのままですが、容量を多く使います。",
    ),
    "[%d] BRANI ESCLUSI DALLA SINCRONIZZAZIONE" to Tr("[%d] SONGS EXCLUDED FROM SYNC", "同期から除外した曲 [%d]曲"),
    "RIPRISTINA TUTTI" to Tr("RESTORE ALL", "すべて戻す"),

    // Xaos Beam
    "NON PARTITO: %s" to Tr("FAILED TO START: %s", "起動できません：%s"),
    "Ascolta la libreria del PC dal telefono, in streaming sulla rete di casa, senza copiarla." to Tr(
        "Listen to your PC library from your phone, streamed over your home network, without copying it.",
        "PCのライブラリを、コピーせずに自宅のネットワーク経由でスマホからストリーミング再生できます。",
    ),
    "PIN PER ABBINARE UN TELEFONO" to Tr("PIN TO PAIR A PHONE", "スマホをペアリングするためのPIN"),
    "Sul telefono: Impostazioni → Xaos Beam → Dispositivi, tocca questo PC e scrivi il PIN. Dopo l'abbinamento il PIN cambia." to Tr(
        "On your phone: Settings → Xaos Beam → Devices, tap this PC and enter the PIN. The PIN changes after pairing.",
        "スマホで「設定」→「Xaos Beam」→「デバイス」を開き、このPCをタップしてPINを入力してください。ペアリング後、PINは変わります。",
    ),
    "NUOVO PIN" to Tr("NEW PIN", "新しいPIN"),
    "INDIRIZZO" to Tr("ADDRESS", "アドレス"),
    "TELEFONI ABBINATI" to Tr("PAIRED PHONES", "ペアリング済みのスマホ"),
    "COLLEGATO ORA" to Tr("CONNECTED NOW", "接続中"),
    "ABBINATO" to Tr("PAIRED", "ペアリング済み"),
    "TOGLI" to Tr("REMOVE", "解除"),
    "Il PC e il telefono devono stare sulla stessa rete Wi-Fi. La prima volta Windows chiede se Xaos può usare la rete: consenti le reti private." to Tr(
        "Your PC and phone must be on the same Wi-Fi network. The first time, Windows asks whether Xaos can use the network: allow private networks.",
        "PCとスマホは同じWi-Fiネットワークに接続してください。初回はXaosのネットワーク使用についてWindowsが確認するので、プライベートネットワークを許可してください。",
    ),
    "impossibile aprire la porta" to Tr("couldn't open the port", "ポートを開けません"),
    "DAL TELEFONO" to Tr("FROM PHONE", "スマホから"),

    // Dispositivi (barra del player)
    "Dispositivi" to Tr("Devices", "デバイス"),
    "DISPOSITIVI" to Tr("DEVICES", "デバイス"),
    "Nessun telefono collegato. Sul telefono apri Xaos con Beam acceso e abbinato a questo PC." to Tr(
        "No phone connected. On your phone, open Xaos with Beam turned on and paired with this PC.",
        "接続中のスマホはありません。スマホでBeamをオンにしてこのPCとペアリングし、Xaosを開いてください。",
    ),
    "ASCOLTA QUI" to Tr("LISTEN HERE", "ここで再生"),
    "MANDA AL TELEFONO" to Tr("PLAY ON PHONE", "スマホで再生"),

    // Tema personalizzato
    "TEMA PERSONALIZZATO" to Tr("CUSTOM THEME", "カスタムテーマ"),
    "Scegli i colori di sfondo, pannelli, testo e accento." to Tr(
        "Choose the colors for the background, panels, text and accent.",
        "背景、パネル、テキスト、アクセントの色を選べます。",
    ),
    "I tuoi colori, sopra il tema scuro." to Tr("Your colors, on top of the dark theme.", "ダークテーマをベースにしたあなたの配色です。"),
    "I tuoi colori, sopra il tema chiaro." to Tr("Your colors, on top of the light theme.", "ライトテーマをベースにしたあなたの配色です。"),
    "SFONDO" to Tr("BACKGROUND", "背景"),
    "Il fondo di tutta l'app" to Tr("The background of the whole app", "アプリ全体の背景"),
    "SFUMATURA" to Tr("GRADIENT", "グラデーション"),
    "Il secondo colore dello sfondo, in diagonale" to Tr("The second background color, on a diagonal", "背景の2色目（斜めのグラデーション）"),
    "PANNELLI" to Tr("PANELS", "パネル"),
    "Schede, menu e finestre" to Tr("Cards, menus and dialogs", "カード、メニュー、ダイアログ"),
    "Testo e icone; le tonalità più tenui se ne ricavano" to Tr(
        "Text and icons; the softer shades are derived from it",
        "テキストとアイコン。淡い色調はこの色から作られます",
    ),
    "ACCENTO" to Tr("ACCENT", "アクセント"),
    "Play, pallini d'accento, selezioni" to Tr("Play, accent dots, selections", "再生ボタン、アクセントのドット、選択項目"),
    "Nessuna: sfondo in tinta unita" to Tr("None: solid background", "なし：単色の背景"),
    "PREDEFINITO" to Tr("DEFAULT", "デフォルト"),
    "Torna al predefinito" to Tr("Reset to default", "デフォルトに戻す"),
    "PALLINI DI SFONDO" to Tr("BACKGROUND DOTS", "背景のドット"),
    "La griglia di punti dietro le schermate" to Tr("The dot grid behind the screens", "画面の背後に表示されるドットのグリッド"),
    "RIPRISTINA I COLORI" to Tr("RESET COLORS", "色をリセット"),
    "CODICE" to Tr("CODE", "カラーコード"),
    "FATTO" to Tr("DONE", "完了"),
    "PRESET" to Tr("PRESETS", "プリセット"),
    "Salva i colori di adesso con un nome, per riapplicarli quando vuoi. I preset arrivano anche sul telefono." to Tr(
        "Save the current colors under a name to reapply them whenever you like. Presets also reach your phone.",
        "現在の色に名前を付けて保存すると、いつでも再適用できます。プリセットはスマホにも同期されます。",
    ),
    "IN USO" to Tr("IN USE", "使用中"),
    "Clic per applicarlo" to Tr("Click to apply", "クリックして適用"),
    "Rinomina" to Tr("Rename", "名前を変更"),
    "Elimina il preset" to Tr("Delete preset", "プリセットを削除"),
    "SALVA COME PRESET" to Tr("SAVE AS PRESET", "プリセットとして保存"),
    "Con un nome già usato, quel preset prende i colori di adesso." to Tr(
        "Using an existing name updates that preset with the current colors.",
        "既存の名前を使うと、そのプリセットが現在の色で上書きされます。",
    ),
    "RINOMINA" to Tr("RENAME", "名前を変更"),
    "NOME" to Tr("NAME", "名前"),
    "Notte, Carta, Ufficio…" to Tr("Night, Paper, Office…", "ナイト、ペーパー、オフィス…"),
    "SALVA" to Tr("SAVE", "保存"),

    // Sfondo del player a schermo intero
    "ONDE" to Tr("WAVEFORM", "波形"),
    "COPERTINA" to Tr("ARTWORK", "アートワーク"),
    "SPENTA" to Tr("OFF", "オフ"),

    // Finestra, download, barra delle applicazioni
    "Aggiungi una cartella di musica" to Tr("Add a music folder", "音楽フォルダを追加"),
    "Ricerca non riuscita" to Tr("Search failed", "検索に失敗しました"),
    "Download non riuscito" to Tr("Download failed", "ダウンロードに失敗しました"),
    "yt-dlp non trovato" to Tr("yt-dlp not found", "yt-dlpが見つかりません"),
    "yt-dlp ha terminato con errore (%d)" to Tr("yt-dlp exited with an error (%d)", "yt-dlpがエラーで終了しました（%d）"),
    "Precedente" to Tr("Previous", "前へ"),
    "Successivo" to Tr("Next", "次へ"),
    "Pausa" to Tr("Pause", "一時停止"),
    "Riproduci" to Tr("Play", "再生"),
)
