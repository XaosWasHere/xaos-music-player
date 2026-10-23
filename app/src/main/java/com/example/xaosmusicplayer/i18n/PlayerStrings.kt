package com.example.xaosmusicplayer.i18n

/** Struttura dell'app, schermata in riproduzione, componenti comuni, servizi, widget e card del riepilogo. */
internal val PLAYER_STRINGS: Map<String, Tr> = mapOf(
    // MainActivity
    "I più ascoltati" to Tr("Most played", "よく聴く曲"),
    "Da riscoprire" to Tr("Rediscover", "再発見"),
    "È un brano del PC: si elimina da Xaos sul PC" to Tr("This song is on the PC: delete it from Xaos on the PC", "PCの曲です。PCのXaosから削除してください。"),
    "Nessun brano" to Tr("No songs", "曲がありません"),
    "ALBUM" to Tr("ALBUM", "アルバム"),
    "Scarica sul telefono i %d brani del PC" to Tr("Download the %d PC songs to the phone", "PCの%d曲をスマホにダウンロード"),
    "Modifica album" to Tr("Edit album", "アルバムを編集"),
    "Playlist vuota" to Tr("Empty playlist", "プレイリストは空です"),
    "PLAYLIST" to Tr("PLAYLIST", "プレイリスト"),
    "Modifica playlist" to Tr("Edit playlist", "プレイリストを編集"),
    "Elimina playlist" to Tr("Delete playlist", "プレイリストを削除"),
    "Nessun ascolto registrato" to Tr("No plays recorded", "再生履歴がありません"),
    "Libreria vuota" to Tr("Library is empty", "ライブラリは空です"),
    "Preferiti" to Tr("Favorites", "お気に入り"),
    "Nessun preferito" to Tr("No favorites", "お気に入りはありません"),
    "I brani col cuore, qui e sul PC." to Tr("Your hearted songs, here and on the PC.", "スマホとPCでハートを付けた曲です。"),
    "In coda" to Tr("Queue", "再生キュー"),
    "Coda vuota" to Tr("Queue is empty", "キューは空です"),

    // Schermata in riproduzione
    "TORNA AL PUNTO" to Tr("BACK TO SYNC", "再生位置に戻る"),
    "Chiudi" to Tr("Close", "閉じる"),
    "IN RIPRODUZIONE SU" to Tr("PLAYING ON", "再生中のデバイス"),
    "IN RIPRODUZIONE DA" to Tr("PLAYING FROM", "再生元"),
    "Altre opzioni" to Tr("More options", "その他のオプション"),
    "Riproduzione casuale" to Tr("Shuffle", "シャッフル"),
    "Brano precedente" to Tr("Previous song", "前の曲"),
    "Pausa" to Tr("Pause", "一時停止"),
    "Riproduci" to Tr("Play", "再生"),
    "Brano successivo" to Tr("Next song", "次の曲"),
    "Ripetizione" to Tr("Repeat", "リピート"),
    "Rimuovi dai preferiti" to Tr("Remove from favorites", "お気に入りから削除"),
    "Aggiungi ai preferiti" to Tr("Add to favorites", "お気に入りに追加"),
    "Dispositivi" to Tr("Devices", "デバイス"),
    "Coda di riproduzione" to Tr("Queue", "再生キュー"),

    // Barra in basso, componenti comuni, miniplayer
    "HOME" to Tr("HOME", "ホーム"),
    "LIBRERIA" to Tr("LIBRARY", "ライブラリ"),
    "CERCA" to Tr("SEARCH", "検索"),
    "Opzioni brano" to Tr("Song options", "曲のオプション"),
    "Solo sul PC: scarica sul telefono" to Tr("Only on the PC: download to the phone", "PCのみ：スマホにダウンロード"),
    "Brani solo sul PC" to Tr("Songs only on the PC", "PCにのみある曲"),
    "SUL PC · %s" to Tr("ON PC · %s", "PCで再生中 · %s"),

    // Servizi di riproduzione
    "Riproduzione" to Tr("Playback", "再生"),

    // Messaggi del ViewModel
    "Impossibile avviare yt-dlp: %s" to Tr("Couldn't start yt-dlp: %s", "yt-dlpを起動できません：%s"),
    "Ricerca fallita: %s" to Tr("Search failed: %s", "検索に失敗しました：%s"),
    "Creazione immagine..." to Tr("Creating image...", "画像を作成中..."),
    "Salvata in Immagini/Xaos" to Tr("Saved to Pictures/Xaos", "Pictures/Xaosに保存しました"),
    "Immagine non salvata: %s" to Tr("Image not saved: %s", "画像を保存できませんでした：%s"),
    "Aggiornamento del motore in corso..." to Tr("Updating the engine...", "エンジンを更新中..."),
    "Aggiornamento fallito: %s" to Tr("Update failed: %s", "更新に失敗しました：%s"),
    "errore sconosciuto" to Tr("unknown error", "不明なエラー"),

    // Widget
    "TESTO" to Tr("LYRICS", "歌詞"),
    "TOCCA PER APRIRE" to Tr("TAP TO OPEN", "タップして開く"),

    // Card del riepilogo annuale
    "IL MIO %d" to Tr("MY %d", "私の%d年"),
    "1 ASCOLTO" to Tr("1 PLAY", "1回再生"),
    "%d ASCOLTI" to Tr("%d PLAYS", "%d回再生"),
    "%d MINUTI · %s" to Tr("%d MINUTES · %s", "%d分 · %s"),
    "ARTISTA DELL'ANNO" to Tr("ARTIST OF THE YEAR", "年間トップアーティスト"),
    "%d MINUTI" to Tr("%d MINUTES", "%d分"),
    "BRANO PIÙ ASCOLTATO" to Tr("MOST PLAYED SONG", "最も再生した曲"),
    "ALTRI ARTISTI" to Tr("OTHER ARTISTS", "その他のアーティスト"),
    "BRANI DELL'ANNO" to Tr("SONGS OF THE YEAR", "年間トップソング"),
    // Errori del salvataggio, mostrati nel messaggio "Immagine non salvata"
)
