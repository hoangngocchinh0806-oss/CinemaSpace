@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.cinemaspace

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** Sóng nhạc: bấm hoặc kéo để tua. Các chấm nhỏ là ghi chú theo thời điểm. */
@Composable
fun Waveform(seed: Long, progress: Float, markers: List<Float>, onSeek: (Float) -> Unit, modifier: Modifier = Modifier) {
    val bars = remember(seed) {
        val r = Random(seed)
        val raw = List(64) { 0.25f + 0.75f * r.nextFloat() }
        List(64) { i -> (raw[(i - 1).coerceAtLeast(0)] + raw[i] * 2 + raw[(i + 1).coerceAtMost(63)]) / 4f }
    }
    val rest = MaterialTheme.colorScheme.outline
    Canvas(
        modifier.height(72.dp)
            .pointerInput(Unit) { detectTapGestures { o -> onSeek((o.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { o -> onSeek((o.x / size.width).coerceIn(0f, 1f)) },
                    onDrag = { c, _ -> c.consume(); onSeek((c.position.x / size.width).coerceIn(0f, 1f)) }
                )
            }
    ) {
        val n = bars.size
        val gap = 2.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        val top = 10.dp.toPx()
        val usable = size.height - top
        for (i in 0 until n) {
            val h = usable * bars[i]
            drawRoundRect(
                if ((i + 0.5f) / n <= progress) Accent else rest,
                Offset(i * (w + gap), top + (usable - h)),
                androidx.compose.ui.geometry.Size(w, h), CornerRadius(w / 2f, w / 2f)
            )
        }
        markers.forEach { m ->
            drawCircle(Accent2, 4.dp.toPx(), Offset(m.coerceIn(0f, 1f) * size.width, 4.dp.toPx()))
        }
    }
}

@Composable
fun QueueSheet(player: Player, onDismiss: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    val n = remember(tick) { player.mediaItemCount }
    val cur = player.currentMediaItemIndex
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Sắp phát ($n)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { player.clearMediaItems(); tick++ }) { Text("Xóa hàng đợi") }
        }
        if (n == 0) Text("Chưa có bài hát nào trong hàng đợi.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.heightIn(max = 460.dp)) {
            items(n) { i ->
                val m = player.getMediaItemAt(i)
                Row(
                    Modifier.fillMaxWidth().clickable { player.seekTo(i, 0L); player.play() }.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${i + 1}", Modifier.width(28.dp), color = if (i == cur) Accent else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Column(Modifier.weight(1f)) {
                        Text(
                            m.mediaMetadata.title?.toString() ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (i == cur) Accent else MaterialTheme.colorScheme.onSurface, fontWeight = if (i == cur) FontWeight.Bold else FontWeight.Normal
                        )
                        Text(m.mediaMetadata.artist?.toString() ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { player.removeMediaItem(i); tick++ }) { Text("✕") }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun NowPlayingScreen(onClose: () -> Unit, onArtist: (String) -> Unit, onSound: () -> Unit) {
    val ctx = LocalContext.current
    val player = remember { PlayerHub.get(ctx) }
    val np = rememberNowPlaying(player)
    val st by SpatialHub.state.collectAsState()
    var showQueue by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var playlists by remember { mutableStateOf(PlaylistStore.load(ctx)) }
    var vol by remember { mutableFloatStateOf(PlayerHub.userVolume) }
    var notes by remember(np.id) { mutableStateOf(Notes.load(ctx, np.id)) }
    var noteText by remember { mutableStateOf("") }
    var dragY by remember { mutableFloatStateOf(0f) }
    val idLong = np.id.toLongOrNull()
    val liked = idLong != null && idLong in Likes.ids
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val bgc = MaterialTheme.colorScheme.background
    val bg by produceState<ImageBitmap?>(null, np.uri) {
        val u: Uri? = np.uri
        value = if (u == null) null else withContext(Dispatchers.IO) {
            try { ctx.contentResolver.loadThumbnail(u, android.util.Size(512, 512), null).asImageBitmap() } catch (e: Exception) { null }
        }
    }
    fun addNote(text: String) {
        if (np.id.isBlank() || text.isBlank()) return
        val l = (notes + Note(np.pos, text.trim())).sortedBy { it.t }
        notes = l
        Notes.save(ctx, np.id, l)
    }

    Box(Modifier.fillMaxSize().background(bgc)) {
        bg?.let { Image(it, null, Modifier.fillMaxSize().alpha(if (AppPrefs.dark) 0.30f else 0.20f), contentScale = ContentScale.Crop) }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, bgc))))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Text("⌄", fontSize = 30.sp) }
                Spacer(Modifier.weight(1f))
                Text("Đang phát", fontWeight = FontWeight.SemiBold, color = dim)
                Spacer(Modifier.weight(1f))
                if (np.artist.isNotBlank()) FollowButton(np.artist, compact = true) else Spacer(Modifier.width(48.dp))
            }
            if (!np.hasItem) {
                Text("Chưa phát bài nào. Hãy chọn một bài ở tab Khám phá hoặc Thư viện.", Modifier.padding(24.dp), color = dim)
                return@Column
            }
            Box(
                Modifier.fillMaxWidth().padding(vertical = 8.dp).pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { dragY = 0f },
                        onDragEnd = {
                            if (dragY < -120f) player.seekToNext() else if (dragY > 120f) player.seekToPrevious()
                            dragY = 0f
                        },
                        onVerticalDrag = { c, d -> c.consume(); dragY += d }
                    )
                },
                contentAlignment = Alignment.Center
            ) { CoverBox(np.uri, Modifier.fillMaxWidth(0.78f), 600, 20.dp) }
            Text("↑ Vuốt ảnh để chuyển bài ↓", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 11.sp, color = dim)
            Spacer(Modifier.height(10.dp))
            Text(np.title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                np.artist, color = Accent, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                modifier = Modifier.clickable { onArtist(np.artist) }
            )
            Spacer(Modifier.height(12.dp))
            Waveform(
                seed = idLong ?: 1L, progress = if (np.dur > 0) np.pos.toFloat() / np.dur else 0f,
                markers = if (np.dur > 0) notes.map { it.t.toFloat() / np.dur } else emptyList(),
                onSeek = { f -> player.seekTo((f * np.dur).toLong()) }, modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt(np.pos), fontSize = 12.sp, color = dim)
                Text(fmt(np.dur), fontSize = 12.sp, color = dim)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { player.shuffleModeEnabled = !np.shuffle }) {
                    Text("🔀", fontSize = 22.sp, modifier = Modifier.alpha(if (np.shuffle) 1f else 0.4f))
                }
                IconButton(onClick = { player.seekToPrevious() }) { Text("⏮", fontSize = 28.sp) }
                Box(
                    Modifier.size(68.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(Accent, Accent2)))
                        .clickable { if (np.playing) player.pause() else player.play() },
                    contentAlignment = Alignment.Center
                ) { Text(if (np.playing) "⏸" else "▶", fontSize = 28.sp, color = Color.White) }
                IconButton(onClick = { player.seekToNext() }) { Text("⏭", fontSize = 28.sp) }
                IconButton(onClick = {
                    player.repeatMode = when (np.repeat) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                }) {
                    Text(
                        if (np.repeat == Player.REPEAT_MODE_ONE) "🔂" else "🔁", fontSize = 22.sp,
                        modifier = Modifier.alpha(if (np.repeat != Player.REPEAT_MODE_OFF) 1f else 0.4f)
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { idLong?.let { Likes.toggle(ctx, it) } }) {
                    Text(if (liked) "♥ Đã thích" else "♡ Yêu thích", color = if (liked) Accent else MaterialTheme.colorScheme.onSurface)
                }
                TextButton(onClick = { showQueue = true }) { Text("☰ Hàng đợi (${np.queue})", color = MaterialTheme.colorScheme.onSurface) }
                TextButton(onClick = { showAdd = true }) { Text("➕ Playlist", color = MaterialTheme.colorScheme.onSurface) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("🔈", fontSize = 18.sp)
                Slider(value = vol, onValueChange = { vol = it; PlayerHub.userVolume = it; PlayerHub.applyVolume() }, modifier = Modifier.weight(1f))
                Text("🔊", fontSize = 18.sp)
            }

            Text("Âm thanh", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Gốc (tắt hiệu ứng)", !st.enabled) { SpatialHub.update { copy(enabled = false) } }
                MUSIC_QUICK.forEach { n ->
                    Pill(n, st.enabled && st.preset == n) {
                        PRESETS.firstOrNull { it.name == n }?.let { p -> SpatialHub.update { p.build(this).copy(enabled = true) } }
                    }
                }
                Pill("🎚️ Chỉnh chi tiết", false) { onSound() }
            }

            PlayerExtras(player, np)

            Text("Ghi chú trên sóng nhạc (${notes.size})", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⏱ ${fmt(np.pos)}", color = Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = noteText, onValueChange = { noteText = it }, modifier = Modifier.weight(1f),
                    placeholder = { Text("Ghi chú tại thời điểm này...") }, singleLine = true
                )
                IconButton(onClick = { addNote(noteText); noteText = "" }) { Text("➤", color = Accent, fontSize = 20.sp) }
            }
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("🔥", "👏", "🥹", "❤️", "🎶").forEach { e ->
                    Text(
                        e, fontSize = 22.sp,
                        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable { addNote(e) }.padding(10.dp)
                    )
                }
            }
            if (notes.isEmpty()) Text("Chưa có ghi chú nào ghim trên sóng nhạc.", color = dim, fontSize = 13.sp)
            notes.forEach { n ->
                Row(Modifier.fillMaxWidth().clickable { player.seekTo(n.t) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(fmt(n.t), color = Accent, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp))
                    Text(n.text, Modifier.weight(1f))
                    Text("✕", color = dim, modifier = Modifier.clickable {
                        val l = notes - n
                        notes = l
                        Notes.save(ctx, np.id, l)
                    }.padding(8.dp))
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
    if (showQueue) QueueSheet(player) { showQueue = false }
    if (showAdd) {
        AddToPlaylistDialog(
            count = 1, names = playlists.keys.toList(),
            onAdd = { n ->
                val id = np.id.toLongOrNull()
                if (id != null) {
                    val cur = playlists[n] ?: emptyList()
                    val m = playlists + (n to (cur + id).distinct())
                    playlists = m
                    PlaylistStore.save(ctx, m)
                }
                showAdd = false
            },
            onDismiss = { showAdd = false }
        )
    }
}
