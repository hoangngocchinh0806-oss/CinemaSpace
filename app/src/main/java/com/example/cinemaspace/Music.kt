@file:OptIn(androidx.media3.common.util.UnstableApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.cinemaspace

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

// ======================= Dữ liệu =======================

data class Track(
    val id: Long, val title: String, val artist: String, val album: String,
    val durationMs: Long, val uri: Uri, val folder: String,
    val genre: String = "", val added: Long = 0L,
    val albumId: Long = 0L, val trackNo: Int = 0
)

object Library {
    var tracks by mutableStateOf<List<Track>>(emptyList())
    var loaded by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var onlyMp3 by mutableStateOf(true)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun rescan(ctx: Context) {
        if (loading) return
        val app = ctx.applicationContext
        scope.launch {
            loading = true
            try {
                tracks = withContext(Dispatchers.IO) { scanMusic(app, onlyMp3).filter { it.folder !in Settings.hidden } }
                loaded = true
            } finally {
                loading = false
            }
        }
    }
}

object PlayerHub {
    private var player: ExoPlayer? = null
    var userVolume = 1f
    var fadeFactor = 1f
    var sleepFactor = 1f
    fun applyVolume() { player?.volume = userVolume * fadeFactor * sleepFactor }
    fun get(ctx: Context): ExoPlayer {
        player?.let { return it }
        val app = ctx.applicationContext
        val p = ExoPlayer.Builder(app, spatialRenderers(app))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        val handler = Handler(Looper.getMainLooper())
        val saver = object : Runnable {
            override fun run() { QueueStore.save(app, p); handler.postDelayed(this, 5000) }
        }
        val fadeTick = object : Runnable {
            override fun run() {
                val cf = Settings.crossfadeSec * 1000L
                val dur = p.duration
                if (cf > 0 && dur > 0) {
                    val c = minOf(cf, dur / 3)
                    val pos = p.currentPosition
                    val inF = (pos.toFloat() / c).coerceIn(0f, 1f)
                    val outF = ((dur - pos).toFloat() / c).coerceIn(0f, 1f)
                    val hasNext = p.hasNextMediaItem() || p.repeatMode != Player.REPEAT_MODE_OFF
                    PlayerHub.fadeFactor = if (hasNext) minOf(inF, outF) else inF
                } else {
                    PlayerHub.fadeFactor = 1f
                }
                PlayerHub.applyVolume()
                handler.postDelayed(this, 100)
            }
        }
        p.addListener(object : Player.Listener {
            private var startedAt = 0L
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem != null && p.playWhenReady && reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                    mediaItem.mediaId.toLongOrNull()?.let { StatsStore.addPlay(app, it) }
                }
                if (SleepTimer.afterTrack) { SleepTimer.afterTrack = false; p.pauseAtEndOfMediaItems = false }
                QueueStore.save(app, p)
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val now = SystemClock.elapsedRealtime()
                handler.removeCallbacks(saver)
                handler.removeCallbacks(fadeTick)
                if (isPlaying) {
                    startedAt = now
                    handler.postDelayed(saver, 5000)
                    handler.post(fadeTick)
                } else {
                    PlayerHub.fadeFactor = 1f
                    PlayerHub.applyVolume()
                    if (startedAt > 0L) { StatsStore.addTime(app, now - startedAt); startedAt = 0L }
                    QueueStore.save(app, p)
                }
            }
        })
        player = p
        return p
    }
}

object PlaylistStore {
    private fun prefs(c: Context) = c.getSharedPreferences("playlists", Context.MODE_PRIVATE)

    fun load(c: Context): Map<String, List<Long>> {
        val out = LinkedHashMap<String, List<Long>>()
        try {
            val o = JSONObject(prefs(c).getString("data", "{}") ?: "{}")
            o.keys().forEach { k ->
                val a = o.getJSONArray(k)
                out[k] = List(a.length()) { a.getLong(it) }
            }
        } catch (e: Exception) { }
        return out
    }

    fun save(c: Context, m: Map<String, List<Long>>) {
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k, JSONArray(v)) }
        prefs(c).edit().putString("data", o.toString()).apply()
    }
}

private fun clean(s: String?, d: String) = if (s.isNullOrBlank() || s == "<unknown>") d else s

/** Quét nhạc trong máy qua MediaStore. Chỉ lấy file âm thanh, bỏ qua mp4 và các file video. */
fun scanMusic(ctx: Context, onlyMp3: Boolean): List<Track> {
    val out = ArrayList<Track>()
    val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val a = MediaStore.Audio.Media.TITLE
    val proj = arrayOf(
        MediaStore.Audio.Media._ID, a, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.MIME_TYPE,
        MediaStore.Audio.Media.RELATIVE_PATH, MediaStore.Audio.Media.DATE_ADDED,
        MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.TRACK
    ) + (if (Build.VERSION.SDK_INT >= 30) arrayOf(MediaStore.Audio.Media.GENRE) else emptyArray<String>())
    val sel = "${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND ${MediaStore.Audio.Media.IS_NOTIFICATION} = 0 AND " +
        "${MediaStore.Audio.Media.IS_ALARM} = 0 AND ${MediaStore.Audio.Media.DURATION} >= ${Settings.minDurSec * 1000L}"
    ctx.contentResolver.query(base, proj, sel, null, "$a COLLATE NOCASE ASC")?.use { c ->
        val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val iTitle = c.getColumnIndexOrThrow(a)
        val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val iName = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
        val iMime = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
        val iPath = c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
        val iAdded = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
        val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val iTrack = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
        val iGenre = if (Build.VERSION.SDK_INT >= 30) c.getColumnIndex(MediaStore.Audio.Media.GENRE) else -1
        while (c.moveToNext()) {
            val name = (c.getString(iName) ?: "").lowercase()
            val mime = (c.getString(iMime) ?: "").lowercase()
            val videoLike = mime.startsWith("video/") || name.endsWith(".mp4") || name.endsWith(".m4v") ||
                name.endsWith(".mkv") || name.endsWith(".mov") || name.endsWith(".avi") || name.endsWith(".3gp")
            if (videoLike) continue
            if (onlyMp3 && !(mime == "audio/mpeg" || name.endsWith(".mp3"))) continue
            val id = c.getLong(iId)
            out.add(
                Track(
                    id, clean(c.getString(iTitle), name), clean(c.getString(iArtist), "Không rõ nghệ sĩ"),
                    clean(c.getString(iAlbum), ""), c.getLong(iDur), ContentUris.withAppendedId(base, id),
                    clean(c.getString(iPath), "Khác"),
                    if (iGenre >= 0) clean(c.getString(iGenre), "") else "", c.getLong(iAdded),
                    c.getLong(iAlbumId), c.getInt(iTrack) % 1000
                )
            )
        }
    }
    return out
}

fun Track.toItem(): MediaItem = MediaItem.Builder().setUri(uri).setMediaId(id.toString())
    .setMediaMetadata(
        MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album)
            .setArtworkUri(ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)).build()
    )
    .build()

fun playList(ctx: Context, tracks: List<Track>, start: Int) {
    if (tracks.isEmpty()) return
    val p = PlayerHub.get(ctx)
    p.setMediaItems(tracks.map { it.toItem() }, start.coerceIn(0, tracks.size - 1), 0L)
    p.prepare()
    p.play()
    PlaybackService.start(ctx)
}

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return String.format("%d:%02d", s / 60, s % 60)
}

// ======================= Trạng thái đang phát =======================

class NowPlaying {
    var hasItem by mutableStateOf(false)
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var id by mutableStateOf("")
    var uri by mutableStateOf<Uri?>(null)
    var playing by mutableStateOf(false)
    var shuffle by mutableStateOf(false)
    var repeat by mutableIntStateOf(0)
    var pos by mutableLongStateOf(0L)
    var dur by mutableLongStateOf(0L)
    var queue by mutableIntStateOf(0)
    var speed by mutableFloatStateOf(1f)
    var album by mutableStateOf("")

    fun sync(p: Player) {
        val m = p.currentMediaItem
        hasItem = m != null
        title = m?.mediaMetadata?.title?.toString() ?: ""
        artist = m?.mediaMetadata?.artist?.toString() ?: ""
        id = m?.mediaId ?: ""
        uri = m?.localConfiguration?.uri
        playing = p.isPlaying
        shuffle = p.shuffleModeEnabled
        repeat = p.repeatMode
        pos = p.currentPosition
        dur = p.duration.coerceAtLeast(0L)
        queue = p.mediaItemCount
        speed = p.playbackParameters.speed
        album = m?.mediaMetadata?.albumTitle?.toString() ?: ""
    }
}

@Composable
fun rememberNowPlaying(player: Player): NowPlaying {
    val np = remember { NowPlaying() }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) { np.sync(p) }
        }
        np.sync(player)
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    LaunchedEffect(player) {
        while (true) {
            np.pos = player.currentPosition
            np.dur = player.duration.coerceAtLeast(0L)
            delay(400)
        }
    }
    return np
}

// ======================= Thành phần giao diện =======================

@Composable
fun CoverBox(uri: Uri?, modifier: Modifier, px: Int = 256, radius: Dp = 14.dp) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, uri, px) {
        value = if (uri == null) null else withContext(Dispatchers.IO) {
            try {
                ctx.contentResolver.loadThumbnail(uri, android.util.Size(px, px), null).asImageBitmap()
            } catch (e: Exception) { null }
        }
    }
    BoxWithConstraints(
        modifier.aspectRatio(1f).clip(RoundedCornerShape(radius))
            .background(Brush.linearGradient(listOf(Accent2, Accent))),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text("🎵", fontSize = (maxWidth.value * 0.4f).sp, color = Color.White)
    }
}

@Composable
fun Cover(uri: Uri?, size: Dp) = CoverBox(uri, Modifier.size(size), 160, 10.dp)

@Composable
fun TrackRow(t: Track, playing: Boolean, moreLabel: String = "⋮", onClick: () -> Unit, onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Cover(t.uri, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                t.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (playing) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                "${t.artist} • ${fmt(t.durationMs)}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val ctxR = LocalContext.current
        val likedR = t.id in Likes.ids
        IconButton(onClick = { Likes.toggle(ctxR, t.id) }) {
            Text(if (likedR) "♥" else "♡", fontSize = 20.sp, color = if (likedR) Accent else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { if (moreLabel == "⋮") TrackMenu.open(t) else onMore() }) { Text(moreLabel, fontSize = 20.sp) }
    }
}

@Composable
private fun PlCard(title: String, sub: String, onOpen: () -> Unit, onPlay: () -> Unit) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onPlay) { Text("▶", fontSize = 20.sp) }
        }
    }
}

@Composable
fun AddToPlaylistDialog(count: Int, names: List<String>, onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Thêm $count bài vào playlist") },
        text = {
            Column {
                names.forEach { n -> TextButton(onClick = { onAdd(n) }) { Text(n) } }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Playlist mới") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onAdd(name.trim()) }) { Text("Tạo và thêm") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Đóng") } }
    )
}

val MUSIC_QUICK = listOf(
    "Hi-Fi stereo", "Phòng hòa nhạc", "Club / EDM", "Bao quanh 360°", "Quán cà phê / Acoustic", "Jazz club"
)

@Composable
fun MiniPlayer(onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val player = remember { PlayerHub.get(ctx) }
    val np = rememberNowPlaying(player)
    if (!np.hasItem) return
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).clickable { onOpen() }) {
        HorizontalDivider()
        LinearProgressIndicator(
            progress = { if (np.dur > 0) (np.pos.toFloat() / np.dur).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(2.dp), color = Accent, trackColor = MaterialTheme.colorScheme.outline
        )
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(np.uri, 44.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(np.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(np.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { player.seekToPrevious() }) { Text("⏮", fontSize = 20.sp) }
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(Accent, Accent2)))
                    .clickable { if (np.playing) player.pause() else player.play() },
                contentAlignment = Alignment.Center
            ) { Text(if (np.playing) "⏸" else "▶", fontSize = 18.sp, color = Color.White) }
            IconButton(onClick = { player.seekToNext() }) { Text("⏭", fontSize = 20.sp) }
        }
    }
}

class LibraryAccess(val granted: Boolean, val request: () -> Unit, val scan: () -> Unit)

@Composable
fun rememberLibraryAccess(): LibraryAccess {
    val ctx = LocalContext.current
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED) }
    fun scan() { Library.rescan(ctx) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (ok) scan()
    }
    LaunchedEffect(granted) { if (granted && !Library.loaded && !Library.loading) scan() }
    return LibraryAccess(granted, { launcher.launch(perm) }, { scan() })
}

// ======================= Màn hình Nhạc =======================

@Composable
fun MusicScreen(initialSub: Int = 0, onArtist: (String) -> Unit = {}, onAlbum: (Long) -> Unit = {}) {
    val ctx = LocalContext.current
    val player = remember { PlayerHub.get(ctx) }
    val np = rememberNowPlaying(player)
    val access = rememberLibraryAccess()
    fun scan() { access.scan() }
    var sub by remember(initialSub) { mutableIntStateOf(initialSub) }
    var query by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf<String?>(null) }
    var openPl by remember { mutableStateOf<String?>(null) }
    var newPl by remember { mutableStateOf("") }
    var sort by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<List<Track>?>(null) }
    var playlists by remember { mutableStateOf(PlaylistStore.load(ctx)) }
    fun savePl(m: Map<String, List<Long>>) { playlists = m; PlaylistStore.save(ctx, m) }
    val byId = remember(Library.tracks) { Library.tracks.associateBy { it.id } }

    Column(Modifier.fillMaxSize()) {
        Text("Thư viện", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Nhạc trong máy, playlist và nghệ sĩ bạn theo dõi", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        if (!access.granted) {
            SectionCard {
                Text("Cho phép đọc nhạc", style = MaterialTheme.typography.titleSmall)
                Text("App sẽ tự quét các file nhạc MP3 trong máy và gom vào thư viện. File video như MP4 sẽ bị bỏ qua.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                AccentButton("Cấp quyền và quét nhạc") { access.request() }
            }
            return@Column
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val tabNames = listOf("Bài hát", "Album", "Nghệ sĩ", "Thư mục", "Playlist", "Yêu thích", "Theo dõi")
            val tabIds = listOf(0, 5, 6, 1, 2, 3, 4)
            tabNames.forEachIndexed { i, n ->
                Pill(n, sub == tabIds[i]) { sub = tabIds[i]; folder = null; openPl = null }
            }
        }
        Spacer(Modifier.height(10.dp))
        when (sub) {
            0 -> {
                val base = if (query.isBlank()) Library.tracks
                else Library.tracks.filter { it.title.contains(query, true) || it.artist.contains(query, true) }
                val list = when (sort) {
                    1 -> base.sortedByDescending { it.added }
                    2 -> base.sortedByDescending { StatsStore.plays[it.id] ?: 0 }
                    3 -> base.sortedByDescending { it.durationMs }
                    else -> base
                }
                OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Tìm bài hát") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("A-Z", "Mới thêm", "Nghe nhiều", "Thời lượng").forEachIndexed { i, n -> Pill(n, sort == i) { sort = i } }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (Library.loading) "Đang quét..." else "${list.size} bài", Modifier.weight(1f))
                    Text("Chỉ MP3", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(4.dp))
                    Switch(checked = Library.onlyMp3, onCheckedChange = { Settings.setOnlyMp3(ctx, it); scan() })
                    TextButton(onClick = { scan() }) { Text("Quét lại") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { playList(ctx, list, 0) }, enabled = list.isNotEmpty()) { Text("Phát tất cả") }
                    OutlinedButton(
                        onClick = { player.shuffleModeEnabled = true; playList(ctx, list, Random.nextInt(list.size)) },
                        enabled = list.isNotEmpty()
                    ) { Text("Ngẫu nhiên") }
                }
                if (list.isEmpty() && !Library.loading) {
                    Text("Không tìm thấy bài hát nào. Thử tắt \"Chỉ MP3\" hoặc bấm Quét lại.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
                LazyColumn(Modifier.weight(1f)) {
                    itemsIndexed(list, key = { _, t -> t.id }) { i, t ->
                        TrackRow(t, np.id == t.id.toString(), onClick = { playList(ctx, list, i) }, onMore = { dialog = listOf(t) })
                    }
                }
            }
            1 -> {
                val f = folder
                if (f == null) {
                    val groups = Library.tracks.groupBy { it.folder }.toSortedMap()
                    LazyColumn(Modifier.weight(1f)) {
                        items(groups.keys.toList()) { g ->
                            Card(
                                onClick = { folder = g },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("📁 $g", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                    Text("${groups[g]?.size ?: 0} bài", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                } else {
                    val list = Library.tracks.filter { it.folder == f }
                    TextButton(onClick = { folder = null }) { Text("‹ Quay lại") }
                    Text("📁 $f", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                        Button(onClick = { playList(ctx, list, 0) }) { Text("Phát thư mục") }
                        OutlinedButton(onClick = { dialog = list }) { Text("Thêm vào playlist") }
                        OutlinedButton(onClick = { Settings.hide(ctx, f); Library.rescan(ctx); folder = null }) { Text("Ẩn") }
                    }
                    LazyColumn(Modifier.weight(1f)) {
                        itemsIndexed(list, key = { _, t -> t.id }) { i, t ->
                            TrackRow(t, np.id == t.id.toString(), onClick = { playList(ctx, list, i) }, onMore = { dialog = listOf(t) })
                        }
                    }
                }
            }
            2 -> {
                val pl = openPl
                if (pl == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = newPl, onValueChange = { newPl = it }, label = { Text("Tên playlist mới") }, singleLine = true, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            val n = newPl.trim()
                            if (n.isNotEmpty() && n !in playlists) { savePl(playlists + (n to emptyList())); newPl = "" }
                        }) { Text("Tạo") }
                    }
                    Spacer(Modifier.height(6.dp))
                    LazyColumn(Modifier.weight(1f)) {
                        item {
                            PlCard("Tất cả nhạc (tự động)", "${Library.tracks.size} bài", onOpen = { sub = 0 }, onPlay = { playList(ctx, Library.tracks, 0) })
                        }
                        items(playlists.keys.toList()) { n ->
                            PlCard(n, "${playlists[n]?.size ?: 0} bài", onOpen = { openPl = n }, onPlay = {
                                playList(ctx, (playlists[n] ?: emptyList()).mapNotNull { byId[it] }, 0)
                            })
                        }
                    }
                } else {
                    val ids = playlists[pl] ?: emptyList()
                    val list = ids.mapNotNull { byId[it] }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { openPl = null }) { Text("‹ Quay lại") }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { savePl(playlists - pl); openPl = null }) { Text("Xóa playlist") }
                    }
                    Text(pl, style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { playList(ctx, list, 0) }, enabled = list.isNotEmpty(), modifier = Modifier.padding(vertical = 6.dp)) { Text("Phát tất cả (${list.size})") }
                    if (list.isEmpty()) Text("Playlist trống. Ở tab Bài hát, bấm ⋮ cạnh bài hát để thêm vào đây.", style = MaterialTheme.typography.bodySmall)
                    LazyColumn(Modifier.weight(1f)) {
                        itemsIndexed(list, key = { _, t -> t.id }) { i, t ->
                            TrackRow(
                                t, np.id == t.id.toString(), moreLabel = "✕",
                                onClick = { playList(ctx, list, i) },
                                onMore = { savePl(playlists + (pl to ids.filter { it != t.id })) }
                            )
                        }
                    }
                }
            }
            3 -> {
                val list = Library.tracks.filter { it.id in Likes.ids }
                Text("Bài hát yêu thích (${list.size})", fontWeight = FontWeight.SemiBold)
                if (list.isNotEmpty()) AccentButton("▶ Phát tất cả", Modifier.padding(vertical = 8.dp)) { playList(ctx, list, 0) }
                else Text("Bạn chưa yêu thích bài hát nào. Bấm ♡ trên bài hát để lưu vào đây.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                LazyColumn(Modifier.weight(1f)) {
                    itemsIndexed(list, key = { _, t -> t.id }) { i, t ->
                        TrackRow(t, np.id == t.id.toString(), onClick = { playList(ctx, list, i) }, onMore = { dialog = listOf(t) })
                    }
                }
            }
            5 -> {
                val albums = Library.tracks.groupBy { it.albumId }.entries.sortedBy { it.value.first().album.lowercase() }
                LazyColumn(Modifier.weight(1f)) {
                    items(albums.chunked(2)) { row ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { e ->
                                val f = e.value.first()
                                Column(Modifier.weight(1f).clickable { onAlbum(e.key) }) {
                                    CoverBox(f.uri, Modifier.fillMaxWidth(), 320)
                                    Text(f.album.ifBlank { "Không rõ album" }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${f.artist} • ${e.value.size} bài", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            6 -> {
                val artistNames = Library.tracks.map { it.artist }.distinct().sorted()
                Text("${artistNames.size} nghệ sĩ", fontWeight = FontWeight.SemiBold)
                LazyColumn(Modifier.weight(1f)) {
                    items(artistNames) { n -> ArtistRow(n) { onArtist(n) } }
                }
            }
            else -> {
                val names = Follows.names.toList().sorted()
                Text("Đang theo dõi (${names.size})", fontWeight = FontWeight.SemiBold)
                if (names.isEmpty()) {
                    Text("Bạn chưa theo dõi nghệ sĩ nào. Mở hồ sơ nghệ sĩ ở tab Khám phá và bấm Theo dõi.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(names) { n -> ArtistRow(n) { onArtist(n) } }
                }
            }
        }
    }
    dialog?.let { ts ->
        AddToPlaylistDialog(
            count = ts.size, names = playlists.keys.toList(),
            onAdd = { n ->
                val cur = playlists[n] ?: emptyList()
                savePl(playlists + (n to (cur + ts.map { it.id }).distinct()))
                dialog = null
            },
            onDismiss = { dialog = null }
        )
    }
}
