package com.example.xaosmusicplayer.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.xaosmusicplayer.R

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

/** Le pagine della pila, per il launcher. */
class XaosWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = object : RemoteViewsFactory {
        private var pages = WidgetHub.pages

        override fun onCreate() = Unit
        override fun onDataSetChanged() {
            pages = WidgetHub.pages
        }
        override fun onDestroy() = Unit
        override fun getCount(): Int = pages.size
        override fun getViewAt(position: Int): RemoteViews =
            RemoteViews(packageName, R.layout.widget_xaos_page).apply {
                pages.getOrNull(position)?.let { setImageViewBitmap(R.id.widget_page_image, it) }
                setOnClickFillInIntent(R.id.widget_page_image, Intent().putExtra(XaosWidget.EXTRA_PAGE, position))
            }
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount(): Int = 1
        override fun getItemId(position: Int): Long = position.toLong()
        override fun hasStableIds(): Boolean = true
    }
}
