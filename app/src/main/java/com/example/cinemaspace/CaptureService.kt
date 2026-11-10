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

/** Thử nghiệm: bắt âm thanh app khác (Android 10+), xử lý không gian rồi phát lại. */
class CaptureService : Service() {
    @Volatile private var running = false
    private var thread: Thread? = null
    private var projection: MediaProjection? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("cap", "Capture", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "cap")
            .setContentTitle("CinemaSpace đang xử lý âm thanh")
            .setSmallIcon(android.R.drawable.ic_media_play).build()
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
            val rec = AudioRecord.Builder().setAudioFormat(inFmt)
                .setAudioPlaybackCaptureConfig(cfg).setBufferSizeInBytes(minIn * 4).build()
            val minOut = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                .setBufferSizeInBytes(minOut * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY).build()
            val engine = SpatialEngine(sr, 2)
            val inb = ShortArray(960 * 2)
            val outb = ShortArray(960 * 2)
            try {
                rec.startRecording(); track.play()
                while (running) {
                    val read = rec.read(inb, 0, inb.size)
                    if (read <= 0) continue
                    val frames = read / 2
                    engine.process(inb, frames, outb, SpatialHub.state.value)
                    track.write(outb, 0, frames * 2)
                }
            } finally {
                rec.stop(); rec.release(); track.stop(); track.release()
            }
        }.also { it.start() }
    }

    override fun onDestroy() {
        running = false
        thread?.join(500)
        projection?.stop()
        super.onDestroy()
    }
}
