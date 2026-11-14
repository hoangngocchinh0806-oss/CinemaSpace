@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.cinemaspace

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.IBinder
import android.util.Size
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Giữ nhạc phát nền, hiện thông báo có ảnh bìa và nút Trước / Phát / Sau / Dừng, hỗ trợ nút tai nghe. */
class PlaybackService : Service() {
    private var session: MediaSession? = null
    private var artKey: Uri? = null
    private var art: Bitmap? = null
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { refresh() }
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val p = PlayerHub.get(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("play", "Phát nhạc", NotificationManager.IMPORTANCE_LOW))
        startForeground(2, build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        when (intent?.action) {
            "STOP" -> { p.pause(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY }
            "PREV" -> p.seekToPrevious()
            "NEXT" -> p.seekToNext()
            "PLAY" -> if (p.isPlaying) p.pause() else p.play()
        }
        if (p.mediaItemCount == 0) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY }
        if (session == null) {
            session = MediaSession.Builder(this, p).build()
            p.addListener(listener)
        }
        refresh()
        return START_NOT_STICKY
    }

    private fun act(icon: Int, label: String, action: String, code: Int): Notification.Action {
        val pi = PendingIntent.getService(
            this, code, Intent(this, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Action.Builder(Icon.createWithResource(this, icon), label, pi).build()
    }

    private fun build(): Notification {
        val p = PlayerHub.get(this)
        val m = p.currentMediaItem
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val b = Notification.Builder(this, "play")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(m?.mediaMetadata?.title?.toString() ?: "HC Music")
            .setContentText(m?.mediaMetadata?.artist?.toString() ?: "")
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(p.isPlaying)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(act(android.R.drawable.ic_media_previous, "Trước", "PREV", 10))
            .addAction(act(if (p.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, "Phát/Dừng", "PLAY", 11))
            .addAction(act(android.R.drawable.ic_media_next, "Sau", "NEXT", 12))
            .addAction(act(android.R.drawable.ic_menu_close_clear_cancel, "Dừng", "STOP", 13))
            .setStyle(Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2))
        art?.let { b.setLargeIcon(it) }
        return b.build()
    }

    private fun post() {
        getSystemService(NotificationManager::class.java).notify(2, build())
        val p = PlayerHub.get(this)
        val m = p.currentMediaItem
        MusicWidget.update(this, m?.mediaMetadata?.title?.toString() ?: "HC Music", m?.mediaMetadata?.artist?.toString() ?: "", p.isPlaying, art)
    }

    private fun refresh() {
        val uri = PlayerHub.get(this).currentMediaItem?.localConfiguration?.uri
        if (uri != artKey) {
            artKey = uri
            art = null
            if (uri != null) {
                Library.scope.launch(Dispatchers.IO) {
                    val bmp = try { contentResolver.loadThumbnail(uri, Size(256, 256), null) } catch (e: Exception) { null }
                    withContext(Dispatchers.Main) { if (artKey == uri) { art = bmp; post() } }
                }
            }
        }
        post()
    }

    override fun onDestroy() {
        val m = PlayerHub.get(this).currentMediaItem
        MusicWidget.update(this, m?.mediaMetadata?.title?.toString() ?: "HC Music", m?.mediaMetadata?.artist?.toString() ?: "", false, art)
        PlayerHub.get(this).removeListener(listener)
        session?.release()
        session = null
        super.onDestroy()
    }

    companion object {
        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, PlaybackService::class.java))
        }
    }
}
