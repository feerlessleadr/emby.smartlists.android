package com.feerlessleadr.embysmartlists

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** A home-screen widget: one tap refreshes all smart lists, and it says how that went. */
class RefreshWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { manager.updateAppWidget(it, views(context, null)) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_REFRESH) return
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, RefreshWidget::class.java))
        ids.forEach { manager.updateAppWidget(it, views(context, "Refreshing…")) }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val message = QuickRefresh.run(context)
                ids.forEach { manager.updateAppWidget(it, views(context, message)) }
            } finally {
                pending.finish()
            }
        }
    }

    private fun views(context: Context, status: String?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_refresh)
        if (status != null) views.setTextViewText(R.id.widget_status, status)
        val click = Intent(context, RefreshWidget::class.java).setAction(ACTION_REFRESH)
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getBroadcast(context, 0, click, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
        )
        return views
    }

    companion object {
        const val ACTION_REFRESH = "com.feerlessleadr.embysmartlists.action.REFRESH_ALL"
    }
}