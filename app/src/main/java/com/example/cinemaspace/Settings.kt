package com.example.cinemaspace

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import org.json.JSONArray

object Settings {
    var minDurSec by mutableIntStateOf(15)
    var resume by mutableStateOf(true)
    var crossfadeSec by mutableIntStateOf(0)
    var hidden by mutableStateOf<Set<String>>(emptySet())
    private fun p(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(c: Context) {
        minDurSec = p(c).getInt("minDur", 15)
        resume = p(c).getBoolean("resume", true)
        crossfadeSec = p(c).getInt("cf", 0)
        hidden = p(c).getStringSet("hidden", emptySet()) ?: emptySet()
        Library.onlyMp3 = p(c).getBoolean("onlyMp3", true)
    }
    fun save(c: Context) { p(c).edit().putInt("minDur", minDurSec).apply() }
    fun setResume(c: Context, v: Boolean) { resume = v; p(c).edit().putBoolean("resume", v).apply() }
    fun setCrossfade(c: Context, v: Int) { crossfadeSec = v; p(c).edit().putInt("cf", v).apply() }
    fun setOnlyMp3(c: Context, v: Boolean) { Library.onlyMp3 = v; p(c).edit().putBoolean("onlyMp3", v).apply() }
    fun hide(c: Context, folder: String) { hidden = hidden + folder; p(c).edit().putStringSet("hidden", hidden.toSet()).apply() }
    fun unhide(c: Context, folder: String) { hidden = hidden - folder; p(c).edit().putStringSet("hidden", hidden.toSet()).apply() }
}

/** Nhớ hàng đợi, bài đang phát và vị trí để mở lại là nghe tiếp. */
object QueueStore {
    private fun p(c: Context) = c.getSharedPreferences("queue", Context.MODE_PRIVATE)

    fun save(c: Context, player: Player) {
        val n = player.mediaItemCount
        if (n == 0) return
        val ids = JSONArray()
        for (i in 0 until n) player.getMediaItemAt(i).mediaId.toLongOrNull()?.let { ids.put(it) }
        p(c).edit().putString("ids", ids.toString()).putInt("index", player.currentMediaItemIndex)
            .putLong("pos", player.currentPosition).putBoolean("shuffle", player.shuffleModeEnabled)
            .putInt("repeat", player.repeatMode).apply()
    }

    fun restore(c: Context, player: Player, byId: Map<Long, Track>) {
        if (!Settings.resume || player.mediaItemCount > 0) return
        try {
            val a = JSONArray(p(c).getString("ids", "[]") ?: "[]")
            val tracks = List(a.length()) { a.getLong(it) }.mapNotNull { byId[it] }
            if (tracks.isEmpty()) return
            val idx = p(c).getInt("index", 0).coerceIn(0, tracks.size - 1)
            player.setMediaItems(tracks.map { it.toItem() }, idx, p(c).getLong("pos", 0L))
            player.shuffleModeEnabled = p(c).getBoolean("shuffle", false)
            player.repeatMode = p(c).getInt("repeat", 0)
            player.prepare()
        } catch (e: Exception) { }
    }
}

@Composable
fun SettingsScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            TextButton(onClick = onClose) { Text("‹ Quay lại") }
            ScreenTitle("Cài đặt", "Tùy chỉnh HC Music")
            SectionCard {
                Text("Giao diện", style = MaterialTheme.typography.titleSmall)
                SwitchRow("Chế độ tối", AppPrefs.dark) { AppPrefs.toggle(ctx) }
            }
            SectionCard {
                Text("Thư viện nhạc", style = MaterialTheme.typography.titleSmall)
                SwitchRow("Chỉ nhận file MP3", Library.onlyMp3) { v -> Settings.setOnlyMp3(ctx, v); Library.rescan(ctx) }
                Spacer(Modifier.height(8.dp))
                Text("Bỏ qua file ngắn hơn: ${Settings.minDurSec} giây")
                Slider(
                    value = Settings.minDurSec.toFloat(), onValueChange = { Settings.minDurSec = it.toInt() },
                    onValueChangeFinished = { Settings.save(ctx); Library.rescan(ctx) }, valueRange = 0f..120f
                )
                Text("File video (MP4...) luôn bị bỏ qua.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("Thư mục đã ẩn (${Settings.hidden.size})", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                if (Settings.hidden.isEmpty()) {
                    Text("Chưa ẩn thư mục nào. Mở tab Thư viện > Thư mục > chọn thư mục > Ẩn.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Settings.hidden.toList().sorted().forEach { f ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("📁 $f", Modifier.weight(1f), fontSize = 13.sp)
                        TextButton(onClick = { Settings.unhide(ctx, f); Library.rescan(ctx) }) { Text("Hiện lại") }
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { Library.rescan(ctx) }) { Text("Quét lại thư viện") }
            }
            SectionCard {
                Text("Phát nhạc", style = MaterialTheme.typography.titleSmall)
                SwitchRow("Nhớ hàng đợi và vị trí khi mở lại app", Settings.resume) { v -> Settings.setResume(ctx, v) }
                Spacer(Modifier.height(8.dp))
                Text(if (Settings.crossfadeSec == 0) "Làm mờ giữa các bài: tắt" else "Làm mờ giữa các bài: ${Settings.crossfadeSec} giây")
                Slider(
                    value = Settings.crossfadeSec.toFloat(), onValueChange = { Settings.crossfadeSec = it.toInt() },
                    onValueChangeFinished = { Settings.setCrossfade(ctx, Settings.crossfadeSec) }, valueRange = 0f..10f, steps = 9
                )
                Text("Cuối bài nhỏ dần, đầu bài kế tiếp to dần.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SectionCard {
                Text("Dữ liệu", style = MaterialTheme.typography.titleSmall)
                OutlinedButton(onClick = { StatsStore.reset(ctx) }) { Text("Xóa thống kê lượt nghe") }
            }
            Spacer(Modifier.height(16.dp))
            Text("HC Music • âm thanh không gian", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
