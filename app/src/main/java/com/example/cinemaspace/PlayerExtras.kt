package com.example.cinemaspace

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object SleepTimer {
    var endAt by mutableLongStateOf(0L)
    var afterTrack by mutableStateOf(false)
    private val handler = Handler(Looper.getMainLooper())
    private var task: Runnable? = null

    fun cancel(p: ExoPlayer) {
        task?.let { handler.removeCallbacks(it) }
        task = null
        endAt = 0L
        if (afterTrack) { afterTrack = false; p.pauseAtEndOfMediaItems = false }
    }

    fun startMinutes(p: ExoPlayer, minutes: Int) {
        cancel(p)
        endAt = SystemClock.elapsedRealtime() + minutes * 60_000L
        val r = Runnable { fadeOutAndPause(p) }
        task = r
        handler.postDelayed(r, minutes * 60_000L)
    }

    fun afterCurrent(p: ExoPlayer) {
        cancel(p)
        afterTrack = true
        p.pauseAtEndOfMediaItems = true
    }

    private fun fadeOutAndPause(p: ExoPlayer) {
        var step = 0
        val fader = object : Runnable {
            override fun run() {
                step++
                PlayerHub.sleepFactor = 1f - step / 10f
                PlayerHub.applyVolume()
                if (step < 10) handler.postDelayed(this, 300)
                else { p.pause(); PlayerHub.sleepFactor = 1f; PlayerHub.applyVolume(); endAt = 0L; task = null }
            }
        }
        handler.post(fader)
    }
}

data class LyricLine(val t: Long, val text: String)

object Lyrics {
    private fun file(c: Context, id: String) = File(c.filesDir, "lyrics_$id.txt")
    fun load(c: Context, id: String): String? = if (id.isBlank()) null else file(c, id).takeIf { it.exists() }?.readText()
    fun save(c: Context, id: String, text: String) { if (id.isNotBlank()) file(c, id).writeText(text) }
    fun delete(c: Context, id: String) { if (id.isNotBlank()) file(c, id).delete() }

    private val re = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]")

    /** Hỗ trợ file .lrc có mốc thời gian, hoặc văn bản thường. */
    fun parse(raw: String): List<LyricLine> {
        val out = ArrayList<LyricLine>()
        for (line in raw.lines()) {
            val ms = re.findAll(line).toList()
            val text = re.replace(line, "").trim()
            if (ms.isEmpty()) {
                if (line.isNotBlank() && !line.trim().startsWith("[")) out.add(LyricLine(-1, line.trim()))
                continue
            }
            ms.forEach { m ->
                val f = m.groupValues[3]
                val frac = when (f.length) { 0 -> 0L; 1 -> f.toLong() * 100; 2 -> f.toLong() * 10; else -> f.toLong() }
                out.add(LyricLine((m.groupValues[1].toLong() * 60 + m.groupValues[2].toLong()) * 1000 + frac, text))
            }
        }
        return if (out.any { it.t >= 0 }) out.filter { it.t >= 0 }.sortedBy { it.t } else out
    }
}

@Composable
fun PlayerExtras(player: ExoPlayer, np: NowPlaying) {
    val ctx = LocalContext.current
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) { while (true) { now = SystemClock.elapsedRealtime(); delay(1000) } }

    // ----- Tốc độ phát -----
    Text("Tốc độ phát", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
            Pill("${s}x", kotlin.math.abs(np.speed - s) < 0.01f) { player.playbackParameters = PlaybackParameters(s) }
        }
    }

    // ----- Hẹn giờ tắt -----
    Text("Hẹn giờ tắt nhạc", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(15, 30, 45, 60).forEach { m -> Pill("$m phút", false) { SleepTimer.startMinutes(player, m) } }
        Pill("Hết bài này", SleepTimer.afterTrack) { SleepTimer.afterCurrent(player) }
        if (SleepTimer.endAt > 0L || SleepTimer.afterTrack) Pill("Tắt hẹn giờ", false) { SleepTimer.cancel(player) }
    }
    if (SleepTimer.endAt > 0L) {
        val left = ((SleepTimer.endAt - now) / 1000).coerceAtLeast(0)
        Text("Nhạc sẽ tắt sau ${left / 60}:${String.format("%02d", left % 60)}", color = Accent, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    } else if (SleepTimer.afterTrack) {
        Text("Nhạc sẽ dừng khi hết bài này", color = Accent, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    }

    // ----- Lời bài hát -----
    var text by remember(np.id) { mutableStateOf(Lyrics.load(ctx, np.id)) }
    var paste by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }
    var expand by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) {
            val s = try { ctx.contentResolver.openInputStream(u)?.bufferedReader()?.use { it.readText() } } catch (e: Exception) { null }
            if (!s.isNullOrBlank()) { Lyrics.save(ctx, np.id, s); text = s }
        }
    }
    Text("Lời bài hát", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
    val lines = remember(text) { text?.let { Lyrics.parse(it) } ?: emptyList() }
    val synced = lines.any { it.t >= 0 }
    if (lines.isEmpty()) {
        Text("Chưa có lời. Nhập file .lrc / .txt hoặc dán lời bài hát.", color = dim, fontSize = 13.sp)
    } else if (synced) {
        val cur = lines.indexOfLast { it.t <= np.pos }
        val from = if (expand) 0 else (cur - 2).coerceAtLeast(0)
        val to = if (expand) lines.size else (cur + 3).coerceAtLeast(5).coerceAtMost(lines.size)
        for (i in from until to) {
            Text(
                lines[i].text.ifBlank { "♪" },
                color = if (i == cur) Accent else dim,
                fontSize = if (i == cur) 20.sp else 15.sp,
                fontWeight = if (i == cur) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.fillMaxWidth().clickable { player.seekTo(lines[i].t) }.padding(vertical = 4.dp)
            )
        }
    } else {
        (if (expand) lines else lines.take(8)).forEach { Text(it.text, fontSize = 15.sp, modifier = Modifier.padding(vertical = 2.dp)) }
    }
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        TextButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Nhập file") }
        TextButton(onClick = { pasteText = text ?: ""; paste = true }) { Text("Dán lời") }
        TextButton(
            enabled = !searching && np.hasItem,
            onClick = {
                val id = np.id
                searching = true
                scope.launch {
                    val r = fetchLyricsOnline(np.title, if (np.artist.startsWith("Không rõ")) "" else np.artist, np.album, np.dur / 1000)
                    searching = false
                    if (r != null) { Lyrics.save(ctx, id, r); if (np.id == id) text = r }
                    else Toast.makeText(ctx, "Không tìm thấy lời cho bài này", Toast.LENGTH_SHORT).show()
                }
            }
        ) { Text(if (searching) "Đang tìm..." else "Tìm trên mạng") }
        if (lines.size > 6) TextButton(onClick = { expand = !expand }) { Text(if (expand) "Thu gọn" else "Xem toàn bộ") }
        if (lines.isNotEmpty()) TextButton(onClick = { Lyrics.delete(ctx, np.id); text = null }) { Text("Xóa lời") }
    }
    if (paste) {
        AlertDialog(
            onDismissRequest = { paste = false },
            title = { Text("Dán lời bài hát") },
            text = {
                OutlinedTextField(
                    value = pasteText, onValueChange = { pasteText = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp),
                    placeholder = { Text("Dán lời (có thể kèm mốc [mm:ss.xx])") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pasteText.isNotBlank()) { Lyrics.save(ctx, np.id, pasteText); text = pasteText }
                    paste = false
                }) { Text("Lưu") }
            },
            dismissButton = { TextButton(onClick = { paste = false }) { Text("Hủy") } }
        )
    }
}

/** Tìm lời bài hát trên LRCLIB (miễn phí, không cần tài khoản). Chỉ gửi tên bài, nghệ sĩ, album khi bạn bấm nút. */
suspend fun fetchLyricsOnline(title: String, artist: String, album: String, durSec: Long): String? = withContext(Dispatchers.IO) {
    fun get(url: String): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "HCMusic/1.0")
        if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null
    } catch (e: Exception) { null }
    fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    fun pick(o: JSONObject): String? {
        val synced = o.optString("syncedLyrics")
        val plain = o.optString("plainLyrics")
        return when {
            synced.isNotBlank() && synced != "null" -> synced
            plain.isNotBlank() && plain != "null" -> plain
            else -> null
        }
    }
    var res: String? = null
    if (artist.isNotBlank() && album.isNotBlank() && durSec > 0) {
        val d = get("https://lrclib.net/api/get?artist_name=${enc(artist)}&track_name=${enc(title)}&album_name=${enc(album)}&duration=$durSec")
        res = d?.let { try { pick(JSONObject(it)) } catch (e: Exception) { null } }
    }
    if (res == null) {
        val q = "https://lrclib.net/api/search?track_name=${enc(title)}" + if (artist.isNotBlank()) "&artist_name=${enc(artist)}" else ""
        res = get(q)?.let {
            try {
                val a = JSONArray(it)
                (0 until a.length()).firstNotNullOfOrNull { i -> pick(a.getJSONObject(i)) }
            } catch (e: Exception) { null }
        }
    }
    res
}
