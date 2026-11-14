package com.example.cinemaspace

import android.Manifest
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {

    private val projLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val d = r.data
        if (r.resultCode == RESULT_OK && d != null) {
            startForegroundService(
                Intent(this, CaptureService::class.java).putExtra("code", r.resultCode).putExtra("data", d)
            )
        }
    }

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res[Manifest.permission.RECORD_AUDIO] == true) {
            projLauncher.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
        } else {
            Toast.makeText(this, "Cần cấp quyền ghi âm để lấy âm thanh từ máy", Toast.LENGTH_LONG).show()
        }
    }

    private val notifLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val uiHandler = Handler(Looper.getMainLooper())
    private val rescanTask = Runnable { if (Library.loaded) Library.rescan(this) }
    private val mediaObserver = object : ContentObserver(uiHandler) {
        override fun onChange(selfChange: Boolean) {
            uiHandler.removeCallbacks(rescanTask)
            uiHandler.postDelayed(rescanTask, 3000)
        }
    }

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(mediaObserver)
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SpatialHub.separateStream.value = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("sep", false)
        contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mediaObserver)
        Settings.load(this); AppPrefs.load(this); Likes.load(this); Follows.load(this); StatsStore.load(this)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            App { on ->
                if (on) permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
                else startService(Intent(this, CaptureService::class.java).setAction("STOP"))
            }
        }
    }
}
