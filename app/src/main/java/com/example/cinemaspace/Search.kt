package com.example.cinemaspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

@Composable
fun ArtistRow(name: String, onOpen: () -> Unit) {
    val count = Library.tracks.count { it.artist == name }
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(name, 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$count bài hát", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FollowButton(name, compact = true)
    }
}

@Composable
fun SearchOverlay(onClose: () -> Unit, onArtist: (String) -> Unit) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val tracks = Library.tracks
    val query = q.trim()
    val trackHits = if (query.isEmpty()) tracks.sortedByDescending { StatsStore.plays[it.id] ?: 0 }.take(8)
    else tracks.filter { it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true) }.take(50)
    val artistHits = if (query.isEmpty()) emptyList()
    else tracks.map { it.artist }.distinct().filter { it.contains(query, true) }.take(6)
    val plStore = remember { PlaylistStore.load(ctx) }
    val plHits = plStore.keys.filter { query.isNotEmpty() && it.contains(query, true) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = q, onValueChange = { q = it },
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    placeholder = { Text("Tìm bài hát, nghệ sĩ, playlist...") },
                    singleLine = true, shape = RoundedCornerShape(50)
                )
                TextButton(onClick = onClose) { Text("Hủy") }
            }
            LazyColumn(Modifier.weight(1f)) {
                if (artistHits.isNotEmpty()) {
                    item { SectionHead("Nghệ sĩ") }
                    items(artistHits) { n -> ArtistRow(n) { onClose(); onArtist(n) } }
                }
                if (plHits.isNotEmpty()) {
                    item { SectionHead("Playlist") }
                    items(plHits) { n ->
                        val list = (plStore[n] ?: emptyList()).mapNotNull { id -> tracks.firstOrNull { it.id == id } }
                        Row(Modifier.fillMaxWidth().clickable { playList(ctx, list, 0); onClose() }.padding(vertical = 10.dp)) {
                            Text("📃 $n", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Text("${list.size} bài", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item { SectionHead(if (query.isEmpty()) "Gợi ý cho bạn" else "Bài hát (${trackHits.size})") }
                itemsIndexed(trackHits, key = { _, t -> t.id }) { i, t ->
                    TrackRow(t, false, moreLabel = "▶", onClick = { playList(ctx, trackHits, i); onClose() }, onMore = { playList(ctx, trackHits, i); onClose() })
                }
                if (query.isNotEmpty() && trackHits.isEmpty() && artistHits.isEmpty()) {
                    item { Text("Không tìm thấy kết quả nào.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
fun ArtistScreen(name: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val np = rememberNowPlaying(PlayerHub.get(ctx))
    val list = Library.tracks.filter { it.artist == name }
    val plays = list.sumOf { StatsStore.plays[it.id] ?: 0 }
    var dialog by remember { mutableStateOf<List<Track>?>(null) }
    var playlists by remember { mutableStateOf(PlaylistStore.load(ctx)) }
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onBack) { Text("‹ Quay lại") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(name, 88.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2)
                Text("${list.size} bài hát • $plays lượt nghe", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                FollowButton(name)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AccentButton("▶ Phát tất cả") { playList(ctx, list, 0) }
            OutlinedButton(onClick = {
                if (list.isNotEmpty()) { PlayerHub.get(ctx).shuffleModeEnabled = true; playList(ctx, list, Random.nextInt(list.size)) }
            }) { Text("Ngẫu nhiên") }
        }
        LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
            itemsIndexed(list, key = { _, t -> t.id }) { i, t ->
                TrackRow(t, np.id == t.id.toString(), onClick = { playList(ctx, list, i) }, onMore = { dialog = listOf(t) })
            }
        }
    }
    dialog?.let { ts ->
        AddToPlaylistDialog(
            count = ts.size, names = playlists.keys.toList(),
            onAdd = { n ->
                val cur = playlists[n] ?: emptyList()
                val m = playlists + (n to (cur + ts.map { it.id }).distinct())
                playlists = m; PlaylistStore.save(ctx, m); dialog = null
            },
            onDismiss = { dialog = null }
        )
    }
}

@Composable
fun AlbumScreen(albumId: Long, onBack: () -> Unit, onArtist: (String) -> Unit) {
    val ctx = LocalContext.current
    val np = rememberNowPlaying(PlayerHub.get(ctx))
    val list = Library.tracks.filter { it.albumId == albumId }
        .sortedWith(compareBy<Track> { if (it.trackNo == 0) Int.MAX_VALUE else it.trackNo }.thenBy { it.title })
    val first = list.firstOrNull()
    val mins = list.sumOf { it.durationMs } / 60000
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onBack) { Text("‹ Quay lại") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverBox(first?.uri, Modifier.width(112.dp), 400, 14.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(first?.album?.ifBlank { "Không rõ album" } ?: "Album", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2)
                Text(
                    first?.artist ?: "", color = Accent, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { first?.let { onArtist(it.artist) } }
                )
                Text("${list.size} bài • $mins phút", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AccentButton("▶ Phát album") { playList(ctx, list, 0) }
            OutlinedButton(onClick = {
                if (list.isNotEmpty()) { PlayerHub.get(ctx).shuffleModeEnabled = true; playList(ctx, list, Random.nextInt(list.size)) }
            }) { Text("Ngẫu nhiên") }
        }
        LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
            itemsIndexed(list, key = { _, t -> t.id }) { i, t ->
                TrackRow(t, np.id == t.id.toString(), onClick = { playList(ctx, list, i) }, onMore = {})
            }
        }
    }
}
