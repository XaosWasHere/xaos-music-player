package com.example.xaosmusicplayer.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Il widget quadrato: copertina e, scorrendo in verticale, il testo in
 * sincrono. Il disegno e gli aggiornamenti li fa [WidgetHub].
 */
class XaosWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WidgetHub.refresh(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        // Ridimensionato: le pagine vanno ridisegnate alla nuova misura.
        WidgetHub.refresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TAP) {
            // Un tocco su una pagina qualsiasi: play o pausa. Se la musica non
            // è mai partita (servizio spento), apre l'app.
            if (!WidgetHub.togglePlayback()) {
                runCatching { WidgetHub.openApp(context).send() }
            }
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_TAP = "com.example.xaosmusicplayer.widget.TAP"
        const val EXTRA_PAGE = "page"
    }
}
