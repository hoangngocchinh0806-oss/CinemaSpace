package com.example.cinemaspace

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews

/** Widget điều khiển nhạc ngoài màn hình chính. */
class MusicWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val sp = context.getSharedPreferences("widget", Context.MODE_PRIVATE)
        update(
            context, sp.getString("title", "HC Music") ?: "HC Music",
            sp.getString("artist", "Bấm phát để nghe nhạc") ?: "", sp.getBoolean("playing", false), null
        )
    }

    companion object {
        private fun pi(ctx: Context, code: Int, action: String) = PendingIntent.getForegroundService(
            ctx, code, Intent(ctx, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        fun update(ctx: Context, title: String, artist: String, playing: Boolean, art: Bitmap?) {
            ctx.getSharedPreferences("widget", Context.MODE_PRIVATE).edit()
                .putString("title", title).putString("artist", artist).putBoolean("playing", playing).apply()
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, MusicWidget::class.java))
            if (ids.isEmpty()) return
            val v = RemoteViews(ctx.packageName, R.layout.widget_music)
            v.setTextViewText(R.id.w_title, title)
            v.setTextViewText(R.id.w_artist, artist)
            if (art != null) v.setImageViewBitmap(R.id.w_cover, art)
            else v.setImageViewResource(R.id.w_cover, android.R.drawable.ic_media_play)
            v.setImageViewResource(R.id.w_play, if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            v.setOnClickPendingIntent(R.id.w_prev, pi(ctx, 21, "PREV"))
            v.setOnClickPendingIntent(R.id.w_play, pi(ctx, 22, "PLAY"))
            v.setOnClickPendingIntent(R.id.w_next, pi(ctx, 23, "NEXT"))
            val open = PendingIntent.getActivity(ctx, 24, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            v.setOnClickPendingIntent(R.id.w_cover, open)
            v.setOnClickPendingIntent(R.id.w_title, open)
            mgr.updateAppWidget(ids, v)
        }
    }
}
