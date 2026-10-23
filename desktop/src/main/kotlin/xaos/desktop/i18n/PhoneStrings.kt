package xaos.desktop.i18n

/** Il telefono: invio e importazione via USB, app Android, dati, tema e testi. */
internal val PHONE_STRINGS: Map<String, Tr> = mapOf(
    // Schermata
    "TELEFONO" to Tr("PHONE", "スマホ"),
    "IMPORTAZIONE VIA USB · SOLO COPIE, SUL TELEFONO NON CAMBIA NIENTE" to
        Tr("IMPORT OVER USB · COPIES ONLY, NOTHING CHANGES ON THE PHONE", "USBでインポート · コピーのみ、スマホ側は変更されません"),
    "INVIO VIA USB · SOLO AGGIUNTE, NIENTE VIENE CANCELLATO" to
        Tr("SEND OVER USB · ADDS ONLY, NOTHING IS EVER DELETED", "USBで転送 · 追加のみ、削除は一切しません"),
    "INVIA AL TELEFONO" to Tr("SEND TO PHONE", "スマホへ転送"),
    "IMPORTA DAL TELEFONO" to Tr("IMPORT FROM PHONE", "スマホからインポート"),

    // Stato del collegamento
    "Serve l'utilità adb di Android (platform-tools). Installala con Android Studio o scaricala da developer.android.com, poi riavvia Xaos." to Tr(
        "Xaos needs Android's adb tool (platform-tools). Install it with Android Studio or download it from developer.android.com, then restart Xaos.",
        "Androidのadbツール（platform-tools）が必要です。Android Studioでインストールするか、developer.android.comからダウンロードして、Xaosを再起動してください。",
    ),
    "COLLEGA IL TELEFONO" to Tr("CONNECT YOUR PHONE", "スマホを接続"),
    "Collegalo con il cavo USB e tieni attivo il Debug USB nelle Opzioni sviluppatore. Xaos lo riconosce da solo in un paio di secondi." to Tr(
        "Connect it with a USB cable and keep USB debugging on in Developer options. Xaos detects it on its own within a couple of seconds.",
        "USBケーブルで接続し、開発者向けオプションで「USBデバッグ」をオンにしてください。数秒でXaosが自動的に認識します。",
    ),
    "CONFERMA SUL TELEFONO" to Tr("CONFIRM ON YOUR PHONE", "スマホで許可"),
    "Sul telefono è comparso \"Consentire il debug USB?\": tocca Consenti (e \"Consenti sempre da questo computer\", così non lo chiede più)." to Tr(
        "Your phone is asking \"Allow USB debugging?\": tap Allow (and check \"Always allow from this computer\" so it won't ask again).",
        "スマホに「USBデバッグを許可しますか？」と表示されています。「許可」をタップしてください（「このパソコンからのUSBデバッグを常に許可する」にチェックすると、次回から確認されません）。",
    ),
    "COLLEGATO" to Tr("CONNECTED", "接続済み"),
    "CARTELLA  %s  ·  VERSIONE  %s" to Tr("FOLDER  %s  ·  FORMAT  %s", "フォルダ  %s  ·  形式  %s"),
    "MP3 QUANDO C'È" to Tr("MP3 WHEN AVAILABLE", "MP3（ある場合）"),
    "ORIGINALE" to Tr("ORIGINAL", "オリジナル"),

    // Invio al telefono
    "CONFRONTO CON LA LIBRERIA DEL TELEFONO…" to Tr("COMPARING WITH THE PHONE'S LIBRARY…", "スマホのライブラリと比較中…"),
    "DA INVIARE" to Tr("TO SEND", "転送対象"),
    "DA COPIARE" to Tr("TO COPY", "コピー容量"),
    "GIÀ SUL TELEFONO" to Tr("ALREADY ON PHONE", "スマホに保存済み"),
    "SPAZIO LIBERO" to Tr("FREE SPACE", "空き容量"),
    "TUTTO SINCRONIZZATO" to Tr("ALL SYNCED", "すべて同期済み"),
    "NIENTE SELEZIONATO" to Tr("NOTHING SELECTED", "未選択"),
    "SINCRONIZZA [%d]" to Tr("SYNC [%d]", "同期 [%d]"),
    "RICONTROLLA" to Tr("CHECK AGAIN", "再確認"),
    "NON C'È ABBASTANZA SPAZIO SUL TELEFONO" to Tr("NOT ENOUGH SPACE ON THE PHONE", "スマホの空き容量が足りません"),
    "ANNULLA" to Tr("CANCEL", "キャンセル"),
    "INTERROTTO" to Tr("STOPPED", "中断"),
    "COMPLETATO" to Tr("DONE", "完了"),
    "[%d] INVIATI" to Tr("[%d] SENT", "[%d] 転送済み"),
    "[%d] NON RIUSCITI" to Tr("[%d] FAILED", "[%d] 失敗"),
    "ERRORE · %s" to Tr("ERROR · %s", "エラー · %s"),
    "Impossibile leggere il telefono" to Tr("Couldn't read the phone", "スマホを読み取れません"),

    // Selezione
    "SELEZIONA TUTTO" to Tr("SELECT ALL", "すべて選択"),
    "DESELEZIONA TUTTO" to Tr("DESELECT ALL", "すべて選択解除"),
    "[%d/%d] SELEZIONATI · %s" to Tr("[%d/%d] SELECTED · %s", "[%d/%d] 選択中 · %s"),
    "Le spunte vengono ricordate: quello che togli resta escluso anche le prossime volte." to Tr(
        "Checkmarks are remembered: anything you uncheck stays excluded next time too.",
        "チェックの状態は保存されます。外した項目は次回以降も除外されます。",
    ),
    "AGGIORNATO" to Tr("UPDATED", "更新済み"),
    "Chiudi" to Tr("Close", "閉じる"),
    "Mostra i brani" to Tr("Show songs", "曲を表示"),

    // Importazione dal telefono
    "SUL PC IN" to Tr("PC FOLDER", "PCの保存先"),
    "Aggiungi una cartella di musica nelle impostazioni" to Tr("Add a music folder in Settings", "設定で音楽フォルダを追加してください"),
    "CONFRONTO FRA TELEFONO E PC…" to Tr("COMPARING PHONE AND PC…", "スマホとPCを比較中…"),
    "DA IMPORTARE" to Tr("TO IMPORT", "インポート対象"),
    "SOLO SUL TELEFONO" to Tr("ONLY ON PHONE", "スマホにのみ"),
    "BRANI SUL TELEFONO" to Tr("SONGS ON PHONE", "スマホの曲"),
    "IL PC HA GIÀ TUTTO" to Tr("THE PC HAS EVERYTHING", "PCにすべてあります"),
    "IMPORTA SUL PC [%d]" to Tr("IMPORT TO PC [%d]", "PCにインポート [%d]"),
    "[%d] COPIATI SUL PC" to Tr("[%d] COPIED TO PC", "[%d] PCにコピー済み"),

    // App Android
    "XAOS PER ANDROID INSTALLATO SUL TELEFONO" to Tr("XAOS FOR ANDROID INSTALLED ON PHONE", "Xaos for Androidはスマホにインストール済み"),
    "XAOS PER ANDROID" to Tr("XAOS FOR ANDROID", "Xaos for Android"),
    "APRI SUL TELEFONO" to Tr("OPEN ON PHONE", "スマホで開く"),
    "INSTALLATO" to Tr("INSTALLED", "インストール済み"),
    "AGGIORNAMENTO DISPONIBILE" to Tr("UPDATE AVAILABLE", "アップデートあり"),
    "NON INSTALLATO" to Tr("NOT INSTALLED", "未インストール"),
    "Il telefono ha ora la versione %s. Playlist e preferiti sono rimasti." to Tr(
        "The phone now has version %s. Playlists and favorites were kept.",
        "スマホはバージョン%sになりました。プレイリストとお気に入りはそのまま残っています。",
    ),
    "Pronto: aprilo sul telefono per dargli l'accesso alla musica." to Tr(
        "Ready: open it on the phone to give it access to your music.",
        "準備完了です。スマホで開いて、音楽へのアクセスを許可してください。",
    ),
    "Sul telefono c'è la %s, qui la %s. L'aggiornamento mantiene playlist, preferiti e impostazioni del telefono." to Tr(
        "The phone has %s, this PC has %s. Updating keeps the phone's playlists, favorites and settings.",
        "スマホには%s、このPCには%sが入っています。アップデートしても、スマホのプレイリスト、お気に入り、設定はそのまま残ります。",
    ),
    "versione precedente" to Tr("an older version", "旧バージョン"),
    "Non è andata: %s" to Tr("That didn't work: %s", "失敗しました：%s"),
    "Questa versione di Xaos desktop non include l'app per il telefono." to Tr(
        "This version of Xaos for desktop doesn't include the phone app.",
        "このバージョンのXaosデスクトップ版には、スマホ用アプリが含まれていません。",
    ),
    "L'app inclusa è per processori arm64, il telefono è %s." to Tr(
        "The included app is for arm64 processors; this phone is %s.",
        "同梱のアプリはarm64プロセッサ用ですが、このスマホは%sです。",
    ),
    "Sul telefono non c'è. Puoi installarlo da qui, oppure continuare con il player che usi già: i brani sincronizzati li vede qualunque app." to Tr(
        "It's not on the phone. You can install it from here, or keep using the player you already have: any app can see synced songs.",
        "スマホにはインストールされていません。ここからインストールすることも、今お使いのプレーヤーを使い続けることもできます。同期した曲はどのアプリでも再生できます。",
    ),
    "INSTALLAZIONE…" to Tr("INSTALLING…", "インストール中…"),
    "AGGIORNA XAOS" to Tr("UPDATE XAOS", "Xaosをアップデート"),
    "INSTALLA XAOS" to Tr("INSTALL XAOS", "Xaosをインストール"),
    "Aggiornare Xaos sul telefono?" to Tr("Update Xaos on the phone?", "スマホのXaosをアップデートしますか？"),
    "Installare Xaos sul telefono?" to Tr("Install Xaos on the phone?", "スマホにXaosをインストールしますか？"),
    "Xaos su %s passa dalla %s alla %s%s. Playlist, preferiti e impostazioni restano come sono." to Tr(
        "Xaos on %s will go from %s to %s%s. Playlists, favorites and settings stay as they are.",
        "%1\$sのXaosを%2\$sから%3\$s%4\$sにアップデートします。プレイリスト、お気に入り、設定はそのまま残ります。",
    ),
    "versione installata" to Tr("the installed version", "現在のバージョン"),
    "Xaos Music Player per Android verrà installato su %s%s. Non tocca le altre app né la musica già presente." to Tr(
        "Xaos Music Player for Android will be installed on %s%s. It won't touch your other apps or the music already there.",
        "%1\$sにXaos Music Player for Android%2\$sをインストールします。ほかのアプリや保存済みの音楽には影響しません。",
    ),
    "AGGIORNA" to Tr("UPDATE", "アップデート"),
    "INSTALLA" to Tr("INSTALL", "インストール"),
    "l'app sul telefono è firmata con un'altra chiave. Per passare a questa va disinstallata prima, e si perdono playlist e preferiti del telefono." to Tr(
        "the app on the phone is signed with a different key. To switch to this one, it has to be uninstalled first, and the phone's playlists and favorites will be lost.",
        "スマホのアプリは別の鍵で署名されています。このバージョンに切り替えるには先にアンインストールする必要があり、スマホのプレイリストとお気に入りは失われます。",
    ),
    "sul telefono c'è già una versione più nuova." to Tr("the phone already has a newer version.", "スマホにはすでに新しいバージョンが入っています。"),
    "installazione non riuscita" to Tr("installation failed", "インストールに失敗しました"),

    // Preferiti, playlist, ascolti
    "PREFERITI · PLAYLIST · ASCOLTI" to Tr("FAVORITES · PLAYLISTS · PLAYS", "お気に入り · プレイリスト · 再生回数"),
    "Si scambiano nei due sensi a ogni collegamento." to Tr("Synced both ways every time you connect.", "接続するたびに双方向で同期されます。"),
    "Serve Xaos anche sul telefono: installalo qui sopra." to Tr("Xaos is needed on the phone too: install it above.", "スマホにもXaosが必要です。上からインストールしてください。"),
    "L'app sul telefono è troppo vecchia per scambiare questi dati: aggiornala qui sopra (restano tutti)." to Tr(
        "The app on the phone is too old to sync this data: update it above (nothing will be lost).",
        "スマホのアプリが古いため、このデータを同期できません。上からアップデートしてください（データはすべて残ります）。",
    ),
    "ALLINEATI ALLE %s · [%d] PLAYLIST IN COMUNE" to Tr("IN SYNC AT %s · [%d] SHARED PLAYLISTS", "%sに同期済み · 共通のプレイリスト [%d]"),
    "SINCRONIZZATI ALLE %s" to Tr("SYNCED AT %s", "%sに同期"),
    "+%d PREFERITI SUL PC" to Tr("+%d FAVORITES ON PC", "PCのお気に入り +%d"),
    "+%d SUL TELEFONO" to Tr("+%d ON PHONE", "スマホ +%d"),
    "%d TOLTI" to Tr("%d REMOVED", "%d件削除"),
    "%d ASCOLTI → PC" to Tr("%d PLAYS → PC", "再生%d回 → PC"),
    "%d → TELEFONO" to Tr("%d → PHONE", "%d → スマホ"),
    "TEMA DAL TELEFONO" to Tr("THEME FROM PHONE", "スマホからテーマ"),
    "TEMA AL TELEFONO" to Tr("THEME TO PHONE", "スマホへテーマ"),
    "PRESET AGGIORNATI" to Tr("PRESETS UPDATED", "プリセット更新"),
    "[%d] PLAYLIST" to Tr("[%d] PLAYLISTS", "プレイリスト [%d]"),
    "%d preferiti riguardano brani che ci sono solo da una parte: restano dove sono." to Tr(
        "%d favorites are songs that exist on only one side: they stay where they are.",
        "%d件のお気に入りは片方にしかない曲のため、そのまま残ります。",
    ),
    "NON RIUSCITO · %s" to Tr("FAILED · %s", "失敗 · %s"),
    "SINCRONIZZA ORA" to Tr("SYNC NOW", "今すぐ同期"),
    "LETTURA DEI DATI DEL TELEFONO" to Tr("READING PHONE DATA", "スマホのデータを読み込み中"),
    "CONFRONTO DEI BRANI" to Tr("MATCHING SONGS", "曲を照合中"),
    "SCRITTURA SUL TELEFONO" to Tr("WRITING TO PHONE", "スマホに書き込み中"),
    "sincronizzazione non riuscita" to Tr("sync failed", "同期に失敗しました"),
    "l'app sul telefono non ha risposto (%s)" to Tr("the app on the phone didn't respond (%s)", "スマホのアプリが応答しませんでした（%s）"),
    "impossibile leggere i dati del telefono" to Tr("couldn't read the phone's data", "スマホのデータを読み込めません"),
    "dati del telefono illeggibili" to Tr("the phone's data couldn't be parsed", "スマホのデータを解析できません"),
    "impossibile scrivere sul telefono" to Tr("couldn't write to the phone", "スマホに書き込めません"),
    "sul telefono qualcosa è cambiato proprio ora: riprova" to Tr(
        "something just changed on the phone: try again",
        "スマホ側でちょうど変更がありました。もう一度お試しください",
    ),
    "il telefono non ha accettato i dati (%s)" to Tr("the phone didn't accept the data (%s)", "スマホがデータを受け付けませんでした（%s）"),
    "adb non risponde" to Tr("adb isn't responding", "adbが応答しません"),

    // Tema
    "TEMA" to Tr("THEME", "テーマ"),
    "Ognuno tiene il suo: scegli tu quale copiare sull'altro. I preset invece si sincronizzano da soli." to Tr(
        "Each keeps its own: you choose which one to copy to the other. Presets sync on their own.",
        "PCとスマホはそれぞれ自分のテーマを使います。どちらをもう一方にコピーするか選んでください。プリセットは自動で同期されます。",
    ),
    "UGUALI" to Tr("SAME", "同じ"),
    "PC → TELEFONO" to Tr("PC → PHONE", "PC → スマホ"),
    "TELEFONO → PC" to Tr("PHONE → PC", "スマホ → PC"),
    "Da leggere" to Tr("Not read yet", "未取得"),
    "Personalizzato" to Tr("Custom", "カスタム"),
    "Standard" to Tr("Standard", "標準"),

    // Testi
    "CONTROLLO DEI TESTI SUL TELEFONO  %d/%d" to Tr("CHECKING LYRICS ON PHONE  %d/%d", "スマホの歌詞を確認中  %d/%d"),
    "AGGIORNAMENTO DEI TESTI" to Tr("UPDATING LYRICS", "歌詞を更新中"),
    "TESTI AGGIORNATI SUL TELEFONO · [%d]" to Tr("LYRICS UPDATED ON PHONE · [%d]", "スマホの歌詞を更新しました · [%d]"),
    "TESTI DA AGGIORNARE SUL TELEFONO" to Tr("LYRICS TO UPDATE ON PHONE", "スマホで更新が必要な歌詞"),
    "AGGIORNA TESTI [%d]" to Tr("UPDATE LYRICS [%d]", "歌詞を更新 [%d]"),
    "Sul PC questi brani hanno un testo che sul telefono manca o è diverso. L'aggiornamento scrive solo il testo nel file del telefono: audio, nome e cartella restano quelli." to Tr(
        "On the PC these songs have lyrics that are missing or different on the phone. Updating only writes the lyrics into the phone's file: audio, name and folder stay the same.",
        "これらの曲は、PCの歌詞がスマホにないか、内容が異なります。更新ではスマホのファイルに歌詞だけを書き込み、音声、ファイル名、フォルダは変わりません。",
    ),
    "MANCA SUL TELEFONO" to Tr("MISSING ON PHONE", "スマホになし"),
    "DIVERSO" to Tr("DIFFERENT", "相違あり"),
)
