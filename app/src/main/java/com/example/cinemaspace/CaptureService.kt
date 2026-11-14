package com.example.cinemaspace

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.os.Process
import kotlin.math.abs
import kotlin.math.max

/** Bắt âm thanh của các app khác (Android 10+), xử lý không gian rồi phát lại. */
class CaptureService : Service() {
    @Volatile private var running = false
    private var thread: Thread? = null
    private var projection: MediaProjection? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("cap", "Xử lý âm thanh", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "cap")
            .setContentTitle("CinemaSpace đang xử lý âm thanh")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true).build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        val code = intent?.getIntExtra("code", 0) ?: 0
        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>("data")
        if (data == null) { stopSelf(); return START_NOT_STICKY }
        val mpm = getSystemService(MediaProjectionManager::class.java)
        projection = mpm.getMediaProjection(code, data).also {
            it.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopSelf() }
            }, null)
        }
        startLoop()
        return START_NOT_STICKY
    }

    private fun startLoop() {
        if (running) return
        running = true
        thread = Thread {
            var rec: AudioRecord? = null
            var track: AudioTrack? = null
            try {
                val sr = 48000
                val cfg = AudioPlaybackCaptureConfiguration.Builder(projection!!)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .excludeUid(Process.myUid())
                    .build()
                val inFmt = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_IN_STEREO).build()
                val minIn = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
                rec = AudioRecord.Builder().setAudioFormat(inFmt)
                    .setAudioPlaybackCaptureConfig(cfg).setBufferSizeInBytes(minIn * 4).build()
                val minOut = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
                track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(
                        if (SpatialHub.separateStream.value) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA
                    ).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                    .setBufferSizeInBytes(minOut * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY).build()
                val engine = SpatialEngine(sr, 2)
                val inb = ShortArray(960 * 2)
                val outb = ShortArray(960 * 2)
                rec.startRecording(); track.play()
                SpatialHub.running.value = true
                while (running) {
                    val read = rec.read(inb, 0, inb.size)
                    if (read <= 0) continue
                    val frames = read / 2
                    engine.process(inb, frames, outb, SpatialHub.state.value)
                    track.write(outb, 0, frames * 2)
                    var p0 = 0; var p1 = 0
                    for (i in 0 until frames) {
                        p0 = max(p0, abs(outb[2 * i].toInt()))
                        p1 = max(p1, abs(outb[2 * i + 1].toInt()))
                    }
                    SpatialHub.level[0] = max(p0 / 32768f, SpatialHub.level[0] * 0.9f)
                    SpatialHub.level[1] = max(p1 / 32768f, SpatialHub.level[1] * 0.9f)
                }
            } catch (e: Exception) {
                // thiếu quyền hoặc hệ thống từ chối bắt âm thanh
            } finally {
                try { rec?.stop() } catch (_: Exception) {}
                rec?.release()
                try { track?.stop() } catch (_: Exception) {}
                track?.release()
                SpatialHub.running.value = false
                running = false
                stopSelf()
            }
        }.also { it.start() }
    }

    override fun onDestroy() {
        running = false
        thread?.join(500)
        projection?.stop()
        SpatialHub.running.value = false
        SpatialHub.level[0] = 0f; SpatialHub.level[1] = 0f
        super.onDestroy()
    }
}
